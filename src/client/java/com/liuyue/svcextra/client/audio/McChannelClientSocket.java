package com.liuyue.svcextra.client.audio;
import com.liuyue.svcextra.network.VoicePayload;
import de.maxhenkel.voicechat.api.ClientVoicechatSocket;
import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.plugins.impl.RawUdpPacketImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
public class McChannelClientSocket implements ClientVoicechatSocket {
    private static final BlockingQueue<RawUdpPacket> globalQueue = new LinkedBlockingQueue<>();
    public static void onVoicePacket(VoicePayload payload) {
        globalQueue.add(new RawUdpPacketImpl(payload.data(),
                InetSocketAddress.createUnresolved("0.0.0.0", 0),
                System.currentTimeMillis()));
    }
    @Override
    public void open() {}
    @Override
    public RawUdpPacket read() throws Exception {
        RawUdpPacket pkt = globalQueue.poll(100, TimeUnit.MILLISECONDS);
        return pkt != null ? pkt : globalQueue.take();
    }
    @Override
    public void send(byte[] data, SocketAddress address) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new VoicePayload(data)));
        }
    }
    @Override
    public void close() {
        globalQueue.clear();
    }
    @Override
    public boolean isClosed() { return false; }
}
