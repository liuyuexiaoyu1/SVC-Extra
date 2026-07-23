package com.liuyue.svcextra.client.audio;
import com.liuyue.svcextra.SvcExtra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import java.util.EnumMap;
import java.util.Map;

public class DuckingManager {
    private static long lastAudioTime = 0;
    private static final long HOLD_MS = 600;
    private static final float SMOOTH = 0.15f;
    private static final Map<SoundSource, Float> originals = new EnumMap<>(SoundSource.class);
    private static boolean hasOriginals = false;

    public static void markActive() {
        lastAudioTime = System.currentTimeMillis();
    }

    public static void tick() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        float level = SvcExtra.CONFIG.client.duckingLevel;
        if (level <= 0f) {
            restoreAll(sm);
            return;
        }
        long elapsed = System.currentTimeMillis() - lastAudioTime;
        boolean active = elapsed < HOLD_MS;
        if (active) {
            if (!hasOriginals) {
                originals.clear();
                for (var src : SoundSource.values()) {
                    if (src == SoundSource.MASTER || src == SoundSource.VOICE) continue;
                    originals.put(src, Minecraft.getInstance().options.getSoundSourceVolume(src));
                }
                hasOriginals = true;
            }
            for (var entry : originals.entrySet()) {
                float orig = entry.getValue();
                float target = Math.max(0, orig * (1f - level));
                float current = Minecraft.getInstance().options.getSoundSourceVolume(entry.getKey());
                if (Math.abs(current - target) > 0.001f) {
                    sm.updateCategoryVolume(entry.getKey(), current + (target - current) * SMOOTH);
                }
            }
        } else if (hasOriginals) {
            restoreAll(sm);
        }
    }

    private static void restoreAll(SoundManager sm) {
        if (!hasOriginals) return;
        boolean allRestored = true;
        for (var entry : originals.entrySet()) {
            float current = Minecraft.getInstance().options.getSoundSourceVolume(entry.getKey());
            if (Math.abs(current - entry.getValue()) > 0.01f) {
                sm.updateCategoryVolume(entry.getKey(), current + (entry.getValue() - current) * SMOOTH);
                allRestored = false;
            } else {
                sm.updateCategoryVolume(entry.getKey(), entry.getValue());
            }
        }
        if (allRestored) {
            originals.clear();
            hasOriginals = false;
        }
    }
}
