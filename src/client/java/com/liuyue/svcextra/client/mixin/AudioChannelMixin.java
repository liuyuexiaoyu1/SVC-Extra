package com.liuyue.svcextra.client.mixin;

import com.liuyue.svcextra.SvcExtra;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.maxhenkel.voicechat.voice.client.AudioChannel;
import de.maxhenkel.voicechat.voice.client.AudioPacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AudioChannel.class)
public class AudioChannelMixin {
    @WrapOperation(method = "<init>",
              at = @At(value = "NEW",
                      target = "(I)Lde/maxhenkel/voicechat/voice/client/AudioPacketBuffer;"),
              remap = false)
    private AudioPacketBuffer createPacketBuffer(int packetThreshold, Operation<AudioPacketBuffer> original) {
        int tol = SvcExtra.CONFIG.client.networkTolerance;
        int frame = SvcExtra.CONFIG.client.frameLengthMs;
        int pktCount = Math.max(1, tol / Math.max(1, frame) / 3);
        return original.call(pktCount);
    }
}
