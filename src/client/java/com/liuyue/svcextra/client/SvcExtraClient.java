package com.liuyue.svcextra.client;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.audio.AudioPipeline;
import com.liuyue.svcextra.client.audio.DuckingManager;
import com.liuyue.svcextra.client.audio.McChannelClientSocket;
import com.liuyue.svcextra.client.audio.MusicPlayer;
import com.liuyue.svcextra.client.audio.MusicReceiver;
import com.liuyue.svcextra.client.audio.PlayerVelocityTracker;
import com.liuyue.svcextra.client.audio.RayTracedReverb;
import com.liuyue.svcextra.config.SvcExtraConfig;
import com.liuyue.svcextra.network.MusicPlayPayload;
import com.liuyue.svcextra.network.MusicStopPayload;
import com.liuyue.svcextra.network.VoiceConfigPayload;
import com.liuyue.svcextra.network.VoicePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.openal.AL11;

public class SvcExtraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AudioPipeline.init();
        ClientPlayNetworking.registerGlobalReceiver(
                VoicePayload.TYPE, (payload, _) -> McChannelClientSocket.onVoicePacket(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                VoiceConfigPayload.TYPE, (payload, _) -> {
                    SvcExtra.CONFIG.server.transport = SvcExtraConfig.Transport.valueOf(payload.transport());
                    SvcExtra.LOGGER.info("Server set transport to {}", payload.transport());
                });
        ClientPlayNetworking.registerGlobalReceiver(
                MusicPlayPayload.TYPE, (payload, _) -> MusicReceiver.onPayload(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                MusicStopPayload.TYPE, (_, _) -> MusicPlayer.stop());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;
            DuckingManager.tick();
            if (SvcExtra.CONFIG.client.rayTraceAudio) {
                RayTracedReverb.showDebugRays = SvcExtra.CONFIG.client.showRayTrace;
                RayTracedReverb.tick();
            } else {
                RayTracedReverb.clear();
            }
            MusicReceiver.processPending();
            MusicPlayer.tick();
            if (SvcExtra.CONFIG.client.rayTraceAudio) {
                PlayerVelocityTracker.tick();
                if (client.player != null) {
                    var pv = client.player.getDeltaMovement();
                    AL11.alListener3f(AL11.AL_VELOCITY, (float) pv.x, (float) pv.y, (float) pv.z);
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> AudioPipeline.shutdown());
    }
}
