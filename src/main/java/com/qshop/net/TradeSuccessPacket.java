package com.qshop.net;

import com.qshop.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: informs the local player that a shop trade completed. */
public class TradeSuccessPacket {

    public static void encode(TradeSuccessPacket packet, FriendlyByteBuf buf) {
    }

    public static TradeSuccessPacket decode(FriendlyByteBuf buf) {
        return new TradeSuccessPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isClient()) {
            c.enqueueWork(ClientPacketHandler::tradeSuccess);
        }
        c.setPacketHandled(true);
    }
}
