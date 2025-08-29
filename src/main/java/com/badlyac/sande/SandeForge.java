package com.badlyac.sande;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;

import java.util.*;

@Mod(SandeForge.MODID)
public class SandeForge {
    public static final String MODID = "sande";

    // ---- 可調參數 ----
    private static final double MOVE_ADD = 0.1;     // 追加移速
    private static final double ATKSPD_ADD = 15.0;   // 追加攻速
    private static final int HIT_IFRAMES = 2;        // 受擊無敵時間
    private static final int MAX_ACTIVE_TICKS = 10 * 20; // ✅ 最長啟動 10 秒

    // 固定 UUID
    private static final UUID MOVE_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ATKS_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // 當前啟用 Sande 的玩家
    private static final Set<UUID> ACTIVE = new HashSet<>();
    // ✅ 每位玩家本次啟動剩餘時間（倒數到 0 自動關）
    private static final Map<UUID, Integer> ACTIVE_REMAIN = new HashMap<>();

    public SandeForge() {
        var modBus   = FMLJavaModLoadingContext.get().getModEventBus(); // get() is deprecated since version 1.21.1 and marked for removal
        var forgeBus = MinecraftForge.EVENT_BUS;

        forgeBus.register(this);
        com.badlyac.sande.net.Network.init();
        com.badlyac.sande.init.ModSounds.SOUNDS.register(modBus);
        try { com.badlyac.sande.init.ModItems.ITEMS.register(modBus); } catch (Throwable ignore) {}
    }

    /* ======================================================================
     * 切換（由 C2S 封包或其他入口呼叫）
     * ====================================================================== */
    public static void toggle(ServerPlayer p) {
        if (p == null) return;
        // 二次確認：必須穿著特製胸甲
        if (!com.badlyac.sande.item.SandevistanArmorItem.isWornBy(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("§7需要穿上 §bSandevistan §7胸甲"), true);
            return;
        }
        UUID id = p.getUUID();
        if (ACTIVE.contains(id)) endSandeImmediate(p);
        else startSandeImmediate(p);
    }

    private static void startSandeImmediate(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!ACTIVE.add(id)) return;

        // 開始 10 秒倒數
        ACTIVE_REMAIN.put(id, MAX_ACTIVE_TICKS);

        // 音效 & 粒子
        playStartSfx(p);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.5, p.getZ(), 40, 0.6, 1.0, 0.6, 0.01);
        }

        // 屬性
        applyAttr(p, Attributes.MOVEMENT_SPEED, MOVE_UUID, "sande_move_add", MOVE_ADD, AttributeModifier.Operation.ADDITION);
        applyAttr(p, Attributes.ATTACK_SPEED,  ATKS_UUID, "sande_atks_add",  ATKSPD_ADD, AttributeModifier.Operation.ADDITION);
        // 凍結泡泡
        FreezeManager.setActive(id, true);

        // 客端特效
        com.badlyac.sande.net.Network.sendTo(p, new com.badlyac.sande.net.S2CSetActive(true));
    }

    private static void endSandeImmediate(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!ACTIVE.remove(id)) return;

        ACTIVE_REMAIN.remove(id);

        // 關閉凍結泡泡
        FreezeManager.setActive(id, false);

        // 移除屬性
        removeAttr(p, Attributes.MOVEMENT_SPEED, MOVE_UUID);
        removeAttr(p, Attributes.ATTACK_SPEED,   ATKS_UUID);

        // 客端關特效 + 音效
        com.badlyac.sande.net.Network.sendTo(p, new com.badlyac.sande.net.S2CSetActive(false));
        playEndSfx(p);
    }

    // ✅ 當玩家離線/不存在時的保險關閉
    private static void forceEndById(UUID id) {
        ACTIVE.remove(id);
        ACTIVE_REMAIN.remove(id);
        FreezeManager.setActive(id, false);
        // 玩家不在就不播音效/移屬性；等他回來狀態已清
    }

    /* ======================================================================
     * 伺服器 Tick：倒數自動關（無冷卻）
     * ====================================================================== */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (ACTIVE_REMAIN.isEmpty()) return;

        List<UUID> toEnd = new ArrayList<>();
        for (var it = ACTIVE_REMAIN.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            UUID id = en.getKey();
            int remain = en.getValue() - 1;
            if (remain <= 0) {
                it.remove();
                toEnd.add(id);
            } else {
                en.setValue(remain);
            }
        }

        for (UUID id : toEnd) {
            ServerPlayer p = getPlayerByUUID(id);
            if (p != null) endSandeImmediate(p);
            else forceEndById(id);
        }
    }

    /* ======================================================================
     * 事件：降低 i-frames（只有在攻擊者為啟用狀態時）
     * ====================================================================== */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof Player attacker)) return;
        if (!ACTIVE.contains(attacker.getUUID())) return;

        LivingEntity victim = e.getEntity();
        int iframes = Math.max(0, HIT_IFRAMES);
        victim.hurtTime = Math.min(iframes, victim.hurtTime);
        victim.hurtDuration = Math.min(iframes, victim.hurtDuration);
    }

    /* ======================================================================
     * 輔助：屬性・玩家查找・音效
     * ====================================================================== */
    private static void applyAttr(LivingEntity e, Attribute attr, UUID uuid, String name, double value, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuid);
        inst.addTransientModifier(new AttributeModifier(uuid, name, value, op));
    }

    private static void removeAttr(LivingEntity e, Attribute attr, UUID uuid) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(uuid);
    }

    private static ServerPlayer getPlayerByUUID(UUID id) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return null;
        return srv.getPlayerList().getPlayer(id);
    }

    private static void playStartSfx(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(),
                com.badlyac.sande.init.ModSounds.SANDE_START.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.5f, 1.0f);
        p.playNotifySound(com.badlyac.sande.init.ModSounds.SANDE_START.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.5f, 1.0f);
    }

    private static void playEndSfx(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(),
                com.badlyac.sande.init.ModSounds.SANDE_END.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.5f, 1.0f);
        p.playNotifySound(com.badlyac.sande.init.ModSounds.SANDE_END.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.5f, 1.0f);
    }
}
