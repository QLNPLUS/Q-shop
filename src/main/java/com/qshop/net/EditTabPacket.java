package com.qshop.net;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端:编辑模式下修改子商店(tab)的名字、描述、图标与任务/阶段要求。
 */
public class EditTabPacket {

    public String shopId = "";
    public int tabIndex = 0;
    public String name = "";
    public String description = "";
    public ItemStack icon = ItemStack.EMPTY;
    public final List<String> requiredQuests = new ArrayList<>();
    public final List<String> requiredStages = new ArrayList<>();

    public EditTabPacket() {
    }

    public EditTabPacket(String shopId, int tabIndex, String name, String description, ItemStack icon,
                         List<String> requiredQuests, List<String> requiredStages) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        if (requiredQuests != null) {
            for (String q : requiredQuests) {
                if (q != null && !q.isBlank()) {
                    this.requiredQuests.add(q.trim());
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

    public static void encode(EditTabPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeUtf(p.name);
        buf.writeUtf(p.description);
        buf.writeItemStack(p.icon, true);
        buf.writeInt(p.requiredQuests.size());
        for (String q : p.requiredQuests) {
            buf.writeUtf(q == null ? "" : q);
        }
        buf.writeInt(p.requiredStages.size());
        for (String s : p.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
    }

    public static EditTabPacket decode(FriendlyByteBuf buf) {
        EditTabPacket p = new EditTabPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.name = buf.readUtf();
        p.description = buf.readUtf();
        p.icon = buf.readItem();
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredQuests.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredStages.add(buf.readUtf());
        }
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
                var t = shop.tab(tabIndex);
                if (t == null) {
                    return;
                }
                t.name = name == null ? "" : name;
                t.description = description == null ? "" : description;
                t.icon = icon == null ? ItemStack.EMPTY : icon.copy();
                t.requiredQuests.clear();
                t.requiredQuests.addAll(requiredQuests);
                t.requiredStages.clear();
                t.requiredStages.addAll(requiredStages);
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
            });
        }
        c.setPacketHandled(true);
    }
}
