package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下调整交易条目顺序(拖拽排序)。
 */
public class ReorderEntryPacket {

    public String shopId = "";
    public int tabIndex = 0;
    public int fromIndex = 0;
    public int toIndex = 0;

    public ReorderEntryPacket() {
    }

    public ReorderEntryPacket(String shopId, int tabIndex, int fromIndex, int toIndex) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    public static void encode(ReorderEntryPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.fromIndex);
        buf.writeInt(p.toIndex);
    }

    public static ReorderEntryPacket decode(FriendlyByteBuf buf) {
        ReorderEntryPacket p = new ReorderEntryPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.fromIndex = buf.readInt();
        p.toIndex = buf.readInt();
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
                var list = shop.entriesOf(tabIndex);
                if (list.isEmpty()) {
                    return;
                }
                int from = Math.max(0, Math.min(fromIndex, list.size() - 1));
                int to = Math.max(0, Math.min(toIndex, list.size() - 1));
                if (from == to) {
                    return;
                }
                ShopEntry entry = list.remove(from);
                list.add(to, entry);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
