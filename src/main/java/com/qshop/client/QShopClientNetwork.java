package com.qshop.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-only payload dispatch helpers. */
public final class QShopClientNetwork {

    private QShopClientNetwork() {
    }

    public static void sendToServer(CustomPacketPayload message) {
        ClientPacketDistributor.sendToServer(message);
    }
}
