package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下删除子商店(tab),至少保留一个。
 */
public class RemoveTabPacket {

    public String shopId = "";
    public int tabIndex = 0;

    public RemoveTabPacket() {
    }

    public RemoveTabPacket(String shopId, int tabIndex) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
    }

    public static void encode(RemoveTabPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
    }

    public static RemoveTabPacket decode(FriendlyByteBuf buf) {
        RemoveTabPacket p = new RemoveTabPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        return p;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player == null || !player.hasPermissions(2) || !player.isCreative()) {
                    return;
                }
                Shop shop = ShopManager.get(shopId);
                if (shop == null) {
                    return;
                }
                if (shop.tabs.size() <= 1) {
                    return; // 至少保留一个子商店
                }
                if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
                    return;
                }
                shop.tabs.remove(tabIndex);
                shop.ensureTabs();
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
