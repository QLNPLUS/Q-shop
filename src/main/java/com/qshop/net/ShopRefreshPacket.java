package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:轮询商店数据是否变化(打开中的商店界面实时同步)。
 * 服务端比较 lastVersion 与当前 dataVersion,变化时才重新发送 OpenShopPacket(refresh=true)。
 */
public class ShopRefreshPacket {

    public String shopId = "";
    /** 客户端上次收到的 dataVersion */
    public int lastVersion = 0;

    public ShopRefreshPacket() {
    }

    public ShopRefreshPacket(String shopId, int lastVersion) {
        this.shopId = shopId == null ? "" : shopId;
        this.lastVersion = lastVersion;
    }

    public static void encode(ShopRefreshPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.lastVersion);
    }

    public static ShopRefreshPacket decode(FriendlyByteBuf buf) {
        ShopRefreshPacket p = new ShopRefreshPacket();
        p.shopId = buf.readUtf();
        p.lastVersion = buf.readInt();
        return p;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player == null) {
                    return;
                }
                Shop shop = ShopManager.get(shopId);
                if (shop == null || shop.dataVersion == lastVersion) {
                    return; // 无变化:不响应,节省流量
                }
                ShopManager.openShop(player, shop, true);
            });
        }
        c.setPacketHandled(true);
    }
}
