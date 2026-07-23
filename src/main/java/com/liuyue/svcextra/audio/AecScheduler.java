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

public class AecScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AecScheduler.class);
    private static final int FRAME_MS = 10;
    private static final int FRAME_SIZE = 480;

    private final ConcurrentLinkedQueue<short[]> refQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<short[]> micQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private AudioProcessing apm;
    private AudioProcessingStreamConfig cfg;

    private int fixedDelayMs = 120;

    public void setFixedDelayMs(int delayMs) {
        this.fixedDelayMs = Math.max(50, Math.min(500, delayMs));
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
            LOGGER.info("AEC scheduler started, delay={}ms", fixedDelayMs);
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
        scheduler.scheduleAtFixedRate(this::tick, fixedDelayMs, FRAME_MS, TimeUnit.MILLISECONDS);
    }

    public void pushReference(short[] audio, int sampleRate) {
        if (!running.get()) return;
        short[] copy = new short[audio.length];
        System.arraycopy(audio, 0, copy, 0, audio.length);
        refQueue.offer(copy);
        while (refQueue.size() > 200) refQueue.poll();
    }

    public void submitMic(short[] audio, int sampleRate) {
        if (!running.get()) return;
        short[] copy = new short[audio.length];
        System.arraycopy(audio, 0, copy, 0, audio.length);
        micQueue.offer(copy);
        while (micQueue.size() > 20) micQueue.poll();
    }

    private void tick() {
        if (apm == null) return;

        short[] ref = refQueue.poll();
        if (ref != null) {
            processFrame(ref, true);
        }

        short[] mic = micQueue.poll();
        if (mic != null) {
            processFrame(mic, false);
        }
    }

    private void processFrame(short[] audio, boolean isRef) {
        int fb = FRAME_SIZE * 2;
        for (int off = 0; off + FRAME_SIZE <= audio.length; off += FRAME_SIZE) {
            byte[] bytes = new byte[fb];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().put(audio, off, FRAME_SIZE);
            try {
                if (isRef) {
                    apm.processReverseStream(bytes, cfg, cfg, new byte[fb]);
                } else {
                    byte[] out = new byte[fb];
                    apm.processStream(bytes, cfg, cfg, out);
                    ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().get(audio, off, FRAME_SIZE);
                }
            } catch (Throwable e) {
                LOGGER.warn("AEC {} failed", isRef ? "reverse" : "process", e);
            }
        }
    }

    public void stop() {
        running.set(false);
        if (scheduler != null) scheduler.shutdown();
        if (apm != null) try { apm.dispose(); } catch (Throwable ignored) {}
        refQueue.clear();
        micQueue.clear();
    }

    public boolean isRunning() {
        return running.get();
    }
}