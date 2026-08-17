package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下上移/下移子商店(tab),改变顺序。
 */
public class MoveTabPacket {

    public String shopId = "";
    public int tabIndex = 0;
    /** -1 = 上移,1 = 下移 */
    public int dir = 0;

    public MoveTabPacket() {
    }

    public MoveTabPacket(String shopId, int tabIndex, int dir) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.dir = dir;
    }

    public static void encode(MoveTabPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.dir);
    }

    public static MoveTabPacket decode(FriendlyByteBuf buf) {
        MoveTabPacket p = new MoveTabPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.dir = buf.readInt();
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
                if (shop == null || shop.tabs.size() <= 1) {
                    return;
                }
                int from = tabIndex;
                int to = from + (dir < 0 ? -1 : 1);
                if (from < 0 || from >= shop.tabs.size() || to < 0 || to >= shop.tabs.size()) {
                    return;
                }
                var t = shop.tabs.remove(from);
                shop.tabs.add(to, t);
                shop.ensureTabs();
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
