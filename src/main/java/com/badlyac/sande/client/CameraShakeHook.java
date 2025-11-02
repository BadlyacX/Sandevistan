package com.badlyac.sande.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.Mth;
import java.util.Random;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CameraShakeHook {
    private static final Random RNG = new Random();

    private static final float ENTRY_MAX = 6f;
    private static final float EXIT_MAX  = 6f;

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        float strength = 0f;

        if (SandeClientState.entryTicks > 0f) {
            float t = SandeClientState.entryTicks / ENTRY_MAX; // 1 → 0
            strength += (t * t) * 2.0f;
        }
        if (SandeClientState.exitTicks > 0f) {
            float t = SandeClientState.exitTicks / EXIT_MAX; // 1 → 0
            strength += t * 1.6f;
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
        double add = 0.0;
        if (SandeClientState.entryTicks > 0f) {
            double t = SandeClientState.entryTicks / ENTRY_MAX;
            add += 6.0 * t;
        }
        if (SandeClientState.exitTicks > 0f) {
            double t = SandeClientState.exitTicks / EXIT_MAX;
            add += 4.0 * t;
        }
        if (add != 0.0) e.setFOV(e.getFOV() + add);
    }
}
