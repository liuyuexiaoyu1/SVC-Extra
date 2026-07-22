package com.liuyue.svcextra.client.mixin;

import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ALSpeakerBase.class, remap = false)
public class AbsoluteLoudnessMixin {
    @Unique
    private static final float[][] XM_GAIN = {
        { 1.0f, 0.06f, 0.15f },
        { 0.06f, 1.0f, 0.30f },
        { 0.15f, 0.30f, 1.0f  },
    };

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
                if (di == 1) extraGain *= 0.35f;
                else if (di == 2) extraGain *= 0.5f;
            }
        }
        cir.setReturnValue(original * extraGain);
    }

    @Unique
    private static int mediumOrd(FluidState fluid) {
        if (!fluid.isEmpty()) {
            if (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA))
                return 2;
            return 1;
        }
        return 0;
    }
}
