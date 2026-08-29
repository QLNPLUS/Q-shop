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

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端:编辑模式下修改子商店(tab)的名字、描述、图标与任务/阶段要求。
 */
public class EditTabPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditTabPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "edit_tab"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditTabPacket> STREAM_CODEC =
            CustomPacketPayload.codec(EditTabPacket::encode, EditTabPacket::decode);

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

    public static void encode(EditTabPacket p, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeUtf(p.name);
        buf.writeUtf(p.description);
        PacketCodecs.writeItem(buf, p.icon);
        buf.writeInt(p.requiredQuests.size());
        for (String q : p.requiredQuests) {
            buf.writeUtf(q == null ? "" : q);
        }
        buf.writeInt(p.requiredStages.size());
        for (String s : p.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
    }

    public static EditTabPacket decode(RegistryFriendlyByteBuf buf) {
        EditTabPacket p = new EditTabPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.name = buf.readUtf();
        p.description = buf.readUtf();
        p.icon = PacketCodecs.readItem(buf);
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
    @Override
    public CustomPacketPayload.Type<EditTabPacket> type() {
        return TYPE;
    }

}
