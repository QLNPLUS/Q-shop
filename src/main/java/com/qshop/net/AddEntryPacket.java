package com.qshop.net;

import com.qshop.currency.CurrencyRegistry;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下添加交易条目(物品由客户端物品选择器提供)。
 */
public class AddEntryPacket {

    public String shopId = "";
    public int tabIndex = 0;
    public byte typeId = 0;
    public double price = 0;
    public String currency = "";
    public String command = "";
    public ItemStack item = ItemStack.EMPTY;      // BUY/SELL 交易物品;BARTER 获得物
    public ItemStack giveItem = ItemStack.EMPTY;  // BARTER 付出物
    public ItemStack displayItem = ItemStack.EMPTY; // 展示物品(可选)
    public final List<String> requiredQuests = new ArrayList<>();
    public final List<String> requiredStages = new ArrayList<>();
    public boolean showWhenRequirementsNotMet = false;

    public AddEntryPacket() {
    }

    public AddEntryPacket(String shopId, int tabIndex, byte typeId, double price, String currency,
                          String command, ItemStack item, ItemStack giveItem, ItemStack displayItem,
                          List<String> requiredQuests, List<String> requiredStages,
                          boolean showWhenRequirementsNotMet) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.typeId = typeId;
        this.price = price;
        this.currency = currency == null ? "" : currency;
        this.command = command == null ? "" : command;
        this.item = item == null ? ItemStack.EMPTY : item;
        this.giveItem = giveItem == null ? ItemStack.EMPTY : giveItem;
        this.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem;
        this.showWhenRequirementsNotMet = showWhenRequirementsNotMet;
        if (requiredQuests != null) {
            for (String s : requiredQuests) {
                if (s != null && !s.isBlank()) {
                    this.requiredQuests.add(s.trim());
                }
            }
        }
        if (requiredStages != null) {
            for (String s : requiredStages) {
                if (s != null && !s.isBlank()) {
                    this.requiredStages.add(s.trim());
                }
            }
        }
    }

    public static void encode(AddEntryPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeByte(p.typeId);
        buf.writeDouble(p.price);
        buf.writeUtf(p.currency);
        buf.writeUtf(p.command == null ? "" : p.command);
        buf.writeItemStack(p.item, true);
        buf.writeItemStack(p.giveItem, true);
        buf.writeItemStack(p.displayItem, true);
        buf.writeInt(p.requiredQuests.size());
        for (String s : p.requiredQuests) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(p.requiredStages.size());
        for (String s : p.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeBoolean(p.showWhenRequirementsNotMet);
    }

    public static AddEntryPacket decode(FriendlyByteBuf buf) {
        AddEntryPacket p = new AddEntryPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.typeId = buf.readByte();
        p.price = buf.readDouble();
        p.currency = buf.readUtf();
        p.command = buf.readUtf();
        p.item = buf.readItem();
        p.giveItem = buf.readItem();
        p.displayItem = buf.readItem();
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredQuests.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredStages.add(buf.readUtf());
        }
        p.showWhenRequirementsNotMet = buf.readBoolean();
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
                ShopEntryType[] types = ShopEntryType.values();
                ShopEntryType entryType = typeId >= 0 && typeId < types.length ? types[typeId] : ShopEntryType.BUY;
                String cur = currency == null || currency.isEmpty() || CurrencyRegistry.get(currency) != null ? currency : "";
                ShopManager.addEntryToTab(shop, tabIndex, entryType, item, giveItem, displayItem, price, cur, command,
                        requiredQuests, requiredStages, showWhenRequirementsNotMet);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
