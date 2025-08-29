package com.badlyac.sande.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

public final class SandePostFX {
    private static PostChain chain;
    private static float time = 0f;
    private static final ResourceLocation PIPELINE =
            ResourceLocation.fromNamespaceAndPath("sande", "shaders/post/sande.json");

    private SandePostFX() {}

    public static void enable() {
        if (chain != null) return;
        try {
            var mc = Minecraft.getInstance();
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), PIPELINE);
            chain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            time = 0f;
        } catch (Exception ignored) {
            chain = null;
        }
    }

    public static void disable() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        time = 0f;
    }

    public static void onRenderTick(float partialTick) {
        if (chain == null) return;
        time += partialTick;
        try {
            // 不再設定自訂 uniforms，純處理管線
            chain.process(partialTick);
        } catch (Exception ignored) {}
    }


    public static void onResize(int w, int h) {
        if (chain != null) chain.resize(w, h);
    }

    public static boolean isEnabled() { return chain != null; }
}
