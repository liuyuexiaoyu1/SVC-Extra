package com.liuyue.svcextra.client.audio;
import com.liuyue.svcextra.client.audio.rt.BlockSoundProperty;
import com.liuyue.svcextra.client.audio.rt.HitPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import static org.lwjgl.openal.EXTEfx.*;
public class RayTracedReverb {
    private static final List<HitPoint> HIT_POINTS = new CopyOnWriteArrayList<>();
    private static int auxSlot = -1;
    private static int reverbEffect = -1;
    private static long lastUpdateMs = 0;
    private static int maxDist = 64, maxBounce = 5, rayCount = 300;
    public static final List<Ray> debugRays = new CopyOnWriteArrayList<>();
    public static boolean showDebugRays = false;
    private static final ForkJoinPool RAY_POOL;
    static {
        AtomicInteger idx = new AtomicInteger();
        RAY_POOL = new ForkJoinPool(8, pool -> {
            var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setName("SvcExtra-RayTrace-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }, null, false);
    }
    public static volatile float density = 0.5f;
    public static volatile float diffusion = 0.5f;
    public static volatile float hfGain = 0.9f;
    public static volatile float rt60 = 1.5f;
    public static volatile float earlyRefGain = 0.05f;
    public static volatile float earlyRefDelay = 0.007f;
    public static volatile float lateRefGain = 0.5f;
    public static volatile float lateRefDelay = 0.01f;
    public static volatile float echoTime = 0.25f;
    public static volatile float echoDepth = 0.05f;
    public static volatile Vec3 earlyRefPos = Vec3.ZERO;
    public static volatile Vec3 lateRefPos = Vec3.ZERO;
    private static boolean inWater = false;
    private static boolean inLava = false;
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        maxDist = 64; maxBounce = 5; rayCount = 300;
        long now = System.currentTimeMillis();
        BlockPos eyePos = BlockPos.containing(mc.player.getEyePosition());
        FluidState fluid = mc.level.getBlockState(eyePos).getFluidState();
        inWater = !fluid.isEmpty() && fluid.getType().isSame(Fluids.WATER);
        inLava = !fluid.isEmpty() && fluid.getType().isSame(Fluids.LAVA);
        int interval = Math.max(5, Math.min(3000, com.liuyue.svcextra.SvcExtra.CONFIG.client.rayTraceIntervalMs));
        if (now - lastUpdateMs > interval) {
            lastUpdateMs = now;
            updateReverb(mc);
        }
        updateReflectionPan(mc);
    }
    public static void clear() { HIT_POINTS.clear(); debugRays.clear(); }
    public static void disable() {
        clear();
        if (reverbEffect != -1) { alDeleteEffects(reverbEffect); reverbEffect = -1; }
        if (auxSlot != -1) { alDeleteAuxiliaryEffectSlots(auxSlot); auxSlot = -1; }
    }
    public static boolean isUnderwater() { return inWater || inLava; }
    public static boolean isInWater() { return inWater; }
    public static boolean isInLava() { return inLava; }
    private static void updateReverb(Minecraft mc) {
        clear();
        Vec3 earPos = mc.player.getEyePosition();
        List<Vector3d> rays0 = generateFibonacciRays(rayCount, 0);
        RAY_POOL.submit(() -> rays0.parallelStream().forEach(ray ->
            traceRay(mc, earPos, ray, 0x80ffffff)
        )).join();
        List<Vector3d> rays1 = generateFibonacciRays(rayCount, 0.5);
        RAY_POOL.submit(() -> rays1.parallelStream().forEach(ray ->
            traceRay(mc, earPos, ray, 0x8000ff00)
        )).join();
        if (HIT_POINTS.isEmpty()) {
            setDryEnvironment();
            return;
        }
        int n = HIT_POINTS.size();
        double journeySum = 0, distanceSum = 0, weightSum = 0;
        double roughnessW = 0, absorptionW = 0, hfW = 0;
        double earlyGS = 0, earlyDS = 0, earlyWS = 0;
        double lateGS = 0, lateDS = 0, lateWS = 0;
        double[] earlyPA = new double[3], latePA = new double[3];
        float soundSpeed = inWater ? 1500f : 340f;
        for (HitPoint hp : HIT_POINTS) {
            double w = hp.weight();
            journeySum += hp.journey(); distanceSum += hp.distance();
            weightSum += w;
            roughnessW += hp.roughness() * w;
            absorptionW += hp.absorption() * w;
            hfW += hp.hfGain() * w;
            if (hp.journey() <= 17) {
                earlyGS += (1 - hp.absorption()) * w;
                earlyDS += hp.journey() * w; earlyWS += w;
                earlyPA[0] += hp.pos().x * w; earlyPA[1] += hp.pos().y * w; earlyPA[2] += hp.pos().z * w;
            }
            if (hp.journey() > 17 && hp.journey() <= 34) {
                lateGS += (1 - hp.absorption()) * w;
                lateDS += hp.journey() * w; lateWS += w;
                latePA[0] += hp.pos().x * w; latePA[1] += hp.pos().y * w; latePA[2] += hp.pos().z * w;
            }
        }
        if (weightSum == 0) return;
        double meanJourney = journeySum / n, meanDist = distanceSum / n;
        double meanAbs = absorptionW / weightSum, meanRough = roughnessW / weightSum;
        double hfAvg = hfW / weightSum;
        double sqDiff = 0;
        for (HitPoint hp : HIT_POINTS) sqDiff += (hp.journey() - meanJourney) * (hp.journey() - meanJourney);
        density = (float) Math.min(1, Math.sqrt(sqDiff / n) / (maxDist / 2f) + (inWater ? 0.6f : 0));
        diffusion = (float) Math.min(1, meanRough);
        double airHf = Math.pow(0.99, meanJourney);
        hfGain = (float) Math.min(2, Math.max(0, Math.sqrt(airHf * hfAvg) * (inLava ? 0.35f : (inWater ? 0.15f : 1))));
        double collisionCount = -6 / Math.log10(1 - meanAbs + 1e-10);
        double openSpace = (inWater || inLava) ? 1 : Math.min(1, (double) n / (rayCount * maxBounce * 2));
        double r = collisionCount * meanDist / 340 * openSpace;
        rt60 = (float) Math.min(20, Math.max(0.1, (20 - 400 / (r + 20)) * (inLava ? 3.6f : (inWater ? 6.0f : 1))));
        if (earlyWS > 0) {
            earlyRefGain = (float) Math.min(6.0f, Math.max(0, 4 * Math.sqrt(earlyGS / n) * openSpace * ((inWater || inLava) ? 1.5f : 1)));
            earlyRefDelay = (float) (earlyDS / earlyWS / soundSpeed);
            earlyRefPos = new Vec3(earlyPA[0] / earlyWS, earlyPA[1] / earlyWS, earlyPA[2] / earlyWS);
        }
        if (lateWS > 0) {
            lateRefGain = (float) Math.min(10, Math.max(0, 20 * Math.sqrt(lateGS / n) * openSpace));
            lateRefDelay = (float) (lateDS / lateWS / soundSpeed) - earlyRefDelay;
            lateRefPos = new Vec3(latePA[0] / lateWS, latePA[1] / lateWS, latePA[2] / lateWS);
        }
        echoTime = (float) Math.min(0.25, Math.max(0.075, 4 * meanDist / 340));
        echoDepth = Math.min(1, (float) (1.0 / (1.0 + meanRough)));
        applyToEfx();
    }
    private static void applyToEfx() {
        if (auxSlot == -1) {
            auxSlot = alGenAuxiliaryEffectSlots();
            reverbEffect = alGenEffects();
            alEffecti(reverbEffect, AL_EFFECT_TYPE, AL_EFFECT_EAXREVERB);
        }
        alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, density);
        alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, diffusion);
        alEffectf(reverbEffect, AL_EAXREVERB_GAIN, inLava ? 0.5f : (inWater ? 0.8f : 0.5f));
        alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, hfGain);
        alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, 1);
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, rt60);
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, inLava ? 0.4f : (inWater ? 0.2f : hfGain));
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, 1);
        alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, earlyRefGain);
        alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, earlyRefDelay);
        alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, lateRefGain);
        alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, lateRefDelay);
        alEffectf(reverbEffect, AL_EAXREVERB_ECHO_TIME, Float.isNaN(echoTime) ? 0.25f : echoTime);
        alEffectf(reverbEffect, AL_EAXREVERB_ECHO_DEPTH, Float.isNaN(echoDepth) ? 0.05f : echoDepth);
        alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_TIME, 0.4f);
        alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_DEPTH, 0.025f);
        alAuxiliaryEffectSloti(auxSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);
    }
    private static void setDryEnvironment() {
        density = 0f; diffusion = 0.5f; hfGain = 0.3f; rt60 = 0.1f;
        earlyRefGain = 0f; earlyRefDelay = 0.007f;
        lateRefGain = 0f; lateRefDelay = 0.01f;
        echoTime = 0.075f; echoDepth = 0f;
        applyToEfx();
    }
    private static void updateReflectionPan(Minecraft mc) {
        if (reverbEffect == -1) return;
        var earPos = mc.player.getEyePosition();
        float yaw = mc.player.getViewYRot(1.0F), pitch = mc.player.getViewXRot(1.0F);
        var q = new org.joml.Quaternionf().rotateY(-yaw * (float)Math.PI / 180F)
                .rotateX(pitch * (float)Math.PI / 180F);
        var ep = new org.joml.Vector3f((float)(earlyRefPos.x - earPos.x), (float)(earlyRefPos.y - earPos.y), (float)(earlyRefPos.z - earPos.z)).rotate(q);
        var lp = new org.joml.Vector3f((float)(lateRefPos.x - earPos.x), (float)(lateRefPos.y - earPos.y), (float)(lateRefPos.z - earPos.z)).rotate(q);
    }
    private static void traceRay(Minecraft mc, Vec3 earPos, Vector3d ray, int debugColor) {
        Vec3 pos = earPos;
        float journey = 0;
        for (int round = 0; round < maxBounce; round++) {
            if (journey >= maxDist) break;
            Vec3 to = pos.add(ray.x * maxDist, ray.y * maxDist, ray.z * maxDist);
            var ctx = new ClipContext(pos, to, ClipContext.Block.COLLIDER,
                    (inWater || inLava) ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY, CollisionContext.empty());
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() == HitResult.Type.MISS) break;
            float dist = (float) hit.getLocation().distanceTo(pos);
            if (dist == 0) break;
            journey += dist;
            BlockPos hitPos = hit.getBlockPos();
            BlockState block = mc.level.getBlockState(hitPos);
            FluidState fluid = block.getFluidState();
            float absorption, roughness, hf;
            if (!fluid.isEmpty()) {
                if (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA)) {
                    absorption = 0.5f; roughness = 0.9f; hf = 0.8f;   
                } else {
                    absorption = 0.9f; roughness = 0.1f; hf = 0.05f;  
                }
            } else {
                var soundType = block.getSoundType();
                var props = BlockSoundProperty.get(soundType);
                absorption = props.absorption();
                roughness = props.roughness();
                hf = props.hfGain();
            }
            HIT_POINTS.add(new HitPoint(round, hit.getLocation(), journey, dist,
                    absorption, roughness, hf, hit.getDirection()));
            if (showDebugRays) {
                debugRays.add(new Ray(pos, hit.getLocation(), debugColor, 1f));
            }
            if (block.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                    || block.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
                    || block.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                    || block.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                float bleed = 0.3f;
                Vec3 past = hit.getLocation().add(ray.x * bleed, ray.y * bleed, ray.z * bleed);
                journey += bleed;
                HIT_POINTS.add(new HitPoint(round, past, journey, bleed,
                        0.3f, roughness * 0.5f, hf * 0.7f, hit.getDirection()));
                pos = past;
                if (showDebugRays) {
                    debugRays.add(new Ray(hit.getLocation(), past, 0x40ffffff, 0.5f));
                }
                continue;
            }
            Vec3 hitPos3 = hit.getLocation();
            AABB segBounds = new AABB(pos, hitPos3).inflate(0.5);
            for (Entity e : mc.level.getEntities(null, segBounds)) {
                if (e instanceof Player && !e.equals(mc.player) && e.getBoundingBox().clip(pos, hitPos3).isPresent()) {
                    Vec3 entHit = e.getBoundingBox().clip(pos, hitPos3).get();
                    float eDist = (float) entHit.distanceTo(pos);
                    HIT_POINTS.add(new HitPoint(round, entHit, journey + eDist, eDist,
                            0.7f, 0.6f, 0.3f, hit.getDirection()));
                    if (showDebugRays) {
                        debugRays.add(new Ray(pos, entHit, debugColor, 1f));
                    }
                }
            }
            boolean hitFluid = !fluid.isEmpty();
            boolean earInFluid = inWater || inLava;
            boolean refract = false;
            if (hitFluid && !earInFluid) {
                refract = true; // air->water/lava
            }
            if (!hitFluid && earInFluid && round > 1) {
                refract = true; // water/lava->air (only after interior bounces)
            }
            if (refract && Math.random() < 0.3) {
                double squeeze = hitFluid ? 1.33 : 0.75;
                Vec3 refrEnd = hit.getLocation().add(ray.x * squeeze * 3, ray.y * squeeze * 3, ray.z * squeeze * 3);
                float refrDist = (float) refrEnd.distanceTo(hit.getLocation());
                float refrAbsorb = Math.min(0.98f, absorption + 0.5f);
                journey += refrDist;
                HIT_POINTS.add(new HitPoint(round + 1, refrEnd, journey, refrDist,
                        refrAbsorb, 0.8f, 0.05f, hit.getDirection()));
                if (showDebugRays) {
                    debugRays.add(new Ray(hit.getLocation(), refrEnd, debugColor, 0.4f));
                }
            }
            switch (hit.getDirection()) {
                case UP, DOWN -> ray.mul(1, -1, 1);
                case NORTH, SOUTH -> ray.mul(1, 1, -1);
                case EAST, WEST -> ray.mul(-1, 1, 1);
            }
            pos = hit.getLocation();
        }
    }
    private static List<Vector3d> generateFibonacciRays(int n, double offset) {
        double phi = Math.PI * (3 - Math.sqrt(5));
        var rays = new ArrayList<Vector3d>(n);
        for (int i = 0; i < n; i++) {
            double y = 1 - ((i + offset) / (float) (n - 1 + offset)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = phi * (i + offset);
            rays.add(new Vector3d(Math.cos(theta) * r, y, Math.sin(theta) * r));
        }
        return rays;
    }
    public static int getAuxSlot() {
        if (auxSlot == -1) {
            auxSlot = alGenAuxiliaryEffectSlots();
            reverbEffect = alGenEffects();
            alEffecti(reverbEffect, AL_EFFECT_TYPE, AL_EFFECT_EAXREVERB);
        }
        return auxSlot;
    }
    public static void cleanup() {
        if (reverbEffect != -1) { alDeleteEffects(reverbEffect); reverbEffect = -1; }
        if (auxSlot != -1) { alDeleteAuxiliaryEffectSlots(auxSlot); auxSlot = -1; }
        HIT_POINTS.clear(); debugRays.clear();
    }
    public record Ray(Vec3 from, Vec3 to, int color, float width) {}
}
