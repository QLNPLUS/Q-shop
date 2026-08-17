package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下添加子商店(tab)。
 */
public class AddTabPacket {

    public String shopId = "";
    public String name = "";

    public AddTabPacket() {
    }

    public AddTabPacket(String shopId, String name) {
        this.shopId = shopId;
        this.name = name == null ? "" : name;
    }

    public static void encode(AddTabPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeUtf(p.name);
    }

    public static AddTabPacket decode(FriendlyByteBuf buf) {
        AddTabPacket p = new AddTabPacket();
        p.shopId = buf.readUtf();
        p.name = buf.readUtf();
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
                shop.ensureTabs();
                ShopTab t = new ShopTab();
                t.uuid = java.util.UUID.randomUUID().toString();
                t.name = name == null || name.isBlank() ? "Tab " + (shop.tabs.size() + 1) : name.trim();
                shop.tabs.add(t);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
