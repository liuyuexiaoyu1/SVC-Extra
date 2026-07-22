package com.liuyue.svcextra.client.mixin;

import com.liuyue.svcextra.client.audio.RayTracedReverb;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class MinecraftGizmoMixin {
    @Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("RETURN"), remap = false)
    private void onCollectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
        if (!RayTracedReverb.showDebugRays) return;
        var rays = RayTracedReverb.debugRays;
        if (rays.isEmpty()) return;
        for (var ray : rays) {
            Gizmos.line(ray.from(), ray.to(), ray.color(), ray.width()).setAlwaysOnTop();
        }
    }
}
