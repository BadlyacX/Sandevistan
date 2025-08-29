package com.badlyac.sande.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PostChainHook {

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        if (SandeClientState.ACTIVE) {
            if (!SandePostFX.isEnabled()) SandePostFX.enable();
            SandePostFX.onRenderTick(e.getPartialTick());
        } else {
            if (SandePostFX.isEnabled()) SandePostFX.disable();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        SandePostFX.disable();
        SandeClientState.setActive(false);
    }
}
