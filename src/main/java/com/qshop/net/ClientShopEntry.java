package com.qshop.net;

import com.qshop.shop.ShopCommand;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 发送给客户端的交易条目副本(含限购用量)。
 */
public class ClientShopEntry {

    /** 条目在服务端 tab 中的真实序号；客户端过滤后仍用它发送操作。 */
    public int serverIndex = -1;
    /** 条目稳定 uuid(复制会生成新 uuid;KubeJS 按 uuid 匹配) */
    public String uuid = "";
    public boolean requirementsMet = true;
    public ShopEntryType type = ShopEntryType.BUY;
    public String displayName = "";
    public String description = "";
    public ItemStack displayItem = ItemStack.EMPTY;
    public ItemStack item = ItemStack.EMPTY;
    public final List<ItemStack> give = new ArrayList<>();
    public final List<ItemStack> receive = new ArrayList<>();
    public String currencyId = "";
    public double price = 0;
    public int globalLimit = -1;
    public int playerLimit = -1;
    public String resetName = "NEVER";
    public final List<ShopCommand> commands = new ArrayList<>();
    public final List<String> requiredQuests = new ArrayList<>();
    public final List<String> requiredStages = new ArrayList<>();
    public final List<String> requiredStageDescriptions = new ArrayList<>();
    public boolean showWhenRequirementsNotMet = false;
    public int usedGlobal = 0;
    public int usedPlayer = 0;

    public static ClientShopEntry from(ShopEntry e, int serverIndex, boolean requirementsMet,
                                       int usedGlobal, int usedPlayer) {
        ClientShopEntry c = new ClientShopEntry();
        c.serverIndex = serverIndex;
        c.uuid = e.uuid == null ? "" : e.uuid;
        c.requirementsMet = requirementsMet;
        c.type = e.type;
        c.displayName = e.displayName;
        c.description = e.description;
        c.displayItem = e.displayItem.copy();
        c.item = e.item.copy();
        for (ItemStack s : e.give) {
            c.give.add(s.copy());
        }
        for (ItemStack s : e.receive) {
            c.receive.add(s.copy());
        }
        c.currencyId = e.currencyId;
        c.price = e.price;
        c.globalLimit = e.globalLimit;
        c.playerLimit = e.playerLimit;
        c.resetName = e.reset.name();
        for (ShopCommand sc : e.commands) {
            c.commands.add(new ShopCommand(sc.command, sc.op, sc.silent));
        }
        c.requiredQuests.addAll(e.requiredQuests);
        c.requiredStages.addAll(e.requiredStages);
        c.requiredStageDescriptions.addAll(e.requiredStageDescriptions);
        c.showWhenRequirementsNotMet = e.showWhenRequirementsNotMet;
        c.usedGlobal = usedGlobal;
        c.usedPlayer = usedPlayer;
        return c;
    }

    public static void write(ClientShopEntry e, FriendlyByteBuf buf) {
        buf.writeInt(e.serverIndex);
        buf.writeUtf(e.uuid == null ? "" : e.uuid);
        buf.writeBoolean(e.requirementsMet);
        buf.writeByte(e.type.ordinal());
        buf.writeUtf(e.displayName == null ? "" : e.displayName);
        buf.writeUtf(e.description == null ? "" : e.description);
        buf.writeItemStack(e.displayItem, true);
        buf.writeItemStack(e.item, true);
        writeStacks(e.give, buf);
        writeStacks(e.receive, buf);
        buf.writeUtf(e.currencyId == null ? "" : e.currencyId);
        buf.writeDouble(e.price);
        buf.writeInt(e.globalLimit);
        buf.writeInt(e.playerLimit);
        buf.writeUtf(e.resetName == null ? "NEVER" : e.resetName);
        buf.writeInt(e.commands.size());
        for (ShopCommand sc : e.commands) {
            buf.writeUtf(sc.command == null ? "" : sc.command);
            buf.writeBoolean(sc.op);
            buf.writeBoolean(sc.silent);
        }
        buf.writeInt(e.requiredQuests.size());
        for (String s : e.requiredQuests) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(e.requiredStages.size());
        for (String s : e.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(e.requiredStageDescriptions.size());
        for (String description : e.requiredStageDescriptions) {
            buf.writeUtf(description == null ? "" : description);
        }
        buf.writeBoolean(e.showWhenRequirementsNotMet);
        buf.writeInt(e.usedGlobal);
        buf.writeInt(e.usedPlayer);
    }

    public static ClientShopEntry read(FriendlyByteBuf buf) {
        ClientShopEntry c = new ClientShopEntry();
        c.serverIndex = buf.readInt();
        c.uuid = buf.readUtf();
        c.requirementsMet = buf.readBoolean();
        c.type = ShopEntryType.values()[buf.readByte() & 0xFF];
        c.displayName = buf.readUtf();
        c.description = buf.readUtf();
        c.displayItem = buf.readItem();
        c.item = buf.readItem();
        readStacks(c.give, buf);
        readStacks(c.receive, buf);
        c.currencyId = buf.readUtf();
        c.price = buf.readDouble();
        c.globalLimit = buf.readInt();
        c.playerLimit = buf.readInt();
        c.resetName = buf.readUtf();
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            c.commands.add(new ShopCommand(buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            c.requiredQuests.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            c.requiredStages.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            c.requiredStageDescriptions.add(buf.readUtf());
        }
        c.showWhenRequirementsNotMet = buf.readBoolean();
        c.usedGlobal = buf.readInt();
        c.usedPlayer = buf.readInt();
        return c;
    }

    private static void writeStacks(List<ItemStack> stacks, FriendlyByteBuf buf) {
        buf.writeInt(stacks.size());
        for (ItemStack s : stacks) {
            buf.writeItemStack(s, true);
        }
    }

    private static void readStacks(List<ItemStack> stacks, FriendlyByteBuf buf) {
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            stacks.add(buf.readItem());
        }
    }
}
