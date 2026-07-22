package com.liuyue.svcextra.client.mixin;

import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.voice.client.AudioChannel;
import de.maxhenkel.voicechat.voice.client.AudioPacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AudioChannel.class)
public class AudioChannelMixin {
    @Redirect(method = "<init>",
              at = @At(value = "NEW",
                      target = "(I)Lde/maxhenkel/voicechat/voice/client/AudioPacketBuffer;"),
              remap = false)
    private AudioPacketBuffer createPacketBuffer(int threshold) {
        int tol = SvcExtra.CONFIG.client.networkTolerance;
        int frame = SvcExtra.CONFIG.client.frameLengthMs;
        int pktCount = Math.max(1, tol / Math.max(1, frame) / 3);
        return new AudioPacketBuffer(pktCount);
    }
}
