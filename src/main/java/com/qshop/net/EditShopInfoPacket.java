package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


/**
 * 客户端 → 服务端:编辑模式下修改商店信息(显示名 / 图标 / 默认货币)。
 */
public class EditShopInfoPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditShopInfoPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "edit_shop_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditShopInfoPacket> STREAM_CODEC =
            CustomPacketPayload.codec(EditShopInfoPacket::encode, EditShopInfoPacket::decode);

    public String shopId = "";
    public String displayName = "";
    public ItemStack icon = ItemStack.EMPTY;
    /** 默认货币 id(空表示由界面回退到第一种货币) */
    public String currency = "";

    public EditShopInfoPacket() {
    }

    public EditShopInfoPacket(String shopId, String displayName, ItemStack icon, String currency) {
        this.shopId = shopId;
        this.displayName = displayName == null ? "" : displayName;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.currency = currency == null ? "" : currency;
    }

    public static void encode(EditShopInfoPacket p, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeUtf(p.displayName);
        PacketCodecs.writeItem(buf, p.icon);
        buf.writeUtf(p.currency == null ? "" : p.currency);
    }

    public static EditShopInfoPacket decode(RegistryFriendlyByteBuf buf) {
        EditShopInfoPacket p = new EditShopInfoPacket();
        p.shopId = buf.readUtf();
        p.displayName = buf.readUtf();
        p.icon = PacketCodecs.readItem(buf);
        p.currency = buf.readUtf();
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
                shop.displayName = displayName == null ? "" : displayName;
                shop.icon = icon == null ? ItemStack.EMPTY : icon.copy();
                shop.currency = currency == null ? "" : currency.trim();
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<EditShopInfoPacket> type() {
        return TYPE;
    }

}
