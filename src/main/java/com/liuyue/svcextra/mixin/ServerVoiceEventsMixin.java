package com.liuyue.svcextra.mixin;
import com.liuyue.svcextra.network.VoiceConfigPayload;
import de.maxhenkel.voicechat.voice.server.ServerVoiceEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = ServerVoiceEvents.class, remap = false)
public class ServerVoiceEventsMixin {
    @Inject(method = "initializePlayerConnection", at = @At("TAIL"))
    private void onInitPlayerConnection(ServerPlayer player, CallbackInfo ci) {
        VoiceConfigPayload.sendTo(player);
    }
}
