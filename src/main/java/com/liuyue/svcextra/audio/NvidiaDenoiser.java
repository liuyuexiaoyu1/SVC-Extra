package com.liuyue.svcextra.audio;

import com.liuyue.svcextra.SvcExtra;
import cn.ussshenzhou.channel.audio.nativ.NvAFX_Status;
import cn.ussshenzhou.channel.audio.nativ.NvAudioEffects;
import com.sun.jna.platform.win32.Kernel32;

import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.foreign.ValueLayout.*;

public class NvidiaDenoiser {
    private static volatile boolean nativeLoaded = false;

    private final MemorySegment handle;
    private final boolean isRtxVoice;

    public NvidiaDenoiser(int sampleRate) {
        try {
            var cfg = SvcExtra.CONFIG.client;
            String rawDllPath = (cfg.nvidiaDllPath != null && !cfg.nvidiaDllPath.isEmpty())
                    ? cfg.nvidiaDllPath : findDll();
            if (rawDllPath == null) throw new RuntimeException("NVAudioEffects.dll not found");

            Path dllPath = Paths.get(rawDllPath).toAbsolutePath().normalize();
            if (!Files.exists(dllPath)) throw new RuntimeException("DLL not found: " + dllPath);

            if (!nativeLoaded) {
                synchronized (NvidiaDenoiser.class) {
                    if (!nativeLoaded) {
                        Kernel32.INSTANCE.LoadLibraryEx(dllPath.toString(), null, 0x8);
                        System.load(dllPath.toString());
                        nativeLoaded = true;
                    }
                }
            }

            isRtxVoice = dllPath.toString().toLowerCase().contains("rtx voice");
            String modelName = isRtxVoice ? "denoiser_48k.wpkg" : "denoiser_" + (sampleRate / 1000) + "k.trtpkg";
            Path modelDir;
            if (cfg.nvidiaModelDir != null && !cfg.nvidiaModelDir.isEmpty()) {
                modelDir = Paths.get(cfg.nvidiaModelDir);
            } else if (isRtxVoice) {
                modelDir = dllPath.getParent();
            } else {
                modelDir = dllPath.getParent().resolve("models");
            }
            Path modelPath = modelDir.resolve(modelName).toAbsolutePath().normalize();
            if (!Files.exists(modelPath)) throw new RuntimeException("Model not found: " + modelPath);

            try (var arena = Arena.ofConfined()) {
                MemorySegment effectStr = arena.allocateFrom("denoiser");
                var handlePtr = arena.allocate(ADDRESS);
                check(NvAudioEffects.NvAFX_CreateEffect(effectStr, handlePtr), "NvAFX_CreateEffect");
                handle = handlePtr.get(ADDRESS, 0);

                if (isRtxVoice) {
                    MemorySegment paramSampleRate = arena.allocateFrom("sample_rate");
                    check(NvAudioEffects.NvAFX_SetU32(handle, paramSampleRate, 48000), "NvAFX_SetU32");
                }
                MemorySegment paramModelPath = NvAudioEffects.NVAFX_PARAM_MODEL_PATH(isRtxVoice);
                MemorySegment valModelPath = arena.allocateFrom(modelPath.toString());
                check(NvAudioEffects.NvAFX_SetString(handle, paramModelPath, valModelPath), "NvAFX_SetString");
                float ratio = 1.0f;
                check(NvAudioEffects.NvAFX_SetFloat(handle, NvAudioEffects.NVAFX_PARAM_INTENSITY_RATIO(), ratio), "NvAFX_SetFloat");
                check(NvAudioEffects.NvAFX_Load(handle), "NvAFX_Load");
            }
        } catch (Throwable e) {
            throw new RuntimeException("NvidiaDenoiser init failed", e);
        }
    }

    public void process(short[] audio, int sampleRate) {
        if (audio == null || audio.length == 0) return;
        if (isRtxVoice) processRtxVoice(audio, sampleRate);
        else processAfxSdk(audio, sampleRate);
    }

    private void processRtxVoice(short[] audio, int sampleRate) {
        if (sampleRate != 48000) return;
        int frameSize = 480, segs = audio.length / frameSize;
        if (segs == 0) return;
        try (var arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(JAVA_FLOAT, frameSize);
            MemorySegment outSeg = arena.allocate(JAVA_FLOAT, frameSize);
            MemorySegment inPtrArr = arena.allocate(ADDRESS, 1);
            MemorySegment outPtrArr = arena.allocate(ADDRESS, 1);
            inPtrArr.setAtIndex(ADDRESS, 0, inSeg);
            outPtrArr.setAtIndex(ADDRESS, 0, outSeg);
            float[] buf = new float[frameSize];
            for (int i = 0; i < segs; i++) {
                int off = i * frameSize;
                for (int j = 0; j < frameSize; j++) buf[j] = audio[off + j] / 32768.0f;
                MemorySegment.copy(buf, 0, inSeg, JAVA_FLOAT, 0, frameSize);
                int status = NvAudioEffects.NvAFX_Run(handle, inPtrArr, outPtrArr, frameSize, 1);
                if (status != 0) break;
                for (int j = 0; j < frameSize; j++) {
                    float f = outSeg.getAtIndex(JAVA_FLOAT, j);
                    audio[off + j] = (short) Math.max(-32768, Math.min(32767, Math.round(f * 32767f)));
                }
            }
        }
    }

    private void processAfxSdk(short[] audio, int sampleRate) {
        int frameSize = sampleRate / 100, segs = audio.length / frameSize;
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
                    audio[off + j] = (short) Math.max(-32768, Math.min(32767, Math.round(floats[j] * 32767)));
                }
            }
        }
    }

    public void close() {
        try { NvAudioEffects.NvAFX_DestroyEffect(handle); } catch (Throwable ignored) {}
    }

    private static String findDll() {
        String[] paths = {
            "C:\\Program Files\\NVIDIA Corporation\\NVIDIA RTX Voice\\NVAudioEffects.dll",
            "C:\\Program Files\\NVIDIA Corporation\\NVIDIA Audio Effects SDK\\NVAudioEffects.dll",
            "C:\\ProgramData\\NVIDIA\\NGX\\models\\nvbcast\\NVAudioEffects.dll"
        };
        for (String p : paths) if (Files.exists(Paths.get(p))) return p;
        return null;
    }

    private static void check(int status, String msg) {
        if (status != NvAFX_Status.NVAFX_STATUS_SUCCESS.ordinal())
            throw new RuntimeException(msg + " failed: status=" + NvAFX_Status.values()[status]);
    }
}
