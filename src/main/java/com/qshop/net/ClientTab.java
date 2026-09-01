package com.qshop.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 发送给客户端的子商店(tab)副本:名称、图标、条目列表。
 */
public class ClientTab {

    /** 子商店在服务端的真实序号(客户端过滤隐藏子商店后仍用它发送操作) */
    public int serverIndex = -1;
    /** 当前玩家是否满足子商店的任务与阶段要求 */
    public boolean requirementsMet = true;
    /** 条件未满足时是否仍显示该子商店 */
    public boolean showWhenRequirementsNotMet = false;
    /** 子商店稳定 uuid(KubeJS 按 uuid 匹配) */
    public String uuid = "";
    public String name = "";
    /** 子商店描述(悬停在 tab 上时显示) */
    public String description = "";
    public ItemStack icon = ItemStack.EMPTY;
    public final List<String> requiredQuests = new ArrayList<>();
    public final List<String> requiredStages = new ArrayList<>();
    public final List<String> requiredStageDescriptions = new ArrayList<>();
    public final List<ClientShopEntry> entries = new ArrayList<>();

    public static void write(ClientTab t, RegistryFriendlyByteBuf buf) {
        buf.writeInt(t.serverIndex);
        buf.writeBoolean(t.requirementsMet);
        buf.writeBoolean(t.showWhenRequirementsNotMet);
        buf.writeUtf(t.uuid == null ? "" : t.uuid);
        buf.writeUtf(t.name == null ? "" : t.name);
        buf.writeUtf(t.description == null ? "" : t.description);
        PacketCodecs.writeItem(buf, t.icon);
        buf.writeInt(t.requiredQuests.size());
        for (String q : t.requiredQuests) {
            buf.writeUtf(q == null ? "" : q);
        }
        buf.writeInt(t.requiredStages.size());
        for (String s : t.requiredStages) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeInt(t.requiredStageDescriptions.size());
        for (String description : t.requiredStageDescriptions) {
            buf.writeUtf(description == null ? "" : description);
        }
        buf.writeInt(t.entries.size());
        for (ClientShopEntry e : t.entries) {
            ClientShopEntry.write(e, buf);
        }
    }

    public static ClientTab read(RegistryFriendlyByteBuf buf) {
        ClientTab t = new ClientTab();
        t.serverIndex = buf.readInt();
        t.requirementsMet = buf.readBoolean();
        t.showWhenRequirementsNotMet = buf.readBoolean();
        t.uuid = buf.readUtf();
        t.name = buf.readUtf();
        t.description = buf.readUtf();
        t.icon = PacketCodecs.readItem(buf);
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            t.requiredQuests.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            t.requiredStages.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            t.requiredStageDescriptions.add(buf.readUtf());
        }
        n = buf.readInt();
        for (int i = 0; i < n; i++) {
            t.entries.add(ClientShopEntry.read(buf));
        }
        return t;
    }
}
