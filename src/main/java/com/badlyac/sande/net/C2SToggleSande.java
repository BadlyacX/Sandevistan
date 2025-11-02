package com.badlyac.sande.net;

import com.badlyac.sande.SandeForge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SToggleSande() {
    public static void encode(C2SToggleSande pkt, FriendlyByteBuf buf) {}
    public static C2SToggleSande decode(FriendlyByteBuf buf) { return new C2SToggleSande(); }

    public static void handle(C2SToggleSande pkt, Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        var sender = c.getSender();
        if (sender != null) {
            sender.getServer().execute(() -> SandeForge.toggle(sender));
        }
        c.setPacketHandled(true);
    }
}
