package com.liuyue.svcextra.audio;

import com.liuyue.svcextra.SvcExtra;
import cn.ussshenzhou.channel.audio.nativ.NvAFX_Status;
import cn.ussshenzhou.channel.audio.nativ.NvAudioEffects;
import com.sun.jna.platform.win32.Kernel32;
import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.lang.foreign.ValueLayout.*;
public class NvidiaDenoiser {
    private static MemorySegment handle;

    public static boolean init(int sampleRate) {
        if (handle != null) return true;
        try {
            var cfg = SvcExtra.CONFIG.client;
            String dllPath = (cfg.nvidiaDllPath != null && !cfg.nvidiaDllPath.isEmpty())
                    ? cfg.nvidiaDllPath : findDll();
            if (dllPath == null || !Files.exists(Paths.get(dllPath))) {
                SvcExtra.LOGGER.warn("NVAudioEffects.dll not found");
                return false;
            }
            loadLibraryWithDeps(dllPath);
            String modelName = "denoiser_" + (sampleRate / 1000) + "k.trtpkg";
            String modelDir = (cfg.nvidiaModelDir != null && !cfg.nvidiaModelDir.isEmpty())
                    ? cfg.nvidiaModelDir
                    : dllPath.replace("NVAudioEffects.dll", "models");
            String modelPath = modelDir + "\\" + modelName;
            if (!Files.exists(Paths.get(modelPath))) {
                SvcExtra.LOGGER.error("Model not found: {}", modelPath);
                return false;
            }
            try (var arena = Arena.ofConfined()) {
                MemorySegment effectStr = arena.allocateFrom("denoiser");
                var handlePtr = arena.allocate(ADDRESS);
                check(NvAudioEffects.NvAFX_CreateEffect(effectStr, handlePtr), "NvAFX_CreateEffect");
                handle = handlePtr.get(ADDRESS, 0);
                MemorySegment paramModelPath = NvAudioEffects.NVAFX_PARAM_MODEL_PATH();
                MemorySegment valModelPath = arena.allocateFrom(modelPath);
                check(NvAudioEffects.NvAFX_SetString(handle, paramModelPath, valModelPath), "NvAFX_SetString");
                float ratio = Math.min(1, Math.max(0, cfg.aiNoiseCancelRatio));
                MemorySegment paramIntensity = NvAudioEffects.NVAFX_PARAM_INTENSITY_RATIO();
                check(NvAudioEffects.NvAFX_SetFloat(handle, paramIntensity, ratio), "NvAFX_SetFloat");
                check(NvAudioEffects.NvAFX_Load(handle), "NvAFX_Load");
            }
            SvcExtra.LOGGER.info("NVIDIA AFX ready (model: {}, ratio: {})", modelName, cfg.aiNoiseCancelRatio);
            return true;
        } catch (Throwable e) {
            SvcExtra.LOGGER.error("NVIDIA AFX init failed", e);
            return false;
        }
    }
    public static void process(short[] audio, int sampleRate) {
        if (handle == null) return;
        int frameSize = sampleRate / 100;
        int segs = audio.length / frameSize;
        try (var arena = Arena.ofConfined()) {
            var inPtrArr = arena.allocate(ADDRESS, 1);
            var outPtrArr = arena.allocate(ADDRESS, 1);
            for (int i = 0; i < segs; i++) {
                int off = i * frameSize;
                float[] in = new float[frameSize];
                for (int j = 0; j < frameSize; j++) in[j] = audio[off + j] / 32768f;
                float[] out = new float[frameSize];
                var inSeg = arena.allocateFrom(JAVA_FLOAT, in);
                var outSeg = arena.allocateFrom(JAVA_FLOAT, out);
                inPtrArr.setAtIndex(ADDRESS, 0, inSeg);
                outPtrArr.setAtIndex(ADDRESS, 0, outSeg);
                check(NvAudioEffects.NvAFX_Run(handle, inPtrArr, outPtrArr, frameSize, 1), "NvAFX_Run");
                var floats = outSeg.toArray(JAVA_FLOAT);
                for (int j = 0; j < frameSize; j++) {
                    int s = Math.round(floats[j] * 32767);
                    audio[off + j] = (short) Math.max(-32768, Math.min(32767, s));
                }
            }
        } catch (Throwable e) {
            SvcExtra.LOGGER.error("NVIDIA AFX process failed", e);
        }
    }
    public static void close() {
        if (handle != null) {
            try { NvAudioEffects.NvAFX_DestroyEffect(handle); } catch (Throwable ignored) {}
            handle = null;
        }
    }
    public static boolean isLoaded() { return handle != null; }
    private static void loadLibraryWithDeps(String dllPath) {
        Kernel32.INSTANCE.LoadLibraryEx(dllPath, null, 0x8);
        System.load(dllPath);
    }
    private static String findDll() {
        String[] paths = {
            "C:\\Program Files\\NVIDIA Corporation\\NVIDIA Audio Effects SDK\\NVAudioEffects.dll",
            "C:\\ProgramData\\NVIDIA\\NGX\\models\\nvbcast\\NVAudioEffects.dll"
        };
        for (String p : paths) {
            if (Files.exists(Paths.get(p))) return p;
        }
        return null;
    }
    private static void check(int status, String msg) {
        if (status != NvAFX_Status.NVAFX_STATUS_SUCCESS.ordinal()) {
            throw new RuntimeException(msg + " failed: status=" + NvAFX_Status.values()[status]);
        }
    }
}
