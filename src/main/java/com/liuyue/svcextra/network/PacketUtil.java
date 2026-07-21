package com.liuyue.svcextra.network;
import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.config.SvcExtraConfig;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.plugins.impl.RawUdpPacketImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.net.InetSocketAddress;
public class PacketUtil {
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(VoicePayload.TYPE, VoicePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VoicePayload.TYPE, VoicePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoiceConfigPayload.TYPE, VoiceConfigPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VoicePayload.TYPE, (payload, context) -> {
            if (SvcExtra.CONFIG.server.transport != SvcExtraConfig.Transport.MC_CHANNEL) {
                SvcExtra.LOGGER.debug("MC_CHANNEL packet ignored (transport={})", SvcExtra.CONFIG.server.transport);
                return;
            }
            var svcServer = Voicechat.SERVER != null ? Voicechat.SERVER.getServer() : null;
            if (svcServer == null) return;
            var addr = InetSocketAddress.createUnresolved("mc",
                    Math.abs(context.player().getUUID().hashCode() % 65535));
            svcServer.addRawPacket(new RawUdpPacketImpl(payload.data(), addr, System.currentTimeMillis()));
            SvcExtra.LOGGER.debug("MC_CHANNEL: packet from {} enqueued ({} bytes)",
                    context.player().getName().getString(), payload.data().length);
        });
    }
}
