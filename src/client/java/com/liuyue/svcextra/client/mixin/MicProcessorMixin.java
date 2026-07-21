package com.liuyue.svcextra.client.mixin;
import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.audio.AudioPipeline;
import com.liuyue.svcextra.config.SvcExtraConfig;
import de.maxhenkel.voicechat.voice.client.MicrophoneProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MicrophoneProcessor.class)
public class MicProcessorMixin {
    @Inject(method = "useDenoiser", at = @At("HEAD"), cancellable = true, remap = false)
    private void overrideUseDenoiser(CallbackInfoReturnable<Boolean> cir) {
        if (SvcExtra.CONFIG.client.noiseCancelMode != SvcExtraConfig.NoiseCancelMode.OFF) {
            cir.setReturnValue(false);
        }
    }
    @Inject(method = "preprocess", at = @At("HEAD"), remap = false)
    private void onPreprocess(short[] audio, CallbackInfo ci) {
        if (SvcExtra.CONFIG.client.noiseCancelMode == SvcExtraConfig.NoiseCancelMode.OFF) return;
        AudioPipeline.processShort(audio, SvcExtra.CONFIG);
    }
}
