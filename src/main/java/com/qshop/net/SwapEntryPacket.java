package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;


/**
 * 客户端 → 服务端:编辑模式下拖拽排序(交换两个条目的位置)。
 */
public class SwapEntryPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SwapEntryPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "swap_entry"));
    public static final StreamCodec<FriendlyByteBuf, SwapEntryPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SwapEntryPacket::encode, SwapEntryPacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    public int fromIndex = 0;
    public int toIndex = 0;

    public SwapEntryPacket() {
    }

    public SwapEntryPacket(String shopId, int tabIndex, int fromIndex, int toIndex) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    public static void encode(SwapEntryPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.fromIndex);
        buf.writeInt(p.toIndex);
    }

    public static SwapEntryPacket decode(FriendlyByteBuf buf) {
        SwapEntryPacket p = new SwapEntryPacket();
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
                int a = Math.max(0, Math.min(fromIndex, list.size() - 1));
                int b = Math.max(0, Math.min(toIndex, list.size() - 1));
                if (a == b) {
                    return;
                }
                var tmp = list.get(a);
                list.set(a, list.get(b));
                list.set(b, tmp);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<SwapEntryPacket> type() {
        return TYPE;
    }

}
