package com.liuyue.svcextra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record MusicPlayPayload(byte[] pcmBytes, String fileName, UUID sourcePlayerId, int totalChunks, int chunkIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicPlayPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("svc-extra", "music_play"));

    public static final StreamCodec<FriendlyByteBuf, MusicPlayPayload> CODEC = new StreamCodec<>() {
        public void encode(FriendlyByteBuf buf, MusicPlayPayload p) {
            buf.writeVarInt(p.pcmBytes.length);
            buf.writeBytes(p.pcmBytes);
            buf.writeUtf(p.fileName, 256);
            buf.writeUUID(p.sourcePlayerId);
            buf.writeVarInt(p.totalChunks);
            buf.writeVarInt(p.chunkIndex);
        }
        public MusicPlayPayload decode(FriendlyByteBuf buf) {
            int len = buf.readVarInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            String name = buf.readUtf(256);
            UUID id = buf.readUUID();
            int total = buf.readVarInt();
            int idx = buf.readVarInt();
            return new MusicPlayPayload(bytes, name, id, total, idx);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
