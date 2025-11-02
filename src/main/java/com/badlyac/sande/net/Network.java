package com.badlyac.sande.net;

import com.badlyac.sande.SandeForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Network {
    private static final String PROTO = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SandeForge.MODID, "main"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.registerMessage(
                id++,
                C2SToggleSande.class,
                C2SToggleSande::encode,
                C2SToggleSande::decode,
                C2SToggleSande::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                id++,
                S2CSetActive.class,
                S2CSetActive::encode,
                S2CSetActive::decode,
                S2CSetActive::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendToTrackingAndSelf(ServerPlayer subject, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> subject), msg);
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.sendTo(msg, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
