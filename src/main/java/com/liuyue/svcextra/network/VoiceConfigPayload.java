package com.liuyue.svcextra.network;
import com.liuyue.svcextra.SvcExtra;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
public record VoiceConfigPayload(String transport) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("svc-extra", "config");
    public static final Type<VoiceConfigPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceConfigPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, VoiceConfigPayload p) {
            buf.writeUtf(p.transport);
        }
        @Override
        public VoiceConfigPayload decode(RegistryFriendlyByteBuf buf) {
            return new VoiceConfigPayload(buf.readUtf());
        }
    };
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new VoiceConfigPayload(SvcExtra.CONFIG.server.transport.name()));
    }
}
