package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下修改商店信息(显示名 / 图标 / 默认货币)。
 */
public class EditShopInfoPacket {

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

    public static void encode(EditShopInfoPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeUtf(p.displayName);
        buf.writeItemStack(p.icon, true);
        buf.writeUtf(p.currency == null ? "" : p.currency);
    }

    public static EditShopInfoPacket decode(FriendlyByteBuf buf) {
        EditShopInfoPacket p = new EditShopInfoPacket();
        p.shopId = buf.readUtf();
        p.displayName = buf.readUtf();
        p.icon = buf.readItem();
        p.currency = buf.readUtf();
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
                shop.displayName = displayName == null ? "" : displayName;
                shop.icon = icon == null ? ItemStack.EMPTY : icon.copy();
                shop.currency = currency == null ? "" : currency.trim();
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
