package com.liuyue.svcextra.mixin;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.config.SvcExtraConfig;
import com.liuyue.svcextra.network.VoicePayload;
import de.maxhenkel.voicechat.voice.common.NetworkMessage;
import de.maxhenkel.voicechat.voice.server.ClientConnection;
import de.maxhenkel.voicechat.voice.server.Server;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Server.class)
public class McChannelMixin {
    @Final
    @Shadow
    private MinecraftServer server;

    @Inject(method = "sendPacketRaw", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(de.maxhenkel.voicechat.voice.common.Packet<?> packet,
                               ClientConnection connection,
                               CallbackInfo ci) throws Exception {
        if (SvcExtra.CONFIG.server.transport != SvcExtraConfig.Transport.MC_CHANNEL) return;
        ServerPlayer player = server.getPlayerList().getPlayer(connection.getPlayerUUID());
        if (player == null) return;
        Server self = (Server) (Object) this;
        NetworkMessage msg = new NetworkMessage(packet);
        byte[] fullPacket = msg.writeServer(self, connection);
        VoicePayload.sendToClient(player, fullPacket);
        ci.cancel();
    }
}
