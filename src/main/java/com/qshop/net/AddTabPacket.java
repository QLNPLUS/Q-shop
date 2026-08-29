package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;


/**
 * 客户端 → 服务端:编辑模式下添加子商店(tab)。
 */
public class AddTabPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AddTabPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "add_tab"));
    public static final StreamCodec<FriendlyByteBuf, AddTabPacket> STREAM_CODEC =
            CustomPacketPayload.codec(AddTabPacket::encode, AddTabPacket::decode);

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
                shop.ensureTabs();
                ShopTab t = new ShopTab();
                t.uuid = java.util.UUID.randomUUID().toString();
                t.name = name == null || name.isBlank() ? "Tab " + (shop.tabs.size() + 1) : name.trim();
                shop.tabs.add(t);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<AddTabPacket> type() {
        return TYPE;
    }

}
