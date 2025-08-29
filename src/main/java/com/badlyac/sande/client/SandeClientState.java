package com.badlyac.sande.client;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class SandeClientState {
    public static boolean ACTIVE = false;

    // 進/退場計時
    public static final int ENTRY_TICKS_MAX = 10;  // 進場約 0.5s
    public static final int EXIT_TICKS_MAX  = 8;   // 退場約 0.4s
    public static int entryTicks = 0;
    public static int exitTicks  = 0;
    public static int fpSwitchCooldown = 0; // 切到第一人稱後，前幾個 tick 更嚴格處理
    private static boolean lastFirstPerson = false;

    // 濾鏡強度（0~1，WorldTintOverlay 會讀）
    public static float tintStrength = 0f;

    // === 你的殘影資料（保留你先前的版本） ===
    private static final int SAMPLE_INTERVAL = 0; // 每0.5s產生一個殘影
    private static final int SAMPLE_LIFETIME = 20; // 殘影存在1秒
    private static final Deque<Sample> TRAIL = new ArrayDeque<>();
    private static int sampleCooldown = 0;

    private SandeClientState() {}

    public static void updateCameraState(boolean firstPerson, Player p, double camX, double camY, double camZ) {
        if (firstPerson != lastFirstPerson) {
            if (firstPerson) {
                // 剛切進第一人稱：開 6tick 冷卻，並清掉「貼臉」樣本
                fpSwitchCooldown = 6;
                // 把距離鏡頭 < 0.8 格的樣本移除，避免瞬間擋住
                TRAIL.removeIf(s -> {
                    double dx = s.x - camX, dy = s.y - camY, dz = s.z - camZ;
                    return (dx*dx + dy*dy + dz*dz) < (0.8 * 0.8);
                });
            } else {
                // 切回第三人稱就關閉冷卻
                fpSwitchCooldown = 0;
            }
            lastFirstPerson = firstPerson;
        }
    }

    public static void setActive(boolean active) {
        if (active) {
            ACTIVE = true;
            exitTicks = 0;
            entryTicks = ENTRY_TICKS_MAX;
            tintStrength = 0f; // 進場從0拉上去
            TRAIL.clear();
            sampleCooldown = 0;
        } else {
            // 結束：啟動退場動畫，保持 ACTIVE = false（渲染端用 exitTicks 判斷繼續顯示）
            ACTIVE = false;
            entryTicks = 0;
            exitTicks = EXIT_TICKS_MAX;
            // 退場起點給一個基礎強度，避免立刻變黑
            tintStrength = Math.max(tintStrength, 0.6f);
            TRAIL.clear(); // 結束就清空殘影
            sampleCooldown = 0;
        }
    }

    public static void tickClient(Player p) {
        // 殘影維護（與先前相同）
        if (!(ACTIVE || exitTicks > 0 || entryTicks > 0)) {
            TRAIL.clear();
            return;
        }

        // 更新殘影壽命
        Iterator<Sample> it = TRAIL.iterator();
        while (it.hasNext()) {
            Sample s = it.next();
            s.life--;
            if (s.life <= 0) it.remove();
        }

        if (!ACTIVE) return; // 結束動畫期間不再新增殘影

        if (sampleCooldown > 0) {
            sampleCooldown--;
            return;
        }
        sampleCooldown = SAMPLE_INTERVAL;

        TRAIL.addFirst(new Sample(
                (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                p.yBodyRot, p.getYHeadRot(), p.getXRot(),
                p.walkAnimation.position(), p.walkAnimation.speed(),
                p.tickCount, SAMPLE_LIFETIME
        ));
    }

    // 讓 CameraShakeHook 每幀呼叫：更新進/退場曲線
    public static void tickEntryExitCurves() {
        // 進場：0 → 0.85 線性/略加速
        if (entryTicks > 0) {
            entryTicks--;
            float t = 1f - (float) entryTicks / ENTRY_TICKS_MAX; // 0→1
            tintStrength = Math.min(0.85f, t * 0.9f);
        }
        // 退場：0.6 → 0 線性/略加速
        if (exitTicks > 0) {
            float f = (float) exitTicks / EXIT_TICKS_MAX; // 1→0（因為先讀後--）
            tintStrength = Math.max(0f, 0.7f * f);
            exitTicks--;
        }
        if (fpSwitchCooldown > 0) fpSwitchCooldown--;
    }

    public static Deque<Sample> samples() { return TRAIL; }

    public static final class Sample {
        public final float x, y, z;
        public final float bodyYaw, headYaw, headPitch;
        public final float limbSwing, limbSwingAmount;
        public final float ageTicks;
        public int life;
        public Sample(float x, float y, float z,
                      float bodyYaw, float headYaw, float headPitch,
                      float limbSwing, float limbSwingAmount,
                      float ageTicks, int life) {
            this.x=x; this.y=y; this.z=z;
            this.bodyYaw=bodyYaw; this.headYaw=headYaw; this.headPitch=headPitch;
            this.limbSwing=limbSwing; this.limbSwingAmount=limbSwingAmount;
            this.ageTicks=ageTicks; this.life=life;
        }
    }
}
