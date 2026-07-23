package com.liuyue.svcextra.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface VoiceFlushAccessors {
    @Accessor("connection")
    Connection svcextra$getConnection();

    @Mixin(Connection.class)
    interface ConnectionChannel {
        @Accessor("channel")
        Channel svcextra$getChannel();
    }
}
