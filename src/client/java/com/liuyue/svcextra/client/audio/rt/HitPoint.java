package com.liuyue.svcextra.client.audio.rt;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
public record HitPoint(int round, Vec3 pos, double journey, double distance,
                       float absorption, float roughness, float hfGain, Direction face) {
    public double weight() {
        return (1 - absorption) / (journey * journey + 0.01);
    }
}
