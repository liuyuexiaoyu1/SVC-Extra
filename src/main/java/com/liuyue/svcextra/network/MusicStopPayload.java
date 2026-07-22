package com.liuyue.svcextra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicStopPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicStopPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("svc-extra", "music_stop"));

    public static final StreamCodec<FriendlyByteBuf, MusicStopPayload> CODEC = StreamCodec.unit(new MusicStopPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
