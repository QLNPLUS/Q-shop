package com.qshop.net;

import com.qshop.util.ItemStackData;

import com.qshop.QShopMod;

import com.qshop.currency.CurrencyRegistry;
import com.qshop.shop.LimitReset;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopCommand;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopManager;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端:游戏内编辑交易条目(仅创造模式 op 2 级及以上)。
 * 携带条目全部字段:价格/货币/数量/限购/重置/指令/标题/描述/展示物品/交易物品。
 */
public class EditShopPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditShopPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "edit_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditShopPacket> STREAM_CODEC =
            CustomPacketPayload.codec(EditShopPacket::encode, EditShopPacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    public int entryIndex = 0;
    /** 新类型(允许在编辑界面切换交易形状;与勾选组合一致) */
    public byte typeId = 0;
    public double price = 0;
    public String currency = "";
    public int globalLimit = -1;
    public int playerLimit = -1;
    public String reset = "NEVER";
    public final List<ShopCommand> commands = new ArrayList<>();
    public String displayName = "";
    public String description = "";
    public ItemStack displayItem = ItemStack.EMPTY;
    public ItemStack item = ItemStack.EMPTY;      // BUY/SELL 交易物品;BARTER 获得物
    public ItemStack giveItem = ItemStack.EMPTY;  // BARTER 付出物
    public int itemCount = 1;                     // 交易物品数量
    public String itemNbt = "";                   // 交易物品 NBT(SNBT 文本,空=清除)
    public final List<String> requiredQuests = new ArrayList<>();
    public final List<String> requiredStages = new ArrayList<>();
    public final List<String> requiredStageDescriptions = new ArrayList<>();
    public boolean showWhenRequirementsNotMet = false;

    public EditShopPacket() {
    }

    public EditShopPacket(String shopId, int tabIndex, int entryIndex, byte typeId, double price, String currency,
                          int globalLimit, int playerLimit, String reset, List<ShopCommand> commands,
                          String displayName, String description, ItemStack displayItem,
                          ItemStack item, ItemStack giveItem, int itemCount, String itemNbt,
                          List<String> requiredQuests, List<String> requiredStages,
                          List<String> requiredStageDescriptions,
                          boolean showWhenRequirementsNotMet) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
        this.typeId = typeId;
        this.price = price;
        this.currency = currency;
        this.globalLimit = globalLimit;
        this.playerLimit = playerLimit;
        this.reset = reset;
        this.commands.addAll(commands);
        this.displayName = displayName == null ? "" : displayName;
        this.description = description == null ? "" : description;
        this.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem;
        this.item = item == null ? ItemStack.EMPTY : item;
        this.giveItem = giveItem == null ? ItemStack.EMPTY : giveItem;
        this.itemCount = itemCount;
        this.itemNbt = itemNbt == null ? "" : itemNbt;
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
        if (requiredStageDescriptions != null) {
            for (String stageDescription : requiredStageDescriptions) {
                this.requiredStageDescriptions.add(stageDescription == null ? "" : stageDescription.trim());
            }
        }
    }

    public static void encode(EditShopPacket p, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.entryIndex);
        buf.writeByte(p.typeId);
        buf.writeDouble(p.price);
        buf.writeUtf(p.currency);
        buf.writeInt(p.globalLimit);
        buf.writeInt(p.playerLimit);
        buf.writeUtf(p.reset);
        buf.writeInt(p.commands.size());
        for (ShopCommand sc : p.commands) {
            buf.writeUtf(sc.command == null ? "" : sc.command);
            buf.writeBoolean(sc.op);
            buf.writeBoolean(sc.silent);
        }
        buf.writeUtf(p.displayName == null ? "" : p.displayName);
        buf.writeUtf(p.description == null ? "" : p.description);
        PacketCodecs.writeItem(buf, p.displayItem);
        PacketCodecs.writeItem(buf, p.item);
        PacketCodecs.writeItem(buf, p.giveItem);
        buf.writeInt(p.itemCount);
        buf.writeUtf(p.itemNbt == null ? "" : p.itemNbt);
        buf.writeInt(p.requiredQuests.size());
        for (String s : p.requiredQuests) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(p.requiredStages.size());
        for (String s : p.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(p.requiredStageDescriptions.size());
        for (String stageDescription : p.requiredStageDescriptions) {
            buf.writeUtf(stageDescription == null ? "" : stageDescription);
        }
        buf.writeBoolean(p.showWhenRequirementsNotMet);
    }

    public static EditShopPacket decode(RegistryFriendlyByteBuf buf) {
        EditShopPacket p = new EditShopPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.entryIndex = buf.readInt();
        p.typeId = buf.readByte();
        p.price = buf.readDouble();
        p.currency = buf.readUtf();
        p.globalLimit = buf.readInt();
        p.playerLimit = buf.readInt();
        p.reset = buf.readUtf();
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.commands.add(new ShopCommand(buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        p.displayName = buf.readUtf();
        p.description = buf.readUtf();
        p.displayItem = PacketCodecs.readItem(buf);
        p.item = PacketCodecs.readItem(buf);
        p.giveItem = PacketCodecs.readItem(buf);
        p.itemCount = buf.readInt();
        p.itemNbt = buf.readUtf();
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredQuests.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredStages.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.requiredStageDescriptions.add(buf.readUtf());
        }
        p.showWhenRequirementsNotMet = buf.readBoolean();
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
                ShopEntryType[] types = ShopEntryType.values();
                ShopEntryType entryType = typeId >= 0 && typeId < types.length
                        ? types[typeId] : ShopEntryType.BUY;
                var list = shop.entriesOf(tabIndex);
                if (entryIndex < 0) {
                    ShopEntry e = new ShopEntry();
                    apply(e, entryType);
                    if (!validNewEntry(e)) {
                        return;
                    }
                    e.ensureUuid();
                    list.add(e);
                } else {
                    if (entryIndex >= list.size()) {
                        return;
                    }
                    apply(list.get(entryIndex), entryType);
                }
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }

    private void apply(ShopEntry e, ShopEntryType entryType) {
        e.type = entryType;
        e.price = Math.max(0, price);
        if (currency == null || currency.isEmpty() || CurrencyRegistry.get(currency) != null) {
            e.currencyId = currency == null ? "" : currency;
        }
        e.globalLimit = globalLimit < -1 ? -1 : globalLimit;
        e.playerLimit = playerLimit < -1 ? -1 : playerLimit;
        e.reset = LimitReset.fromName(reset);
        e.commands.clear();
        if (e.type == ShopEntryType.COMMAND) {
            for (ShopCommand sc : commands) {
                if (sc.command != null && !sc.command.isEmpty()) {
                    e.commands.add(sc);
                }
            }
        }
        e.displayName = displayName == null ? "" : displayName;
        e.description = description == null ? "" : description;
        e.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem.copy();
        e.requiredQuests.clear();
        e.requiredQuests.addAll(requiredQuests);
        e.requiredStages.clear();
        e.requiredStages.addAll(requiredStages);
        e.requiredStageDescriptions.clear();
        e.requiredStageDescriptions.addAll(requiredStageDescriptions);
        e.showWhenRequirementsNotMet = showWhenRequirementsNotMet;
        if (e.type == ShopEntryType.BARTER) {
            e.item = ItemStack.EMPTY;
            e.give.clear();
            e.receive.clear();
            if (item != null && !item.isEmpty()) {
                e.receive.add(item.copy());
            }
            if (giveItem != null && !giveItem.isEmpty()) {
                e.give.add(giveItem.copy());
            }
            if (!e.receive.isEmpty()) {
                applyItemDetails(e.receive.get(0), itemCount, itemNbt);
            }
        } else {
            e.give.clear();
            e.receive.clear();
            e.item = item == null ? ItemStack.EMPTY : item.copy();
            if (!e.item.isEmpty()) {
                applyItemDetails(e.item, itemCount, itemNbt);
            }
        }
    }

    private static boolean validNewEntry(ShopEntry e) {
        return e.type == ShopEntryType.COMMAND
                || (e.type == ShopEntryType.BARTER ? !e.receive.isEmpty() : !e.item.isEmpty());
    }

    /** 应用交易物品的数量与 NBT(SNBT 文本;空文本清除 NBT) */
    private static void applyItemDetails(ItemStack stack, int count, String nbtText) {
        stack.setCount(Mth.clamp(count, 1, 1000));
        if (nbtText != null && !nbtText.isBlank()) {
            try {
                ItemStackData.setCustomTag(stack, TagParser.parseTag(nbtText.trim()));
            } catch (Exception ex) {
                // NBT 解析失败则保留原 NBT
            }
        } else {
            ItemStackData.setCustomTag(stack, null);
        }
    }
    @Override
    public CustomPacketPayload.Type<EditShopPacket> type() {
        return TYPE;
    }

}
