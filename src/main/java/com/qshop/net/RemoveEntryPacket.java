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
 * 客户端 → 服务端:编辑模式下删除交易条目。
 */
public class RemoveEntryPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveEntryPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "remove_entry"));
    public static final StreamCodec<FriendlyByteBuf, RemoveEntryPacket> STREAM_CODEC =
            CustomPacketPayload.codec(RemoveEntryPacket::encode, RemoveEntryPacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    public int entryIndex = 0;

    public RemoveEntryPacket() {
    }

    public RemoveEntryPacket(String shopId, int tabIndex, int entryIndex) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
    }

    public static void encode(RemoveEntryPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.entryIndex);
    }

    public static RemoveEntryPacket decode(FriendlyByteBuf buf) {
        RemoveEntryPacket p = new RemoveEntryPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.entryIndex = buf.readInt();
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
                if (entryIndex < 0 || entryIndex >= list.size()) {
                    return;
                }
                list.remove(entryIndex);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<RemoveEntryPacket> type() {
        return TYPE;
    }

}
