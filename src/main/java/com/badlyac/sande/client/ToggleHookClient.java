package com.badlyac.sande.client;

import com.badlyac.sande.item.SandevistanArmorItem;
import com.badlyac.sande.net.C2SToggleSande;
import com.badlyac.sande.net.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ToggleHookClient {

    // 簡單去抖：同一遊戲刻只送一次
    private static long lastToggleTick = -1;

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        tryToggle(e);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        tryToggle(e);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty e) {
        tryToggle(e);
    }

    private static void tryToggle(PlayerInteractEvent e) {
        if (!e.getLevel().isClientSide) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // 必須按著 Ctrl（macOS 會對應 Cmd）
        if (!Screen.hasControlDown()) return;

        // 必須穿著 Sandevistan 胸甲
        if (!SandevistanArmorItem.isWornBy(mc.player)) return;

        // 去抖：同一 tick 不重複送
        long nowTick = mc.level.getGameTime();
        if (nowTick == lastToggleTick) return;
        lastToggleTick = nowTick;

        // 送封包請伺服器切換
        Network.CHANNEL.sendToServer(new C2SToggleSande());

        // 只有「可取消」的事件才取消，避免 RightClickEmpty 崩潰
        if (e.isCancelable()) {
            e.setCanceled(true);
        }
    }
}
