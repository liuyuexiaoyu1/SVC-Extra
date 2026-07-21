package com.liuyue.svcextra.audio;
import com.liuyue.svcextra.config.SvcExtraConfig;
public class AudioPipeline {
    private static boolean nvidiaReady = false;
    private static float hpfPrevX = 0f, hpfPrevY = 0f;
    private static final float HPF_A = 1f / (1f + 48000f / (2f * (float) Math.PI * 80f));
    private static float gateGain = 1f;
    public static void init() {}
    public static void processShort(short[] audio, SvcExtraConfig cfg) {
        var c = cfg.client;
        if (c.highPassFilter) {
            for (int i = 0; i < audio.length; i++) {
                float x = audio[i];
                float y = HPF_A * (x - hpfPrevX) + (1f - HPF_A) * hpfPrevY;
                hpfPrevX = x;
                hpfPrevY = y;
                audio[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y)));
            }
        }
        var mode = c.noiseCancelMode;
        if (mode == SvcExtraConfig.NoiseCancelMode.OFF) return;
        if (mode == SvcExtraConfig.NoiseCancelMode.NVIDIA_AI) {
            if (!nvidiaReady) nvidiaReady = NvidiaDenoiser.init(48000);
            if (nvidiaReady) NvidiaDenoiser.process(audio, 48000);
            return;
        }
        float threshold = switch (mode) {
            case WEBRTC_LOW -> 0.008f;        
            case WEBRTC_MEDIUM -> 0.016f;     
            case WEBRTC_HIGH -> 0.032f;       
            case WEBRTC_AGGRESSIVE -> 0.064f; 
            default -> 0.008f;
        };
        double sum = 0;
        for (short s : audio) sum += (s / 32768.0) * (s / 32768.0);
        double rms = Math.sqrt(sum / audio.length);
        float target = rms > threshold ? 1f : 0.05f;
        gateGain += (target - gateGain) * 0.1f;
        if (gateGain < 0.999f) {
            for (int i = 0; i < audio.length; i++) {
                audio[i] = (short) (audio[i] * gateGain);
            }
        }
    }
    public static boolean isWebrtcAvailable() { return true; }
    public static boolean isNvidiaAvailable() {
        if (!nvidiaReady) nvidiaReady = NvidiaDenoiser.init(48000);
        return nvidiaReady;
    }
}
