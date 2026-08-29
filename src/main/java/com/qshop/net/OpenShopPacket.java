package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.client.ClientPacketHandler;
import com.qshop.currency.Currency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端 → 客户端:打开商店界面(含条目、余额、货币、限购用量、编辑权限)。
 */
public class OpenShopPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenShopPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "open_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenShopPacket::encode, OpenShopPacket::decode);

    public String shopId = "";
    public String shopName = "";
    public String shopCurrency = "";
    public ItemStack icon = ItemStack.EMPTY;
    /** 当前子商店的条目(客户端切换 tab 时重新指向) */
    public List<ClientShopEntry> entries = new ArrayList<>();
    /** 全部子商店(tab) */
    public final List<ClientTab> tabs = new ArrayList<>();
    /** 当前激活的子商店序号(仅客户端使用,不序列化) */
    public int activeTab = 0;
    public final Map<String, Double> balances = new HashMap<>();
    public final List<Currency> currencies = new ArrayList<>();
    public boolean editing = false;
    /** 商店数据版本(服务端主动推送时用于标记内容版本) */
    public int dataVersion = 0;
    /** true = 服务端刷新包(玩家不在该商店界面时客户端应忽略) */
    public boolean refresh = false;

    public OpenShopPacket() {
    }

    public OpenShopPacket(String shopId, String shopName, String shopCurrency, ItemStack icon, List<ClientTab> tabs,
                          Map<String, Double> balances, List<Currency> currencies, boolean editing) {
        this(shopId, shopName, shopCurrency, icon, tabs, balances, currencies, editing, 0, false);
    }

    public OpenShopPacket(String shopId, String shopName, String shopCurrency, ItemStack icon, List<ClientTab> tabs,
                          Map<String, Double> balances, List<Currency> currencies, boolean editing,
                          int dataVersion, boolean refresh) {
        this.shopId = shopId;
        this.shopName = shopName;
        this.shopCurrency = shopCurrency;
        this.icon = icon;
        this.tabs.addAll(tabs);
        if (!this.tabs.isEmpty()) {
            this.entries = this.tabs.get(0).entries;
        }
        this.balances.putAll(balances);
        this.currencies.addAll(currencies);
        this.editing = editing;
        this.dataVersion = dataVersion;
        this.refresh = refresh;
    }

    public static void encode(OpenShopPacket p, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeUtf(p.shopName);
        buf.writeUtf(p.shopCurrency);
        PacketCodecs.writeItem(buf, p.icon);
        buf.writeInt(p.tabs.size());
        for (ClientTab t : p.tabs) {
            ClientTab.write(t, buf);
        }
        buf.writeInt(p.balances.size());
        for (Map.Entry<String, Double> kv : p.balances.entrySet()) {
            buf.writeUtf(kv.getKey());
            buf.writeDouble(kv.getValue());
        }
        buf.writeInt(p.currencies.size());
        for (Currency c : p.currencies) {
            buf.writeUtf(c.id);
            buf.writeUtf(c.displayName);
            buf.writeInt(c.color);
        }
        buf.writeBoolean(p.editing);
        buf.writeInt(p.dataVersion);
        buf.writeBoolean(p.refresh);
    }

    public static OpenShopPacket decode(RegistryFriendlyByteBuf buf) {
        OpenShopPacket p = new OpenShopPacket();
        p.shopId = buf.readUtf();
        p.shopName = buf.readUtf();
        p.shopCurrency = buf.readUtf();
        p.icon = PacketCodecs.readItem(buf);
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.tabs.add(ClientTab.read(buf));
        }
        if (!p.tabs.isEmpty()) {
            p.entries = p.tabs.get(0).entries;
        }
        int m = buf.readInt();
        for (int i = 0; i < m; i++) {
            p.balances.put(buf.readUtf(), buf.readDouble());
        }
        int k = buf.readInt();
        for (int i = 0; i < k; i++) {
            p.currencies.add(new Currency(buf.readUtf(), buf.readUtf(), buf.readInt()));
        }
        p.editing = buf.readBoolean();
        p.dataVersion = buf.readInt();
        p.refresh = buf.readBoolean();
        return p;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openShop(this));
    }
    @Override
    public CustomPacketPayload.Type<OpenShopPacket> type() {
        return TYPE;
    }

}
