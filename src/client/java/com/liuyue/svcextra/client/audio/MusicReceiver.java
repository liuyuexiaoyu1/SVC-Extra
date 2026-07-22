package com.liuyue.svcextra.client.audio;

import com.liuyue.svcextra.network.MusicPlayPayload;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MusicReceiver {
    private static final Map<UUID, MusicAssembly> assemblies = new HashMap<>();
    private static PendingPlay pending = null;

    public static void onPayload(MusicPlayPayload payload) {
        UUID id = payload.sourcePlayerId();
        MusicAssembly asm = assemblies.computeIfAbsent(id, _ -> new MusicAssembly(payload.totalChunks()));
        asm.addChunk(payload.chunkIndex(), payload.pcmBytes(), payload.fileName());
        if (asm.isComplete()) {
            assemblies.remove(id);
            pending = new PendingPlay(asm.fileName, asm.assemble(), id);
        }
    }

    public static void processPending() {
        if (pending == null) return;
        var p = pending;
        pending = null;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        Player target = mc.level.getPlayerByUUID(p.playerId);
        if (target == null) target = mc.player;
        if (target != null) {
            try {
                Path tmp = java.nio.file.Files.createTempFile("svc-music-", p.fileName);
                java.nio.file.Files.write(tmp, p.rawBytes);
                MusicPlayer.play(target, tmp.toString(), 1.0f);
            } catch (IOException e) {
                System.err.println("MusicReceiver write failed: " + e.getMessage());
            }
        }
    }

    private record PendingPlay(String fileName, byte[] rawBytes, UUID playerId) {}

    private static class MusicAssembly {
        final int total;
        final byte[][] chunks;
        String fileName = "";
        int received = 0;

        MusicAssembly(int total) { this.total = total; this.chunks = new byte[total][]; }

        void addChunk(int idx, byte[] data, String name) {
            if (idx < total && chunks[idx] == null) {
                chunks[idx] = data;
                if (!name.isEmpty()) fileName = name;
                received++;
            }
        }

        boolean isComplete() { return received >= total; }

        byte[] assemble() {
            int size = 0;
            for (byte[] c : chunks) size += c.length;
            byte[] full = new byte[size];
            int off = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, full, off, c.length);
                off += c.length;
            }
            return full;
        }
    }
}
