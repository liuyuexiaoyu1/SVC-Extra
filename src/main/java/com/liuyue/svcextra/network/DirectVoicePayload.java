package com.liuyue.svcextra.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record DirectVoicePayload(byte[] opusData, java.util.UUID sender) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("svc-extra", "direct-voice");
    public static final Type<DirectVoicePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DirectVoicePayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, DirectVoicePayload p) {
            buf.writeUUID(p.sender);
            buf.writeBytes(p.opusData);
        }
        @Override
        public DirectVoicePayload decode(RegistryFriendlyByteBuf buf) {
            var sender = buf.readUUID();
            byte[] opus = new byte[buf.readableBytes()];
            buf.readBytes(opus);
            return new DirectVoicePayload(opus, sender);
        }
    };
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
