package com.badlyac.sande.net;

import com.badlyac.sande.client.SandeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record S2CSetActive(UUID subject, boolean active) {
    public static void encode(S2CSetActive pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.subject);
        buf.writeBoolean(pkt.active);
    }
    public static S2CSetActive decode(FriendlyByteBuf buf) {
        return new S2CSetActive(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(S2CSetActive pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        SandeClientState.setActiveFor(pkt.subject, pkt.active)
                )
        );
        ctx.get().setPacketHandled(true);
    }
}
