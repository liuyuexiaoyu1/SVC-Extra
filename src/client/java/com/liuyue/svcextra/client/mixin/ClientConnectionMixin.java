package com.liuyue.svcextra.client.mixin;
import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.client.audio.McChannelClientSocket;
import com.liuyue.svcextra.config.SvcExtraConfig;
import de.maxhenkel.voicechat.api.ClientVoicechatSocket;
import de.maxhenkel.voicechat.voice.client.ClientVoicechatConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = ClientVoicechatConnection.class, remap = false)
public class ClientConnectionMixin {
    @Final
    @Shadow
    @Mutable
    private ClientVoicechatSocket socket;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceSocket(CallbackInfo ci) {
        if (SvcExtra.CONFIG.server.transport != SvcExtraConfig.Transport.MC_CHANNEL) return;
        if (socket instanceof McChannelClientSocket) return;
        socket = new McChannelClientSocket();
        try { socket.open(); } catch (Exception e) {
            SvcExtra.LOGGER.error("McChannelClientSocket open failed", e);
        }
    }
}
