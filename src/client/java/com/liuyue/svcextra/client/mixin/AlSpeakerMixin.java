package com.liuyue.svcextra.client.mixin;
import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.client.audio.DuckingManager;
import com.liuyue.svcextra.client.audio.PlayerVelocityTracker;
import com.liuyue.svcextra.client.audio.RayTracedReverb;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static org.lwjgl.openal.AL10.*;
@Mixin(value = ALSpeakerBase.class, remap = false)
public class AlSpeakerMixin {
    @Final
    @Shadow
    @Mutable
    protected int source;
    @Shadow
    protected UUID audioChannelId;
    private static final ConcurrentHashMap<UUID, float[]> FILTER_STATE = new ConcurrentHashMap<>();
    @Inject(method = "openSync", at = @At("TAIL"), remap = false)
    private void onOpenSync(CallbackInfo ci) {
        applyReverbFx();
    }
    @Inject(method = "writeSync", at = @At("HEAD"), remap = false)
    private void onWriteSync(short[] audio, float volume, Vec3 pos,
                             String whisper, float distance, CallbackInfo ci) {
        DuckingManager.markActive();
        applyReverbFx();
        if (SvcExtra.CONFIG.client.rayTraceAudio) {
            AL11.alDopplerFactor(1.2f);
            AL11.alSpeedOfSound(340.0f);
            applyUnderwaterFilter(audio);
            if (pos != null) {
                applyDopplerVelocity(pos);
                applyOcclusionFilter(audio, pos, distance);
            }
        }
    }
    @Inject(method = "closeSync", at = @At("HEAD"), remap = false)
    private void onCloseSync(CallbackInfo ci) {
        FILTER_STATE.remove(audioChannelId);
    }
    private void applyReverbFx() {
        if (SvcExtra.CONFIG.client.rayTraceAudio) {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    RayTracedReverb.getAuxSlot(), 0, EXTEfx.AL_FILTER_NULL);
        } else {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
        }
    }
    private void applyUnderwaterFilter(short[] audio) {
        if (!RayTracedReverb.isUnderwater()) {
            FILTER_STATE.remove(audioChannelId);
            return;
        }
        if (audio == null || audio.length == 0) return;
        float alpha = RayTracedReverb.isInLava() ? 0.095f : 0.045f;
        float a1 = 1f - alpha;
        float[] s = FILTER_STATE.computeIfAbsent(audioChannelId, k -> new float[1]);
        for (int i = 0; i < audio.length; i++) {
            float x = audio[i] / 32768f;
            float y = alpha * x + a1 * s[0];
            if (y > 1f) {
                y = 1f - (y - 1f) / (y + 1f);  
            } else if (y < -1f) {
                y = -1f - (y + 1f) / (y - 1f); 
            }
            s[0] = y;
            audio[i] = (short) (y * 23170f); 
        }
    }
    private void applyDopplerVelocity(Vec3 pos) {
        Vec3 vel = PlayerVelocityTracker.getNearestPlayerVelocity(pos);
        AL11.alSource3f(source, AL_VELOCITY, (float) vel.x, (float) vel.y, (float) vel.z);
    }
    private void applyOcclusionFilter(short[] audio, Vec3 srcPos, float distance) {
        if (distance < 2f) return; 
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 earPos = mc.player.getEyePosition();
        Vec3 dir = earPos.subtract(srcPos).normalize();
        Vec3 origin = srcPos.add(dir.scale(0.5));
        var ctx = new ClipContext(origin, earPos, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty());
        BlockHitResult hit = mc.level.clip(ctx);
        if (hit.getType() == HitResult.Type.MISS) return;
        float thickness = (float) hit.getLocation().distanceTo(origin);
        float penetration = Math.min(1f, thickness / 10f);
        if (penetration < 0.05f) return;
        float rawAlpha = 0.5f - penetration * 0.15f;
        if (rawAlpha < 0.1f) rawAlpha = 0.1f;
        if (rawAlpha >= 0.95f) return;
        float[] s = FILTER_STATE.computeIfAbsent(
                UUID.nameUUIDFromBytes(("occl_" + audioChannelId).getBytes()),
                k -> new float[2]); 
        float alpha = s[1] + (rawAlpha - s[1]) * 0.2f; 
        s[1] = alpha;
        float a1 = 1f - alpha;
        for (int i = 0; i < audio.length; i++) {
            float x = audio[i] / 32768f;
            float y = alpha * x + a1 * s[0];
            if (y > 1f) y = 1f; else if (y < -1f) y = -1f;
            s[0] = y;
            audio[i] = (short) (y * 32767f);
        }
    }
}
