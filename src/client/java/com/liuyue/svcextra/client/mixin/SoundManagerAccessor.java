package com.liuyue.svcextra.client.mixin;
import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = ALSpeakerBase.class, remap = false)
public class SoundManagerAccessor {
    @Shadow
    protected SoundManager soundManager;
    private static volatile SoundManager captured = null;
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(CallbackInfo ci) {
        captured = soundManager;
    }
    public static SoundManager get() {
        return captured;
    }
}
