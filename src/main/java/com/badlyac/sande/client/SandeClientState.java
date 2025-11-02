package com.badlyac.sande.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public final class SandeClientState {
    private SandeClientState() {
    }

    public static final int MAX_SAMPLES = 20;
    public static final int SAMPLE_INTERVAL_TICKS = 2;

    @Deprecated
    public static boolean ACTIVE = false;
    public static boolean LOCAL_ACTIVE = false;

    public static float entryTicks = 0f, exitTicks = 0f;
    public static float tintStrength = 0f;

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final Deque<Sample> EMPTY = new ArrayDeque<>();

    private static Trail trailOf(UUID id) {
        return TRAILS.computeIfAbsent(id, k -> new Trail());
    }

    public static void setActiveFor(UUID subject, boolean active) {
        if (subject == null) return;
        Trail t = trailOf(subject);
        t.active = active;

        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = (mc != null && mc.player != null && subject.equals(mc.player.getUUID()));

        if (isLocal) {
            LOCAL_ACTIVE = active;
            ACTIVE = active;
            if (active) {
                entryTicks = 6f;
                exitTicks = 0f;
                tintStrength = Math.min(1f, tintStrength + 0.35f);
            } else {
                exitTicks = 6f;
                entryTicks = 0f;
                t.samples.clear();
            }
        } else {
            if (!active) {
                t.samples.clear();
            }
        }
    }

    public static boolean isActive(UUID id) {
        Trail t = TRAILS.get(id);
        return t != null && t.active;
    }

    public static Deque<Sample> samplesOf(UUID id) {
        Trail t = TRAILS.get(id);
        return (t == null) ? EMPTY : t.samples;
    }

    public static void tickClientAll(Minecraft mc) {
        if (mc == null || mc.level == null) return;

        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            Trail t = TRAILS.get(id);
            if (t == null || !t.active) continue;

            if (--t.sampleCooldown > 0) continue;
            t.sampleCooldown = SAMPLE_INTERVAL_TICKS;

            if (t.samples.size() >= MAX_SAMPLES) t.samples.removeLast();
            t.samples.addFirst(new Sample(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                    p.getYRot(), p.getXRot(),
                    p.yBodyRot, p.yHeadRot,
                    p.walkAnimation.position(), p.walkAnimation.speed(),
                    p.tickCount
            ));
        }

        if (entryTicks > 0f) {
            entryTicks -= 1f;
            tintStrength = Math.min(1f, tintStrength + 0.12f);
        } else if (exitTicks > 0f) {
            exitTicks -= 1f;
            tintStrength = Math.max(0f, tintStrength - 0.12f);
        } else if (!LOCAL_ACTIVE) {
            tintStrength = Math.max(0f, tintStrength - 0.04f);
        }

        if (!LOCAL_ACTIVE && entryTicks <= 0f && exitTicks <= 0f && tintStrength < 0.02f) {
            tintStrength = 0f;
        }

        final float MAX_ON = 0.22f;
        final float LERP_ON = 0.22f;
        final float LERP_OFF = 0.25f;

        float target = LOCAL_ACTIVE ? MAX_ON : 0f;

        if (entryTicks > 0f) {
            float t = entryTicks / 6f;          // 1 -> 0
            target = Math.min(MAX_ON, target + 0.06f * t * t);
        }
        if (exitTicks > 0f) {
            float t = exitTicks / 6f;           // 1 -> 0
            target = Math.min(MAX_ON, target + 0.04f * t);
        }

        float k = (target > tintStrength) ? LERP_ON : LERP_OFF;
        tintStrength += (target - tintStrength) * k;

        if (tintStrength < 0.001f) tintStrength = 0f;
        if (tintStrength > MAX_ON) tintStrength = MAX_ON;
    }

    @Deprecated
    public static void setActive(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        setActiveFor(mc.player.getUUID(), active);
    }

    private static final class Trail {
        boolean active = false;
        int sampleCooldown = 0;
        Deque<Sample> samples = new ArrayDeque<>();
    }

    public record Sample(
            float x, float y, float z,
            float yaw, float pitch,
            float bodyYaw, float headYaw,
            float limbSwing, float limbSwingAmount,
            int ageTicks
    ) {
    }
}
