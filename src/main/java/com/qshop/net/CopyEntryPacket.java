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
 * 客户端 → 服务端:编辑模式下右键菜单"复制"交易条目(插入到原条目之后)。
 */
public class CopyEntryPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CopyEntryPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "copy_entry"));
    public static final StreamCodec<FriendlyByteBuf, CopyEntryPacket> STREAM_CODEC =
            CustomPacketPayload.codec(CopyEntryPacket::encode, CopyEntryPacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    public int entryIndex = 0;

    public CopyEntryPacket() {
    }

    public CopyEntryPacket(String shopId, int tabIndex, int entryIndex) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
    }

    public static void encode(CopyEntryPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.entryIndex);
    }

    public static CopyEntryPacket decode(FriendlyByteBuf buf) {
        CopyEntryPacket p = new CopyEntryPacket();
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
                ShopEntry copy = list.get(entryIndex).copy();
                list.add(Math.min(entryIndex + 1, list.size()), copy);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<CopyEntryPacket> type() {
        return TYPE;
    }

}
