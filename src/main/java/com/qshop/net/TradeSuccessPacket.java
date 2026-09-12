package com.qshop.net;

import com.qshop.QShopMod;
import com.qshop.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: informs the local player that a shop trade completed. */
public class TradeSuccessPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeSuccessPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QShopMod.MODID, "trade_success"));
    public static final StreamCodec<FriendlyByteBuf, TradeSuccessPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TradeSuccessPacket::encode, TradeSuccessPacket::decode);

    public static void encode(TradeSuccessPacket packet, FriendlyByteBuf buf) {
    }

    public static TradeSuccessPacket decode(FriendlyByteBuf buf) {
        return new TradeSuccessPacket();
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(ClientPacketHandler::tradeSuccess);
    }

    @Override
    public CustomPacketPayload.Type<TradeSuccessPacket> type() {
        return TYPE;
    }
}
