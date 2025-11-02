package com.badlyac.sande.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

public class SandevistanArmorItem extends ArmorItem {

    public SandevistanArmorItem(ArmorMaterial material, ArmorItem.Type type, Properties props) {
        super(material, type, props);
    }

    public static boolean isWornBy(Player p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.isEmpty() && chest.getItem() instanceof SandevistanArmorItem;
    }
}
