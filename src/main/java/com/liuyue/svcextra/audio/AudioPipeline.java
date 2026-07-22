package com.liuyue.svcextra.audio;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.config.SvcExtraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPipeline.class);

    private static float hpfPrevX = 0f;
    private static float hpfPrevY = 0f;
    private static final float HPF_A;
    static {
        float cutoffFreq = 80f;
        float sampleRate = 48000f;
        float rc = 1f / (2f * (float) Math.PI * cutoffFreq);
        HPF_A = 1f / (1f + sampleRate * rc);
    }

    private static float gateGain = 1f;
    private static float gateRelease = 0.985f;
    private static float gateAttack = 1.02f;
    private static int silentSamples = 0;
    private static final int SILENT_THRESHOLD = 15;
    private static float currentThreshold = 120f;

    private static boolean nvidiaReady = false;
    private static boolean webrtcReady = false;
    private static int actualSampleRate = 48000;
    private static boolean isInitialized = false;
    private static SvcExtraConfig.NoiseCancelMode currentMode = SvcExtraConfig.NoiseCancelMode.OFF;

    private static int mapNoiseLevel(SvcExtraConfig.NoiseCancelMode mode) {
        return switch (mode) {
            case WEBRTC_LOW -> 0;
            case WEBRTC_MEDIUM -> 1;
            case WEBRTC_HIGH -> 2;
            case WEBRTC_AGGRESSIVE -> 3;
            default -> -1;
        };
    }


    public static void init() {
        if (isInitialized) return;


        var config = SvcExtra.CONFIG.client;
        actualSampleRate = config.micSampleRate > 0 ? config.micSampleRate : 48000;


        try {
            webrtcReady = WebRtcNative.load();
            if (webrtcReady) {

                var mode = config.noiseCancelMode;
                if (mode != SvcExtraConfig.NoiseCancelMode.OFF &&
                        mode != SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
                    int level = mapNoiseLevel(mode);
                    if (level >= 0) {
                        WebRtcNative.init(level);
                        LOGGER.info("WebRTC 降噪已初始化，等级: {}", mode);
                    }
                }

                if (config.echoCancel) {
                    WebRtcNative.applyEchoConfig(true);
                    LOGGER.info("WebRTC 回声消除已启用");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("WebRTC 初始化失败: {}", e.getMessage());
            webrtcReady = false;
        }


        if (SvcExtra.CONFIG.client.noiseCancelMode == SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
            try {
                nvidiaReady = NvidiaDenoiser.init(actualSampleRate);
                if (nvidiaReady) {
                    LOGGER.info("NVIDIA AI 降噪已初始化");
                }
            } catch (Exception e) {
                LOGGER.warn("NVIDIA AI 降噪初始化失败: {}", e.getMessage());
                nvidiaReady = false;
            }
        }

        isInitialized = true;
        currentMode = SvcExtra.CONFIG.client.noiseCancelMode;
        LOGGER.info("音频管道初始化完成，采样率: {}Hz", actualSampleRate);
    }

    public static void processShort(short[] audio, SvcExtraConfig cfg) {
        if (audio == null || audio.length == 0) return;

        var config = cfg.client;
        int sampleRate = config.micSampleRate > 0 ? config.micSampleRate : 48000;

        float inputRMS = calculateRMS(audio);
        if (inputRMS > 0.001f) {
            LOGGER.trace("输入 RMS: {} dB", 20 * Math.log10(inputRMS));
        }

        if (config.echoCancel && webrtcReady) {
            try {
                WebRtcNative.applyEchoConfig(true);
                WebRtcNative.processWithAec(audio, sampleRate);
            } catch (Exception e) {
                LOGGER.warn("AEC 处理失败: {}", e.getMessage());
            }
        }
        var mode = config.noiseCancelMode;
        if (mode != currentMode) {
            currentMode = mode;
            reconfigureNoiseSuppression(mode);
        }

        if (mode == SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
            if (nvidiaReady) {
                try {
                    NvidiaDenoiser.process(audio, sampleRate);
                } catch (Exception e) {
                    LOGGER.warn("NVIDIA 降噪处理失败: {}", e.getMessage());
                    applyNoiseGate(audio);
                }
            } else {
                try {
                    nvidiaReady = NvidiaDenoiser.init(sampleRate);
                    if (nvidiaReady) {
                        NvidiaDenoiser.process(audio, sampleRate);
                    } else {
                        applyNoiseGate(audio);
                    }
                } catch (Exception e) {
                    applyNoiseGate(audio);
                }
            }
        } else if (mode.name().startsWith("WEBRTC")) {
            if (webrtcReady) {
                try {
                    WebRtcNative.process(audio, sampleRate);
                } catch (Exception e) {
                    LOGGER.warn("WebRTC 降噪处理失败: {}", e.getMessage());
                    applyNoiseGate(audio);
                }
            } else {
                applyNoiseGate(audio);
            }
        }

        if (config.highPassFilter) {
            applyHighPassFilter(audio);
        }

        if (config.autoGainControl) {
            applyAutoGainControl(audio, config.targetLevelDbfs, config.maxGain);
        }

        applyGainCompensation(audio);

        preventClipping(audio);

        float outputRMS = calculateRMS(audio);
        if (outputRMS > 0.001f && inputRMS > 0.001f) {
            float gainDb = 20 * (float) Math.log10(outputRMS / (inputRMS + 0.0001f));
            LOGGER.trace("输出 RMS: {} dB, 增益变化: {} dB",
                    20 * Math.log10(outputRMS), gainDb);
        }
    }

    private static void reconfigureNoiseSuppression(SvcExtraConfig.NoiseCancelMode mode) {
        if (!webrtcReady) return;

        int level = mapNoiseLevel(mode);
        if (level >= 0) {
            try {
                WebRtcNative.init(level);
                LOGGER.info("WebRTC 降噪等级切换为: {}", mode);
            } catch (Exception e) {
                LOGGER.warn("切换 WebRTC 降噪等级失败: {}", e.getMessage());
            }
        }
    }

    private static void applyHighPassFilter(short[] audio) {
        for (int i = 0; i < audio.length; i++) {
            float x = audio[i] / 32768f;
            float y = HPF_A * (x - hpfPrevX) + (1f - HPF_A) * hpfPrevY;
            hpfPrevX = x;
            hpfPrevY = y;
            audio[i] = (short) Math.max(-32767, Math.min(32767, Math.round(y * 32767f)));
        }
    }

    private static void applyNoiseGate(short[] audio) {
        float sum = 0;
        float peak = 0;
        for (short s : audio) {
            float abs = Math.abs(s / 32768f);
            sum += abs;
            if (abs > peak) peak = abs;
        }
        float avg = sum / audio.length * 32768f;
        peak *= 32768f;

        float adaptiveThreshold = Math.max(60f, Math.min(250f, peak * 0.12f));
        adaptiveThreshold = (adaptiveThreshold + currentThreshold) * 0.5f;
        currentThreshold = adaptiveThreshold;

        if (avg > adaptiveThreshold) {
            silentSamples = 0;
            gateGain = Math.min(1f, gateGain * gateAttack);
        } else {
            silentSamples++;
            if (silentSamples > SILENT_THRESHOLD) {
                gateGain *= gateRelease;
            }
        }

        float minGain = 0.05f;
        gateGain = Math.max(minGain, Math.min(1f, gateGain));

        if (gateGain < 0.99f) {
            for (int i = 0; i < audio.length; i++) {
                audio[i] = (short) Math.round(audio[i] * gateGain);
            }
        }
    }

    private static void applyAutoGainControl(short[] audio, float targetDbfs, float maxGainDb) {
        float rms = 0;
        for (short s : audio) {
            float sample = s / 32768f;
            rms += sample * sample;
        }
        rms = (float) Math.sqrt(rms / audio.length);

        if (rms < 0.0001f) return;

        float targetRms = (float) Math.pow(10.0, targetDbfs / 20.0);
        float gain = targetRms / rms;

        float maxGainLinear = (float) Math.pow(10.0, maxGainDb / 20.0);
        gain = Math.min(maxGainLinear, gain);
        gain = Math.max(0.1f, gain);

        if (Math.abs(gain - 1.0f) > 0.01f) {
            for (int i = 0; i < audio.length; i++) {
                float sample = audio[i] / 32768f;
                sample *= gain;
                audio[i] = (short) Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
            }
        }
    }

    private static void applyGainCompensation(short[] audio) {
        float rms = 0;
        for (short s : audio) {
            float sample = s / 32768f;
            rms += sample * sample;
        }
        rms = (float) Math.sqrt(rms / audio.length);

        if (rms < 0.0316f && rms > 0.0001f) {
            float targetRms = 0.045f;
            float gain = targetRms / (rms + 0.0001f);
            gain = Math.min(4.0f, Math.max(0.5f, gain));

            for (int i = 0; i < audio.length; i++) {
                float sample = audio[i] / 32768f;
                sample *= gain;
                audio[i] = (short) Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
            }
        }
    }

    private static void preventClipping(short[] audio) {
        float maxSample = 0;
        for (short s : audio) {
            float abs = Math.abs(s / 32768f);
            if (abs > maxSample) maxSample = abs;
        }

        if (maxSample > 0.95f) {
            float gain = 0.9f / maxSample;
            for (int i = 0; i < audio.length; i++) {
                audio[i] = (short) Math.round(audio[i] * gain);
            }
        }
    }

    private static float calculateRMS(short[] audio) {
        float sum = 0;
        for (short s : audio) {
            float sample = s / 32768f;
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / audio.length);
    }

    public static boolean isWebrtcAvailable() {
        return webrtcReady;
    }

    public static boolean isNvidiaAvailable() {
        if (!nvidiaReady) {
            try {
                nvidiaReady = NvidiaDenoiser.init(actualSampleRate);
            } catch (Exception e) {
                LOGGER.warn("NVIDIA 初始化失败: {}", e.getMessage());
            }
        }
        return nvidiaReady;
    }

    public static void setSampleRate(int rate) {
        if (rate > 0) {
            actualSampleRate = rate;
            LOGGER.info("采样率设置为: {}Hz", rate);
        }
    }

    public static int getSampleRate() {
        return actualSampleRate;
    }

    public static void reset() {
        hpfPrevX = 0f;
        hpfPrevY = 0f;
        gateGain = 1f;
        silentSamples = 0;
        currentThreshold = 120f;
        LOGGER.debug("音频管道状态已重置");
    }

    public static void shutdown() {
        try {
            WebRtcNative.close();
        } catch (Exception e) {
            LOGGER.warn("关闭 WebRTC 时出错: {}", e.getMessage());
        }
        isInitialized = false;
        LOGGER.info("音频管道已关闭");
    }
}