package com.badlyac.sande.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 殘影渲染：
 * - 僅第三人稱：用 RenderPlayerEvent.Post（跟著原版管線）。
 * - 不修改玩家本體旋轉；以樣本動畫驅動模型骨架。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrailRenderHook {

    private static final float SAMPLE_LIFETIME_TICKS = 20f;

    /* ===================== 第三人稱 ===================== */

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        if (!SandeClientState.ACTIVE) return;

        // 🚫 第一人稱：完全不畫殘影
        if (Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON) return;

        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();
        PlayerRenderer renderer = (PlayerRenderer) e.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        PoseStack pose = e.getPoseStack();
        MultiBufferSource buffers = e.getMultiBufferSource();
        float pt = e.getPartialTick();

        double camX = player.xOld + (player.getX() - player.xOld) * pt;
        double camY = player.yOld + (player.getY() - player.yOld) * pt;
        double camZ = player.zOld + (player.getZ() - player.zOld) * pt;

        renderTrail(model, player, pose, buffers, camX, camY, camZ, pt);
    }

    /* ===================== 共用渲染邏輯 ===================== */
    private static void renderTrail(PlayerModel<AbstractClientPlayer> model,
                                    AbstractClientPlayer player,
                                    PoseStack pose,
                                    MultiBufferSource buffers,
                                    double camX, double camY, double camZ,
                                    float pt) {

        var texture = player.getSkinTextureLocation();
        int total = SandeClientState.samples().size();
        if (total == 0) return;

        int idx = 0; // 最新 → 最舊
        for (SandeClientState.Sample s : SandeClientState.samples()) {
            // 顏色：藍→綠
            float t = (total == 1) ? 1f : (1f - (float) idx / (float) (total - 1));
            float r = 0.2f, g = 0.6f + 0.4f * t, b = 1.0f - 0.6f * t;

            // 透明度：隨壽命衰減
            float lifeFrac = clamp01(s.life / SAMPLE_LIFETIME_TICKS);
            float alpha = 0.55f * lifeFrac;

            if (alpha <= 0.03f) {
                idx++;
                continue;
            }

            double sx = s.x - camX, sy = s.y - camY, sz = s.z - camZ;

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - s.bodyYaw));
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0.0F, -1.501F, 0.0F);

            var vc = buffers.getBuffer(RenderType.entityTranslucent(texture));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            float netHeadYaw = s.headYaw - s.bodyYaw;
            model.setupAnim(player, s.limbSwing, s.limbSwingAmount, s.ageTicks, netHeadYaw, s.headPitch);
            model.renderToBuffer(pose, vc, 0x00F000F0, OverlayTexture.NO_OVERLAY, r, g, b, alpha);

            RenderSystem.disableBlend();
            pose.popPose();

            idx++;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
