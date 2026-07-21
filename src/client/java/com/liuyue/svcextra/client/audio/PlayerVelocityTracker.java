package com.liuyue.svcextra.client.audio;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class PlayerVelocityTracker {
    private static final Map<UUID, PlayerPos> history = new HashMap<>();
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        for (Player p : mc.level.players()) {
            Vec3 pos = p.getPosition(1.0F);
            history.compute(p.getUUID(), (id, prev) -> {
                if (prev == null) return new PlayerPos(pos, pos, now, 0);
                return new PlayerPos(prev.current, pos, prev.time, now);
            });
        }
    }
    public static Vec3 getVelocity(UUID playerId) {
        PlayerPos pp = history.get(playerId);
        if (pp == null || pp.time == pp.lastTime) return Vec3.ZERO;
        double dt = (pp.time - pp.lastTime) / 1000.0;
        if (dt <= 0) return Vec3.ZERO;
        return pp.current.subtract(pp.last).scale(1.0 / dt);
    }
    public static Vec3 getNearestPlayerVelocity(Vec3 pos) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return Vec3.ZERO;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            double d = p.distanceToSqr(pos);
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest == null) return Vec3.ZERO;
        return getVelocity(nearest.getUUID());
    }
    private record PlayerPos(Vec3 last, Vec3 current, long lastTime, long time) {}
}
