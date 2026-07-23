package com.liuyue.svcextra.client.audio;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.client.mixin.SoundEngineAccessor;
import com.liuyue.svcextra.client.mixin.SoundManagerAccessor;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;

import java.util.EnumMap;
import java.util.Map;

public class DuckingManager {
    private static long lastAudioTime = 0;
    private static final long HOLD_MS = 600;
    private static final float SMOOTH = 0.15f;

    private static final float BEZIER_P1_X = 0.2f;
    private static final float BEZIER_P1_Y = 0.6f;
    private static final float BEZIER_P2_X = 0.6f;
    private static final float BEZIER_P2_Y = 0.1f;

    private static final Map<SoundSource, Float> originals = new EnumMap<>(SoundSource.class);
    private static boolean hasOriginals = false;
    private static float currentDuckLevel = 0f;

    public static void markActive() {
        lastAudioTime = System.currentTimeMillis();
    }

    public static void tick() {
        float level = SvcExtra.CONFIG.client.duckingLevel;
        if (level <= 0f) {
            restoreAll();
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        SoundEngine soundEngine =((SoundManagerAccessor) mc.getSoundManager()).getSoundEngine();
        if (soundEngine == null) return;

        SoundEngineAccessor accessor = (SoundEngineAccessor) soundEngine;
        Object2FloatMap<SoundSource> gainMap = accessor.gainBySource();
        if (gainMap == null) return;

        long elapsed = System.currentTimeMillis() - lastAudioTime;
        boolean active = elapsed < HOLD_MS;

        if (active) {
            float progress = Math.min(1f, (float) elapsed / HOLD_MS);
            float bezierValue = cubicBezier(progress, BEZIER_P1_X, BEZIER_P1_Y, BEZIER_P2_X, BEZIER_P2_Y);
            float targetLevel = level * bezierValue;

            currentDuckLevel = lerp(currentDuckLevel, targetLevel, SMOOTH);
            if (Math.abs(currentDuckLevel - targetLevel) < 0.001f) {
                currentDuckLevel = targetLevel;
            }

            if (!hasOriginals) {
                originals.clear();
                for (var src : SoundSource.values()) {
                    if (src == SoundSource.MASTER || src == SoundSource.VOICE) continue;
                    originals.put(src, gainMap.getOrDefault(src, 1.0f));
                }
                hasOriginals = true;
            }

            for (var entry : originals.entrySet()) {
                float orig = entry.getValue();
                float target = Math.max(0, orig * (1f - currentDuckLevel));
                float current = gainMap.getOrDefault(entry.getKey(), 1.0f);

                if (Math.abs(current - target) > 0.001f) {
                    float newValue = current + (target - current) * SMOOTH;
                    gainMap.put(entry.getKey(), newValue);
                }
            }
        } else if (hasOriginals) {
            float recoveryProgress = Math.min(1f, (float) (elapsed - HOLD_MS) / 500f);
            float bezierRecovery = cubicBezier(recoveryProgress,
                    1f - BEZIER_P2_X, 1f - BEZIER_P2_Y,
                    1f - BEZIER_P1_X, 1f - BEZIER_P1_Y
            );

            float recoveryLevel = level * (1f - bezierRecovery);
            currentDuckLevel = lerp(currentDuckLevel, recoveryLevel, SMOOTH * 0.8f);

            if (currentDuckLevel < 0.001f) {
                restoreAll(gainMap);
            } else {
                for (var entry : originals.entrySet()) {
                    float orig = entry.getValue();
                    float target = Math.max(0, orig * (1f - currentDuckLevel));
                    float current = gainMap.getOrDefault(entry.getKey(), 1.0f);

                    if (Math.abs(current - target) > 0.001f) {
                        float newValue = current + (target - current) * SMOOTH;
                        gainMap.put(entry.getKey(), newValue);
                    }
                }
            }
        }
    }

    private static float cubicBezier(float t, float p1x, float p1y, float p2x, float p2y) {
        float x = t;
        float y;
        int iterations = 8;

        for (int i = 0; i < iterations; i++) {
            float x1 = p1x * 3f * (1f - x) * (1f - x);
            float x2 = p2x * 3f * (1f - x) * x;
            float x3 = x * x * x;
            float currentX = x1 + x2 + x3;
            float dx1 = p1x * 3f * (1f - x) * (1f - x);
            float dx2 = p2x * 6f * (1f - x) * x;
            float dx3 = 3f * x * x;
            float dx = dx1 + dx2 - dx3;
            x = x - (currentX - t) / dx;
            x = Math.max(0, Math.min(1, x));
        }
        float y1 = p1y * 3f * (1f - x) * (1f - x);
        float y2 = p2y * 3f * (1f - x) * x;
        float y3 = x * x * x;
        y = y1 + y2 + y3;

        return Math.max(0, Math.min(1, y));
    }

    private static void restoreAll(Object2FloatMap<SoundSource> gainMap) {
        if (!hasOriginals) return;

        boolean allRestored = true;
        for (var entry : originals.entrySet()) {
            float orig = entry.getValue();
            float current = gainMap.getOrDefault(entry.getKey(), 1.0f);

            if (Math.abs(current - orig) > 0.01f) {
                float newValue = current + (orig - current) * SMOOTH;
                gainMap.put(entry.getKey(), newValue);
                allRestored = false;
            } else {
                gainMap.put(entry.getKey(), orig);
            }
        }

        if (allRestored) {
            originals.clear();
            hasOriginals = false;
            currentDuckLevel = 0f;
        }
    }

    private static void restoreAll() {
        Minecraft mc = Minecraft.getInstance();

        SoundEngine soundEngine =((SoundManagerAccessor) mc.getSoundManager()).getSoundEngine();
        if (soundEngine == null) return;

        SoundEngineAccessor accessor = (SoundEngineAccessor) soundEngine;
        Object2FloatMap<SoundSource> gainMap = accessor.gainBySource();
        if (gainMap == null) return;

        restoreAll(gainMap);
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * Math.min(1f, Math.max(0f, t));
    }
}