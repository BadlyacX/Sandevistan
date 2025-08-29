package com.badlyac.sande.net;

import com.badlyac.sande.client.SandeClientState;
import com.badlyac.sande.client.SandePostFX;
import com.badlyac.sande.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSetActive(boolean active) {
    public static void encode(S2CSetActive pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.active);
    }

    public static S2CSetActive decode(FriendlyByteBuf buf) {
        return new S2CSetActive(buf.readBoolean());
    }

    public static void handle(S2CSetActive pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // 切換客戶端狀態（含進/退場動畫的計時）
                    SandeClientState.setActive(pkt.active());

                    // 後處理：啟用/關閉（若你只用 WorldTintOverlay 也沒關係，開著這段可同時跑像差/模糊）
                    if (pkt.active()) {
                        SandePostFX.enable();
                    } else {
                        SandePostFX.disable();
                    }

                    // 客端本地保底播放音效（避免伺服器廣播因距離/分類聽不到）
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        if (pkt.active()) {
                            mc.player.playSound(ModSounds.SANDE_START.get(), 1.0f, 1.0f);
                        } else {
                            mc.player.playSound(ModSounds.SANDE_END.get(), 1.0f, 1.0f);
                        }
                    }
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
