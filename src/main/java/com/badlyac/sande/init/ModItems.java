package com.badlyac.sande.init;

import com.badlyac.sande.SandeForge;
import com.badlyac.sande.item.SandevistanArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SandeForge.MODID);

    public static final RegistryObject<Item> SANDEVISTAN =
            ITEMS.register("sandevistan", () ->
                    new SandevistanArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent e) {
        e.accept(SANDEVISTAN.get());
    }

    private ModItems() {}
}
