// ============ AecScheduler.java ============
package com.liuyue.svcextra.audio;

import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AecScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AecScheduler.class);
    private static final int FRAME_MS = 10;
    private static final int FRAME_SIZE = 480;

    private int fixedDelayMs = 80;

    private final AecSyncBuffer refBuffer = new AecSyncBuffer(150);
    private final ConcurrentLinkedQueue<MicPacket> micQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private AudioProcessing apm;
    private AudioProcessingStreamConfig cfg;

    private volatile Consumer<short[]> outputCallback;

    private volatile boolean primed = false;
    private long startTimeMs = 0;

    public void setFixedDelayMs(int delayMs) {
        this.fixedDelayMs = Math.max(20, Math.min(500, delayMs));
        if (apm != null) {
            apm.setStreamDelayMs(this.fixedDelayMs);
        }
        LOGGER.info("AEC fixed delay set to {}ms", this.fixedDelayMs);
    }

    public void start() {
        if (running.getAndSet(true)) return;
        try {
            apm = new AudioProcessing();
            var config = new AudioProcessingConfig();
            config.noiseSuppression.enabled = false;
            config.gainControl.enabled = false;
            config.echoCanceller.enabled = true;
            config.echoCanceller.enforceHighPassFiltering = true;
            apm.applyConfig(config);
            apm.setStreamDelayMs(fixedDelayMs);
            cfg = new AudioProcessingStreamConfig(48000, 1);

            startTimeMs = System.currentTimeMillis();
            LOGGER.info("AEC scheduler started, fixedDelay={}ms", fixedDelayMs);
        } catch (Throwable e) {
            LOGGER.error("AEC scheduler init failed", e);
            running.set(false);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "SvcExtra-AEC");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, FRAME_MS, TimeUnit.MILLISECONDS);
    }

    public void setOutputCallback(Consumer<short[]> callback) {
        this.outputCallback = callback;
    }

    public void pushReference(short[] audio, int sampleRate) {
        if (!running.get()) return;
        if (sampleRate != 48000) {
            LOGGER.warn("AEC only supports 48kHz currently, got {}Hz", sampleRate);
            return;
        }

        int frameBytes = FRAME_SIZE * 2;
        long nowMs = System.currentTimeMillis();

        for (int off = 0; off + FRAME_SIZE <= audio.length; off += FRAME_SIZE) {
            short[] frame = new short[FRAME_SIZE];
            System.arraycopy(audio, off, frame, 0, FRAME_SIZE);
            long playTimeMs = nowMs + fixedDelayMs + (off / FRAME_SIZE) * FRAME_MS;
            refBuffer.push(frame, playTimeMs);
        }
    }

    public void submitMic(short[] audio, int sampleRate) {
        if (!running.get()) return;
        if (sampleRate != 48000) {
            LOGGER.warn("AEC only supports 48kHz currently, got {}Hz", sampleRate);
            return;
        }

        long captureTimeMs = System.currentTimeMillis();
        short[] copy = new short[audio.length];
        System.arraycopy(audio, 0, copy, 0, audio.length);
        micQueue.offer(new MicPacket(captureTimeMs, copy));

        while (micQueue.size() > 20) micQueue.poll();
    }

    private void tick() {
        if (apm == null) return;

        if (!primed) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            int neededMs = fixedDelayMs + 50;
            if (elapsed < neededMs || refBuffer.size() < fixedDelayMs / FRAME_MS) {
                return;
            }
            primed = true;
            LOGGER.info("AEC primed after {}ms, refBuffer={} frames", elapsed, refBuffer.size());
        }

        MicPacket mic = micQueue.poll();
        if (mic == null) return;

        int frameBytes = FRAME_SIZE * 2;
        short[] micAudio = mic.audio();

        for (int off = 0; off + FRAME_SIZE <= micAudio.length; off += FRAME_SIZE) {

            long targetRefMs = mic.timestampMs() + (off / FRAME_SIZE) * FRAME_MS;
            short[] ref = refBuffer.consume(targetRefMs, 50);

            if (ref != null) {
                byte[] refBytes = new byte[frameBytes];
                ByteBuffer.wrap(refBytes).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().put(ref, 0, FRAME_SIZE);
                try {
                    apm.processReverseStream(refBytes, cfg, cfg, new byte[frameBytes]);
                } catch (Throwable e) {
                    LOGGER.warn("AEC reverse failed", e);
                }
            } else {
                LOGGER.trace("No reference frame for ts={}", targetRefMs);
            }

            byte[] in = new byte[frameBytes];
            ByteBuffer.wrap(in).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().put(micAudio, off, FRAME_SIZE);
            byte[] out = new byte[frameBytes];
            try {
                apm.processStream(in, cfg, cfg, out);
                ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(micAudio, off, FRAME_SIZE);
            } catch (Throwable e) {
                LOGGER.warn("AEC process failed", e);
            }
        }

        Consumer<short[]> cb = outputCallback;
        if (cb != null) {
            cb.accept(micAudio);
        }
    }

    public void stop() {
        running.set(false);
        primed = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (apm != null) {
            try {
                apm.dispose();
            } catch (Throwable ignored) {
            }
            apm = null;
        }
        refBuffer.clear();
        micQueue.clear();
        LOGGER.info("AEC scheduler stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPrimed() {
        return primed;
    }

    public int getQueueDepth() {
        return micQueue.size();
    }

    public int getRefBufferSize() {
        return refBuffer.size();
    }

    private record MicPacket(long timestampMs, short[] audio) {
    }
}