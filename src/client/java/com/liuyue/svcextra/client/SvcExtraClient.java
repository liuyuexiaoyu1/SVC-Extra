package com.liuyue.svcextra.client;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.audio.AudioPipeline;
import com.liuyue.svcextra.client.audio.DuckingManager;
import com.liuyue.svcextra.client.audio.McChannelClientSocket;
import com.liuyue.svcextra.client.audio.MusicPlayer;
import com.liuyue.svcextra.client.audio.PlayerVelocityTracker;
import com.liuyue.svcextra.client.audio.RayTracedReverb;
import com.liuyue.svcextra.config.SvcExtraConfig;
import com.liuyue.svcextra.network.VoiceConfigPayload;
import com.liuyue.svcextra.network.VoicePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.openal.AL11;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
public class SvcExtraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AudioPipeline.init();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                VoicePayload.TYPE, (payload, _) -> McChannelClientSocket.onVoicePacket(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                VoiceConfigPayload.TYPE, (payload, _) -> {
                    SvcExtra.CONFIG.server.transport = SvcExtraConfig.Transport.valueOf(payload.transport());
                    SvcExtra.LOGGER.info("Server set transport to {}", payload.transport());
                });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(ClientCommands.literal("svc-music")
                .then(ClientCommands.literal("play")
                        .then(ClientCommands.argument("player", string())
                                .then(ClientCommands.argument("file", string())
                                        .executes(ctx -> {
                                            String playerName = ctx.getArgument("player", String.class);
                                            String fileName = ctx.getArgument("file", String.class);
                                            var mc = Minecraft.getInstance();
                                            if (mc.level == null) {
                                                ctx.getSource().sendError(Component.literal("不在游戏中"));
                                                return 0;
                                            }
                                            Player target = null;
                                            for (Player p : mc.level.players()) {
                                                if (p.getDisplayName().getString().equals(playerName)
                                                        || p.getScoreboardName().equals(playerName)) {
                                                    target = p;
                                                    break;
                                                }
                                            }
                                            if (target == null) {
                                                ctx.getSource().sendError(Component.literal("§c找不到玩家: " + playerName));
                                                return 0;
                                            }
                                            String msg = MusicPlayer.play(target, fileName, 1.0f);
                                            ctx.getSource().sendFeedback(Component.literal(msg));
                                            return 1;
                                        })))
                )
                .then(ClientCommands.literal("stop")
                        .executes(ctx -> {
                            String msg = MusicPlayer.stop();
                            ctx.getSource().sendFeedback(Component.literal(msg));
                            return 1;
                        })
                )
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;
            DuckingManager.tick();
            if (SvcExtra.CONFIG.client.rayTraceAudio) {
                RayTracedReverb.showDebugRays = SvcExtra.CONFIG.client.showRayTrace;
                RayTracedReverb.tick();
            } else {
                RayTracedReverb.clear();
            }
            MusicPlayer.tick();
            if (SvcExtra.CONFIG.client.rayTraceAudio) {
                PlayerVelocityTracker.tick();
                if (client.player != null) {
                    var pv = client.player.getDeltaMovement();
                    AL11.alListener3f(AL11.AL_VELOCITY, (float) pv.x, (float) pv.y, (float) pv.z);
                }
            }
        });
    }
}
