package com.liuyue.svcextra.client.audio;
import com.liuyue.svcextra.SvcExtra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
public class DuckingManager {
    private static long lastAudioTime = 0;
    private static final long HOLD_MS = 600;
    private static final float SMOOTH = 0.15f;
    private static float originalVol = 1f;
    private static boolean hasOriginal = false;
    public static void markActive() {
        lastAudioTime = System.currentTimeMillis();
    }
    public static void tick() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        float level = SvcExtra.CONFIG.client.duckingLevel;
        if (level <= 0f) {
            if (hasOriginal) {
                sm.updateCategoryVolume(SoundSource.MASTER, originalVol);
                hasOriginal = false;
            }
            return;
        }
        long elapsed = System.currentTimeMillis() - lastAudioTime;
        boolean active = elapsed < HOLD_MS;
        if (active) {
            if (!hasOriginal) {
                originalVol = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
                hasOriginal = true;
            }
            float target = Math.max(0, Math.min(1, originalVol * (1f - level)));
            float current = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
            if (Math.abs(current - target) > 0.001f) {
                sm.updateCategoryVolume(SoundSource.MASTER, current + (target - current) * SMOOTH);
            }
        } else if (hasOriginal) {
            float current = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
            if (Math.abs(current - originalVol) > 0.01f) {
                sm.updateCategoryVolume(SoundSource.MASTER, current + (originalVol - current) * SMOOTH);
            } else {
                sm.updateCategoryVolume(SoundSource.MASTER, originalVol);
                hasOriginal = false;
            }
        }
    }
}
