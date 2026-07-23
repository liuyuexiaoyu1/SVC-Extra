package com.liuyue.svcextra.audio;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.config.SvcExtraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioPipeline {
    private static AecScheduler aecScheduler;
    private static boolean aecStarted = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPipeline.class);

    private static WebRtcNative webrtc = null;
    private static NvidiaDenoiser nvidia = null;
    private static boolean isInitialized = false;
    private static int sampleRate = 48000;

    public static synchronized void init() {
        if (isInitialized) return;
        sampleRate = SvcExtra.CONFIG.client.micSampleRate > 0 ? SvcExtra.CONFIG.client.micSampleRate : 48000;
        var config = SvcExtra.CONFIG.client;

        if (config.echoCancel) {
            startAecInternal();
        }

        if (!config.echoCancel || !isWebRtcNsMode(config.noiseCancelMode)) {
            try {
                int level = mapNoiseLevel(config.noiseCancelMode);
                if (level >= 0) {
                    webrtc = new WebRtcNative(level, false);
                    LOGGER.info("WebRTC NS initialized, level={}", level);
                }
            } catch (Exception e) {
                LOGGER.warn("WebRTC NS init failed: {}", e.getMessage());
            }
        }

        if (config.noiseCancelMode == SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
            try {
                nvidia = new NvidiaDenoiser(sampleRate);
                LOGGER.info("NVIDIA AI denoiser initialized");
            } catch (Exception e) {
                LOGGER.warn("NVIDIA AI init failed: {}", e.getMessage());
            }
        }

        isInitialized = true;
        LOGGER.info("AudioPipeline initialized");
    }

    private static synchronized void startAecInternal() {
        if (aecScheduler == null) {
            aecScheduler = new AecScheduler();
        }
        if (!aecStarted) {
            aecScheduler.start();
            aecStarted = true;
            LOGGER.info("AEC started");
        }
    }

    public static synchronized void startAec() {
        startAecInternal();
    }

    public static synchronized void stopAec() {
        if (aecScheduler != null && aecStarted) {
            aecScheduler.stop();
            aecStarted = false;
            LOGGER.info("AEC stopped");
        }
    }

    public static synchronized void shutdown() {
        stopAec();
        aecScheduler = null;
        webrtc = null;
        nvidia = null;
        isInitialized = false;
        LOGGER.info("AudioPipeline shutdown");
    }

    private static boolean isWebRtcNsMode(SvcExtraConfig.NoiseCancelMode mode) {
        return switch (mode) {
            case WEBRTC_LOW, WEBRTC_MEDIUM, WEBRTC_HIGH, WEBRTC_AGGRESSIVE -> true;
            default -> false;
        };
    }

    private static int mapNoiseLevel(SvcExtraConfig.NoiseCancelMode mode) {
        return switch (mode) {
            case WEBRTC_LOW -> 0;
            case WEBRTC_MEDIUM -> 1;
            case WEBRTC_HIGH -> 2;
            case WEBRTC_AGGRESSIVE -> 3;
            default -> -1;
        };
    }

    public static boolean isWebrtcAvailable() {
        return true;
    }

    public static boolean isNvidiaAvailable() {
        try {
            if (nvidia == null) {
                new NvidiaDenoiser(48000);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void pushRemoteReference(short[] audio, int sr) {
        if (audio == null || audio.length == 0) return;
        if (!SvcExtra.CONFIG.client.echoCancel) return;
        if (aecScheduler == null || !aecStarted) return;
        aecScheduler.pushReference(audio, sr > 0 ? sr : 48000);
    }

    public static void processShort(short[] audio, SvcExtraConfig cfg) {
        if (audio == null || audio.length == 0) return;
        var config = cfg.client;
        init();

        if (config.echoCancel) {
            if (!aecStarted) {
                startAecInternal();
            }
            if (aecScheduler != null && aecScheduler.isRunning()) {
                short[] ref = AecReferenceMixer.poll();
                if (ref != null) {
                    aecScheduler.pushReference(ref, 48000);
                }
                aecScheduler.submitMic(audio, sampleRate);
            }
            return;
        }

        if (config.noiseCancelMode == SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
            if (nvidia != null) nvidia.process(audio, sampleRate);
        } else if (isWebRtcNsMode(config.noiseCancelMode)) {
            if (webrtc != null) webrtc.process(audio, sampleRate);
        }
    }
}