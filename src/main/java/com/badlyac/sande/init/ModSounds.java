package com.badlyac.sande.init;

import com.badlyac.sande.SandeForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SandeForge.MODID);

    // 事件 ID 必須對應 sounds.json 的 key
    public static final RegistryObject<SoundEvent> SANDE_START =
            SOUNDS.register("sande_start",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SandeForge.MODID, "sande_start")));

    public static final RegistryObject<SoundEvent> SANDE_END =
            SOUNDS.register("sande_end",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SandeForge.MODID, "sande_end")));

    private ModSounds() {}
}
