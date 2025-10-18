package com.badlyac.sande.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * 多人版客端狀態（含相容層）：
 * - 新：以每位玩家 UUID 管理啟用與殘影樣本。
 * - 舊相容：保留 ACTIVE 欄位與 setActive(boolean) 供既有程式碼呼叫。
 */
public final class SandeClientState {
    private SandeClientState() {}

    /* ===================== 參數 ===================== */
    public static final int   MAX_SAMPLES = 20;        // 最多殘影數
    public static final int   SAMPLE_INTERVAL_TICKS = 2; // 每 0.1s 採樣一次（20tps 假設）

    /* ===================== 舊版相容層 ===================== */
    /** 舊版全域旗標（保留相容）：等同於本地玩家是否啟用 */
    @Deprecated public static boolean ACTIVE = false;

    /** 本地玩家啟用旗標（新） */
    public static boolean LOCAL_ACTIVE = false;

    /** 進/出場動畫與濾鏡（你現有 UI 用到） */
    public static float entryTicks = 0f, exitTicks = 0f;
    public static float tintStrength = 0f;

    /* ===================== 多人狀態 ===================== */
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final Deque<Sample> EMPTY = new ArrayDeque<>();

    private static Trail trailOf(UUID id) {
        return TRAILS.computeIfAbsent(id, k -> new Trail());
    }

    /* ===================== 對外 API（新） ===================== */

    /** 設定某位玩家（subject）的啟用狀態；S2C 封包會呼叫這個。 */
    public static void setActiveFor(UUID subject, boolean active) {
        if (subject == null) return;
        Trail t = trailOf(subject);
        t.active = active;

        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = (mc != null && mc.player != null && subject.equals(mc.player.getUUID()));

        if (isLocal) {
            LOCAL_ACTIVE = active;
            ACTIVE = active; // 舊欄位同步
            if (active) {
                entryTicks = 6f; exitTicks = 0f;
                // 進場：立刻拉起濾鏡
                tintStrength = Math.min(1f, tintStrength + 0.35f);
            } else {
                // 關閉：啟動退場動畫；確保之後會降到 0
                exitTicks = 6f; entryTicks = 0f;
                // 立刻停止新增樣本並清空當前樣本，避免「殘影留著」
                t.samples.clear();
            }
        } else {
            // 非本地：不要做畫面動畫；直接收乾淨
            if (!active) {
                t.samples.clear();
            }
        }
    }


    /** 查詢某位玩家是否啟用 Sande（用於渲染判斷） */
    public static boolean isActive(UUID id) {
        Trail t = TRAILS.get(id);
        return t != null && t.active;
    }

    /** 取得某位玩家的殘影樣本（只讀用） */
    public static Deque<Sample> samplesOf(UUID id) {
        Trail t = TRAILS.get(id);
        return (t == null) ? EMPTY : t.samples;
    }

    /** 客端每 tick 呼叫：對啟用中的玩家採樣、更新濾鏡動畫 */
    public static void tickClientAll(Minecraft mc) {
        if (mc == null || mc.level == null) return;

        // 對「啟用中的玩家」按節流採樣
        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            Trail t = TRAILS.get(id);
            if (t == null || !t.active) continue;

            if (--t.sampleCooldown > 0) continue;
            t.sampleCooldown = SAMPLE_INTERVAL_TICKS;

            if (t.samples.size() >= MAX_SAMPLES) t.samples.removeLast();
            t.samples.addFirst(new Sample(
                    (float)p.getX(), (float)p.getY(), (float)p.getZ(),
                    p.getYRot(), p.getXRot(),
                    p.yBodyRot, p.yHeadRot,
                    p.walkAnimation.position(), p.walkAnimation.speed(),
                    p.tickCount
            ));
        }

        // 濾鏡進退場動畫
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

        final float MAX_ON = 0.22f;   // 開啟時的目標透明度（建議 0.18~0.25）
        final float LERP_ON = 0.22f;  // 朝目標靠攏的速率（0~1 越大越快）
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

    /* ===================== 對外 API（舊，相容） ===================== */

    /**
     * 舊版方法：切換「本地玩家」啟用狀態。
     * 仍然有效；內部委派到 setActiveFor(localUUID, active)。
     */
    @Deprecated
    public static void setActive(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        setActiveFor(mc.player.getUUID(), active);
    }

    /* ===================== 資料結構 ===================== */

    private static final class Trail {
        boolean active = false;
        int sampleCooldown = 0; // 以 tick 計
        Deque<Sample> samples = new ArrayDeque<>();
    }

    /** 殘影採樣（不再包含 life；用樣本序位置做透明度衰減） */
    public record Sample(
            float x, float y, float z,
            float yaw, float pitch,
            float bodyYaw, float headYaw,
            float limbSwing, float limbSwingAmount,
            int ageTicks
    ) {}
}
