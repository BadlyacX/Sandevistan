package com.badlyac.sande.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.Mth;
import java.util.Random;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CameraShakeHook {
    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        // 進/退場都會抖
        float strength = 0f;

        if (SandeClientState.entryTicks > 0) {
            float t = (float) SandeClientState.entryTicks / SandeClientState.ENTRY_TICKS_MAX; // 1→0
            strength += (t * t) * 2.0f; // 進場：先強後弱
        }
        if (SandeClientState.exitTicks > 0) {
            float t = (float) SandeClientState.exitTicks / SandeClientState.EXIT_TICKS_MAX; // 1→0
            strength += (t) * 1.6f; // 退場：一開始也強，快速衰減
        }

        if (strength <= 0f) return;

        float yawJ   = (RNG.nextFloat() - 0.5f) * 1.2f * strength;
        float pitchJ = (RNG.nextFloat() - 0.5f) * 0.9f * strength;
        float rollJ  = (RNG.nextFloat() - 0.5f) * 1.5f * strength;

        e.setYaw(e.getYaw() + yawJ);
        e.setPitch(Mth.clamp(e.getPitch() + pitchJ, -90f, 90f));
        e.setRoll(e.getRoll() + rollJ);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        float add = 0f;
        if (SandeClientState.entryTicks > 0) {
            float t = (float) SandeClientState.entryTicks / SandeClientState.ENTRY_TICKS_MAX;
            add += 6.0 * t;
        }
        if (SandeClientState.exitTicks > 0) {
            float t = (float) SandeClientState.exitTicks / SandeClientState.EXIT_TICKS_MAX;
            add += 4.0 * t;
        }
        if (add != 0) e.setFOV(e.getFOV() + add);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean firstPerson = mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
        var cam = mc.gameRenderer.getMainCamera();
        var cp = cam.getPosition();
        // 通知 SandeClientState 是否第一人稱，以及相機座標（用來剔除貼臉樣本）
        com.badlyac.sande.client.SandeClientState.updateCameraState(firstPerson, mc.player, cp.x, cp.y, cp.z);

        // 你原本呼叫的曲線遞減（進/退場）
        com.badlyac.sande.client.SandeClientState.tickEntryExitCurves();
    }
}
