package com.badlyac.sande;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FreezeManager {
    private static final double RADIUS = 10.0;
    private static final int SLOW_LVL = 255;

    // 正在被凍結中的實體 → 保存的原狀態
    private static final Map<Integer, FrozenState> FROZEN = new HashMap<>();
    // 引用計數：同時在多個泡泡內的實體，需要全部移出才解凍
    private static final Map<Integer, Integer> REF = new HashMap<>();

    // 由高速狀態啟停時呼叫 setActive 維護
    public static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    public static void setActive(UUID id, boolean active) {
        if (active) ACTIVE_PLAYERS.add(id);
        else ACTIVE_PLAYERS.remove(id);
    }

    private static boolean isActivePlayer(Entity e) {
        return (e instanceof Player pl) && ACTIVE_PLAYERS.contains(pl.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;

        Set<Integer> touched = new HashSet<>();

        // 逐一處理每位處於高速狀態的玩家，建立其凍結半徑
        for (UUID pid : ACTIVE_PLAYERS) {
            Player p = getPlayer(pid);
            if (p == null) continue;
            Level lvl = p.level();

            AABB box = new AABB(
                    p.getX() - RADIUS, p.getY() - RADIUS, p.getZ() - RADIUS,
                    p.getX() + RADIUS, p.getY() + RADIUS, p.getZ() + RADIUS
            );

            List<Entity> list = lvl.getEntities(p, box, ent -> ent.isAlive() && ent.getId() != p.getId());
            for (Entity ent : list) {
                // 高速玩家互相豁免，不凍結
                if (isActivePlayer(ent)) continue;

                int id = ent.getId();
                touched.add(id);

                REF.put(id, REF.getOrDefault(id, 0) + 1);

                if (!FROZEN.containsKey(id)) {
                    FrozenState st = FrozenState.capture(ent);
                    FROZEN.put(id, st);
                    applyFreeze(ent);
                } else {
                    maintainFrozen(ent);
                }
            }
        }

        // 清理：本 tick 未被任何泡泡觸及者引用-1；為 0 則解凍
        List<Integer> toUnfreeze = new ArrayList<>();
        for (int id : new ArrayList<>(FROZEN.keySet())) {
            Entity ent = findEntityById(id);

            // 若此實體變成高速玩家，立即解凍
            if (ent != null && isActivePlayer(ent)) {
                toUnfreeze.add(id);
                continue;
            }

            if (!touched.contains(id)) {
                int c = REF.getOrDefault(id, 0) - 1;
                if (c <= 0) {
                    toUnfreeze.add(id);
                    REF.remove(id);
                } else {
                    REF.put(id, c);
                }
            }
        }

        // 立刻解凍（同一個 tick 恢復 AI/導航/感知）
        for (int id : toUnfreeze) {
            Entity ent = findEntityById(id);
            FrozenState st = FROZEN.remove(id);
            if (ent != null && st != null) st.restore(ent);
        }
    }

    /* -------------------- 冻結/維護/解凍 -------------------- */

    // 首次進泡泡：關控制旗標、停導航、禁重力、清速度
    private static void applyFreeze(Entity e) {
        if (e instanceof Player pl) {
            pl.setSprinting(false);
            pl.setDeltaMovement(Vec3.ZERO);
            // 玩家用極速緩速效果實現「停住」；短時間重貼，避免殘留長時效
            pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, SLOW_LVL, false, false, true));
        } else if (e instanceof Mob mob) {
            // 關閉 AI 控制旗標（立即生效，避免 setNoAi 帶來的延遲）
            mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
            mob.goalSelector.disableControlFlag(Goal.Flag.JUMP);
            mob.targetSelector.disableControlFlag(Goal.Flag.TARGET);

            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setNoGravity(true);
        } else if (e instanceof Projectile proj) {
            proj.setDeltaMovement(Vec3.ZERO);
            proj.setNoGravity(true);
        } else {
            e.setDeltaMovement(Vec3.ZERO);
        }
    }

    // 每 tick 維持：清速度，對 Mob 停導航，刷新感知（避免偶爾「掙扎」）
    private static void maintainFrozen(Entity e) {
        if (e instanceof Player pl) {
            pl.setDeltaMovement(Vec3.ZERO);
            pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, SLOW_LVL, false, false, true));
        } else if (e instanceof Mob mob) {
            mob.setDeltaMovement(Vec3.ZERO);
            mob.getNavigation().stop();
        } else if (e instanceof Projectile proj) {
            proj.setDeltaMovement(Vec3.ZERO);
        }
        // 若需要暫停 TNT fuse，可於此回補： e.g. if (e instanceof PrimedTnt t) t.setFuse(t.getFuse() + 1);
    }

    /* -------------------- 查找工具 -------------------- */

    private static Player getPlayer(UUID id) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        return srv == null ? null : srv.getPlayerList().getPlayer(id);
    }

    private static Entity findEntityById(int id) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return null;
        for (Level lvl : srv.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /* -------------------- 狀態保存/恢復 -------------------- */

    private static final class FrozenState {
        final boolean hadGravity;
        final boolean playerWasSprinting;
        final Vec3 prevVelocity;

        FrozenState(boolean hadGravity, boolean playerWasSprinting, Vec3 prevVelocity) {
            this.hadGravity = hadGravity;
            this.playerWasSprinting = playerWasSprinting;
            this.prevVelocity = prevVelocity;
        }

        static FrozenState capture(Entity e) {
            boolean g;
            if (e instanceof LivingEntity le) g = !le.isNoGravity();
            else g = !e.isNoGravity();
            boolean wasSprint = (e instanceof Player pl) && pl.isSprinting();
            Vec3 v = e.getDeltaMovement();
            return new FrozenState(g, wasSprint, v);
        }

        // 離開泡泡：同一 tick 立刻恢復控制旗標/導航/感知/重力/速度
        void restore(Entity e) {
            if (e instanceof Mob mob) {
                // 恢復控制旗標
                mob.goalSelector.enableControlFlag(Goal.Flag.MOVE);
                mob.goalSelector.enableControlFlag(Goal.Flag.LOOK);
                mob.goalSelector.enableControlFlag(Goal.Flag.JUMP);
                mob.targetSelector.enableControlFlag(Goal.Flag.TARGET);

                // 立即刷新 AI 系統
                mob.getNavigation().stop();
                mob.getNavigation().recomputePath();
                mob.getSensing().tick();
                // 可選：對有 Brain 的生物，強制刷新活動
                mob.getBrain().updateActivityFromSchedule(mob.level().getDayTime(), mob.level().getGameTime());

                mob.setNoGravity(!hadGravity);
            } else if (e instanceof LivingEntity le) {
                le.setNoGravity(!hadGravity);
            } else {
                e.setNoGravity(!hadGravity);
            }

            // 恢復玩家衝刺狀態
            if (e instanceof Player pl) {
                pl.setSprinting(playerWasSprinting);
            }

            // 恢復速度（可改成 Vec3.ZERO 如果你不想保留原速度）
            e.setDeltaMovement(prevVelocity);
        }
    }
}
