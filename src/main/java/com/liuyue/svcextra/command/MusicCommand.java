package com.liuyue.svcextra.command;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.network.MusicPlayPayload;
import com.liuyue.svcextra.network.MusicStopPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MusicCommand {

    private static final int CHUNK_SIZE = 1000000;

    public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("svc-music")
                .then(Commands.literal("play")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("filename", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            Path musicDir = FabricLoader.getInstance().getConfigDir().resolve("svc-extra").resolve("music");
                                            if (Files.exists(musicDir)) {
                                                try (Stream<Path> files = Files.list(musicDir)) {
                                                    files.filter(Files::isRegularFile).forEach(f -> builder.suggest(f.getFileName().toString()));
                                                } catch (IOException ignored) {}
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            String fileName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "filename");
                                            Path musicDir = FabricLoader.getInstance().getConfigDir().resolve("svc-extra").resolve("music");
                                            Path filePath = musicDir.resolve(fileName);

                                            if (!Files.exists(filePath)) {
                                                ctx.getSource().sendFailure(Component.literal("§c文件不存在: " + fileName));
                                                return 0;
                                            }

                                            var server = ctx.getSource().getServer();
                                            String name = filePath.getFileName().toString();
                                            ctx.getSource().sendSystemMessage(Component.literal("§e正在异步加载: " + name));

                                            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());

                                            CompletableFuture.runAsync(() -> {
                                                try {
                                                    byte[] rawBytes = Files.readAllBytes(filePath);
                                                    if (rawBytes.length == 0) {
                                                        server.execute(() -> ctx.getSource().sendFailure(Component.literal("§c文件为空")));
                                                        return;
                                                    }

                                                    if (rawBytes.length > 50 * 1024 * 1024) {
                                                        server.execute(() -> ctx.getSource().sendFailure(Component.literal("§c文件过大（>50MB）")));
                                                        return;
                                                    }

                                                    int totalChunks = (rawBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
                                                    int batchSize = 5;
                                                    for (int i = 0; i < totalChunks; i += batchSize) {
                                                        int endChunk = Math.min(i + batchSize, totalChunks);
                                                        int finalI = i;
                                                        server.execute(() -> {
                                                            for (int chunkIndex = finalI; chunkIndex < endChunk; chunkIndex++) {
                                                                int from = chunkIndex * CHUNK_SIZE;
                                                                int to = Math.min(from + CHUNK_SIZE, rawBytes.length);
                                                                byte[] chunk = new byte[to - from];
                                                                System.arraycopy(rawBytes, from, chunk, 0, chunk.length);

                                                                MusicPlayPayload payload = new MusicPlayPayload(
                                                                        chunk, name, target.getUUID(), totalChunks, chunkIndex
                                                                );
                                                                for (ServerPlayer p : players) {
                                                                    ServerPlayNetworking.send(p, payload);
                                                                }
                                                            }
                                                        });
                                                        if (endChunk < totalChunks) {
                                                            try {
                                                                Thread.sleep(50);
                                                            } catch (InterruptedException e) {
                                                                Thread.currentThread().interrupt();
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    server.execute(() -> {
                                                        server.getPlayerList().broadcastSystemMessage(
                                                                Component.literal("§e[svc-extra] §a正在播放 §e" + name + "§a 于 §e" + target.getDisplayName().getString()),
                                                                false
                                                        );
                                                        SvcExtra.LOGGER.info("Music '{}' sent to {} players ({} chunks)",
                                                                name, players.size(), totalChunks);
                                                    });

                                                } catch (IOException e) {
                                                    server.execute(() -> ctx.getSource().sendFailure(
                                                            Component.literal("§c读取失败: " + e.getMessage())
                                                    ));
                                                } catch (OutOfMemoryError e) {
                                                    server.execute(() -> ctx.getSource().sendFailure(
                                                            Component.literal("§c内存不足，文件可能过大")
                                                    ));
                                                }
                                            });

                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("§e[svc-extra] §a音乐已停止"), false);
                            MusicStopPayload stop = new MusicStopPayload();
                            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                ServerPlayNetworking.send(p, stop);
                            }
                            return 1;
                        })
                )
        );
    }
}