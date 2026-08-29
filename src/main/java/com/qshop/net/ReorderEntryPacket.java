package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;


/**
 * 客户端 → 服务端:编辑模式下调整交易条目顺序(拖拽排序)。
 */
public class ReorderEntryPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReorderEntryPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "reorder_entry"));
    public static final StreamCodec<FriendlyByteBuf, ReorderEntryPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ReorderEntryPacket::encode, ReorderEntryPacket::decode);

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

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
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
    @Override
    public CustomPacketPayload.Type<ReorderEntryPacket> type() {
        return TYPE;
    }

}
