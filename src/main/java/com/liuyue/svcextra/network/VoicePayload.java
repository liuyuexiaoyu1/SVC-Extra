package com.liuyue.svcextra.network;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;
public record VoicePayload(byte[] data) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("svc-extra", "voice");
    public static final Type<VoicePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VoicePayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, VoicePayload payload) {
            buf.writeBytes(payload.data);
        }
        @Override
        public VoicePayload decode(RegistryFriendlyByteBuf buf) {
            byte[] d = new byte[buf.readableBytes()];
            buf.readBytes(d);
            return new VoicePayload(d);
        }
    };
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void sendToClient(ServerPlayer player, byte[] encryptedData) {
        ServerPlayNetworking.send(player, new VoicePayload(encryptedData));
    }
}
