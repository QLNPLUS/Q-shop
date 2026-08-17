package com.qshop.net;

import com.qshop.trade.TradeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:请求一次交易(units = 交易单位数)。
 */
public class TradePacket {

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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) {
                    TradeService.trade(player, shopId, tabIndex, entryIndex, units);
                }
            });
        }
        c.setPacketHandled(true);
    }
}
