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

import java.util.Deque;
import java.util.UUID;

/**
 * 殘影渲染（多人版）：
 * - 自己第一人稱不畫自己的殘影；其他玩家照常可見。
 * - 不改動玩家本體旋轉；以樣本的動畫數據驅動模型骨架。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrailRenderHook {

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();
        UUID pid = player.getUUID();

        // 自己 + 第一人稱：不畫自己的殘影（避免擋視角）
        if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) return;

        // 該玩家沒啟動 Sande：不畫
        if (!SandeClientState.isActive(pid)) return;

        // 取得該玩家的樣本序列
        Deque<SandeClientState.Sample> samples = SandeClientState.samplesOf(pid);
        if (samples == null || samples.isEmpty()) return;

        PlayerRenderer renderer = (PlayerRenderer) e.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        PoseStack pose = e.getPoseStack();
        MultiBufferSource buffers = e.getMultiBufferSource();
        float pt = e.getPartialTick();

        double camX = player.xOld + (player.getX() - player.xOld) * pt;
        double camY = player.yOld + (player.getY() - player.yOld) * pt;
        double camZ = player.zOld + (player.getZ() - player.zOld) * pt;

        renderTrail(model, player, samples, pose, buffers, camX, camY, camZ, pt);
    }

    /* ===================== 共用渲染邏輯 ===================== */
    private static void renderTrail(PlayerModel<AbstractClientPlayer> model,
                                    AbstractClientPlayer player,
                                    Deque<SandeClientState.Sample> samples,
                                    PoseStack pose,
                                    MultiBufferSource buffers,
                                    double camX, double camY, double camZ,
                                    float pt) {

        var texture = player.getSkinTextureLocation();
        int total = samples.size();
        if (total == 0) return;

        int idx = 0; // 迴圈：最新 → 最舊
        for (SandeClientState.Sample s : samples) {
            // 顏色：藍 → 綠（最新偏綠，越舊越藍）
            float t = (total == 1) ? 1f : (1f - (float) idx / (float) (total - 1));
            float r = 0.2f, g = 0.6f + 0.4f * t, b = 1.0f - 0.6f * t;

            // 透明度：依樣本序衰退
            float alpha = 0.55f * (1.0f - (float) idx / Math.max(1, total)); // 0.55 -> 接近 0
            if (alpha <= 0.03f) { idx++; continue; }

            double sx = s.x() - camX, sy = s.y() - camY, sz = s.z() - camZ;

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - s.bodyYaw()));
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0.0F, -1.501F, 0.0F);

            var vc = buffers.getBuffer(RenderType.entityTranslucent(texture));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            float netHeadYaw = s.headYaw() - s.bodyYaw();
            // ✅ Sample 沒有 headPitch，使用 pitch()
            model.setupAnim(player, s.limbSwing(), s.limbSwingAmount(), s.ageTicks(), netHeadYaw, s.pitch());
            model.renderToBuffer(pose, vc, 0x00F000F0, OverlayTexture.NO_OVERLAY, r, g, b, alpha);

            RenderSystem.disableBlend();
            pose.popPose();

            idx++;
        }
    }
}
