package com.badlyac.sande;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    private static final double MOVE_ADD = 0.1;     // 追加移速
    private static final double ATKSPD_ADD = 15.0;   // 追加攻速
    private static final int HIT_IFRAMES = 2;        // 受擊無敵時間
    private static final int MAX_ACTIVE_TICKS = 10 * 20; // 最長啟動 10 秒

    private static final UUID MOVE_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ATKS_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Integer> ACTIVE_REMAIN = new HashMap<>();

    public SandeForge() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus(); // get() is deprecated since version 1.21.1 and marked for removal
        var forgeBus = MinecraftForge.EVENT_BUS;

        forgeBus.register(this);
        com.badlyac.sande.net.Network.init();
        com.badlyac.sande.init.ModSounds.SOUNDS.register(modBus);
        try {
            com.badlyac.sande.init.ModItems.ITEMS.register(modBus);
        } catch (Throwable ignore) {
        }
    }

    public static void toggle(ServerPlayer p) {
        if (p == null) return;
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

        ACTIVE_REMAIN.put(id, MAX_ACTIVE_TICKS);

        playStartSfx(p);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.5, p.getZ(), 40, 0.6, 1.0, 0.6, 0.01);
        }

        addEffect(p, MobEffects.JUMP, MAX_ACTIVE_TICKS, 2);
        addEffect(p, MobEffects.DAMAGE_BOOST, MAX_ACTIVE_TICKS, 2);
        applyAttr(p, Attributes.MOVEMENT_SPEED, MOVE_UUID, "sande_move_add", MOVE_ADD, AttributeModifier.Operation.ADDITION);
        applyAttr(p, Attributes.ATTACK_SPEED,  ATKS_UUID, "sande_atks_add",  ATKSPD_ADD, AttributeModifier.Operation.ADDITION);
        FreezeManager.setActive(id, true);

        com.badlyac.sande.net.Network.sendToTrackingAndSelf(p,
                new com.badlyac.sande.net.S2CSetActive(p.getUUID(), true));
    }

    private static void endSandeImmediate(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!ACTIVE.remove(id)) return;

        ACTIVE_REMAIN.remove(id);

        FreezeManager.setActive(id, false);

        removeEffect(p, MobEffects.JUMP);
        removeEffect(p, MobEffects.DAMAGE_BOOST);
        removeAttr(p, Attributes.MOVEMENT_SPEED, MOVE_UUID);
        removeAttr(p, Attributes.ATTACK_SPEED,   ATKS_UUID);

        com.badlyac.sande.net.Network.sendToTrackingAndSelf(p,
                new com.badlyac.sande.net.S2CSetActive(p.getUUID(), false));
        playEndSfx(p);
    }

    private static void forceEndById(UUID id) {
        ACTIVE.remove(id);
        ACTIVE_REMAIN.remove(id);
        FreezeManager.setActive(id, false);
    }

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

        for (java.util.UUID id : new java.util.HashSet<>(ACTIVE)) {
            var p = getPlayerByUUID(id);
            if (p != null) p.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof Player attacker)) return;
        if (!ACTIVE.contains(attacker.getUUID())) return;

        LivingEntity victim = e.getEntity();
        int iframes = Math.max(0, HIT_IFRAMES);
        victim.hurtTime = Math.min(iframes, victim.hurtTime);
        victim.hurtDuration = Math.min(iframes, victim.hurtDuration);
    }

    @SubscribeEvent
    public void onLivingFall(net.minecraftforge.event.entity.living.LivingFallEvent e) {
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player p)) return;
        if (!ACTIVE.contains(p.getUUID())) return;

        e.setDistance(0.0F);
        e.setDamageMultiplier(0.0F);
    }

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

    private static void addEffect(LivingEntity e, net.minecraft.world.effect.MobEffect effect, int dur, int amp)
    {
        e.addEffect(
                new MobEffectInstance(effect, dur, amp,false, false, true)
        );
    }

    private static void removeEffect(LivingEntity e, net.minecraft.world.effect.MobEffect effect) {
        e.removeEffect(effect);
    }

}
