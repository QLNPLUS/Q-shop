package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.trade.TradeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;


/**
 * 客户端 → 服务端:请求一次交易(units = 交易单位数)。
 */
public class TradePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradePacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "trade"));
    public static final StreamCodec<FriendlyByteBuf, TradePacket> STREAM_CODEC =
            CustomPacketPayload.codec(TradePacket::encode, TradePacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    public int entryIndex = 0;
    public int units = 1;

    public TradePacket() {
    }

    public TradePacket(String shopId, int tabIndex, int entryIndex, int units) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
        this.units = units;
    }

    public static void encode(TradePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.entryIndex);
        buf.writeInt(p.units);
    }

    public static TradePacket decode(FriendlyByteBuf buf) {
        TradePacket p = new TradePacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.entryIndex = buf.readInt();
        p.units = buf.readInt();
        return p;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                if (player != null) {
                    TradeService.trade(player, shopId, tabIndex, entryIndex, units);
                }
        });
    }
    @Override
    public CustomPacketPayload.Type<TradePacket> type() {
        return TYPE;
    }

}
