package com.liuyue.svcextra.client.mixin;
import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Mixin(value = ALSpeakerBase.class, remap = false)
public class AbsoluteLoudnessMixin {
    private static final float[][] XM_GAIN = {
        { 1.0f, 0.12f, 0.20f },
        { 0.12f, 1.0f, 0.35f },
        { 0.20f, 0.35f, 1.0f  },
    };
    private static final ConcurrentHashMap<UUID, float[]> DIR_SMOOTH = new ConcurrentHashMap<>();
    @Shadow
    protected UUID audioChannelId;
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetVolume(float volume, Vec3 pos, float distance, CallbackInfoReturnable<Float> cir) {
        float original = cir.getReturnValueF();
        float extraGain = 1.0f;
        if (SvcExtra.CONFIG.client.rayTraceAudio) {
            float strength = SvcExtra.CONFIG.client.absoluteLoudness;
            if (strength > 0.001f) {
                float k = strength * strength * 0.05f;
                float atten = 1.0f / (1.0f + k * distance * distance);
                extraGain *= Math.min(1.0f, atten * 4.0f);
            }
            var mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && pos != null) {
                FluidState srcFluid = mc.level.getBlockState(BlockPos.containing(pos)).getFluidState();
                FluidState dstFluid = mc.level.getBlockState(
                        BlockPos.containing(mc.player.getEyePosition())).getFluidState();
                int si = mediumOrd(srcFluid);
                int di = mediumOrd(dstFluid);
                if (si != di) extraGain *= XM_GAIN[si][di];
                if (extraGain > 0.01f) {
                    Player srcPlayer = findNearestPlayer(mc, pos);
                    if (srcPlayer != null) {
                        Vec3 lookDir = srcPlayer.getLookAngle();
                        Vec3 toListener = mc.player.getEyePosition().subtract(pos).normalize();
                        float dot = (float) lookDir.dot(toListener);
                        float rawDir = 0.2f + 0.8f * Math.max(0, dot);
                        float[] smooth = DIR_SMOOTH.computeIfAbsent(audioChannelId, k -> new float[]{1.0f});
                        smooth[0] += (rawDir - smooth[0]) * 0.15f;
                        extraGain *= smooth[0];
                    }
                }
            }
        }
        cir.setReturnValue(original * extraGain);
    }
    private static int mediumOrd(FluidState fluid) {
        if (!fluid.isEmpty()) {
            if (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA))
                return 2;
            return 1;
        }
        return 0;
    }
    private static Player findNearestPlayer(Minecraft mc, Vec3 pos) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            double d = p.distanceToSqr(pos);
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }
}
