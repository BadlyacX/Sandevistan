package com.badlyac.sande.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldTintOverlay {
    private static final float TINT_R = 0.05f;
    private static final float TINT_G = 1.00f;
    private static final float TINT_B = 0.15f;

    @SubscribeEvent
    public static void onGuiOverlayPre(RenderGuiOverlayEvent.Pre e) {
        if (e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        boolean show = SandeClientState.LOCAL_ACTIVE
                || SandeClientState.entryTicks > 0f
                || SandeClientState.exitTicks  > 0f;

        if (!show) return;

        float alpha = Math.max(0f, Math.min(1f, SandeClientState.tintStrength));
        if (alpha <= 0.01f) return;


        var gg = e.getGuiGraphics();
        int w = gg.guiWidth();
        int h = gg.guiHeight();

        int a = Math.round(alpha * 255f);
        int r = Math.round(TINT_R * 255f);
        int g = Math.round(TINT_G * 255f);
        int b = Math.round(TINT_B * 255f);
        int argb = (a << 24) | (r << 16) | (g << 8) | b;

        gg.fill(0, 0, w, h, argb);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
