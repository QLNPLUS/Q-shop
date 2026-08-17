package com.qshop.shop;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个商店。id 用于玩家/指令/KubeJS 引用,uuid 在文件缺失时自动生成并写回。
 */
public class Shop {

    /** 唯一 id(对应文件名) */
    public String id = "";

    /** 唯一 uuid(文件缺失时自动生成) */
    public UUID uuid = UUID.randomUUID();

    /** 显示名,留空则使用 id */
    public String displayName = "";

    /** 默认展示货币(界面只显示该货币余额),留空则使用第一种货币 */
    public String currency = "";

    /** 商店图标,留空则不显示 */
    public ItemStack icon = ItemStack.EMPTY;

    /**
     * 交易条目列表(兼容旧代码/指令/KubeJS:始终指向第一个子商店的条目)。
     */
    public List<ShopEntry> entries = new ArrayList<>();

    /** 子商店列表(至少一个;第一个子商店的条目 == entries) */
    public final List<ShopTab> tabs = new ArrayList<>();

    /**
     * 数据版本(仅内存):每次 save() 自增。客户端轮询时用它判断内容是否变化,
     * 变化则重新发送 OpenShopPacket,让打开着的商店界面实时同步修改。
     */
    public int dataVersion = 0;

    /** 确保至少有一个子商店;entries 始终别名第一个子商店的条目 */
    public void ensureTabs() {
        if (tabs.isEmpty()) {
            ShopTab t = new ShopTab();
            t.name = displayNameOrId();
            t.icon = ItemStack.EMPTY;
            t.entries.addAll(entries);
            tabs.add(t);
        }
        for (ShopTab t : tabs) {
            t.ensureUuid();
        }
        entries = tabs.get(0).entries;
    }

    /** 指定子商店的条目列表(越界时回退到第一个) */
    public List<ShopEntry> entriesOf(int tab) {
        if (tab >= 0 && tab < tabs.size()) {
            return tabs.get(tab).entries;
        }
        return entries;
    }

    public ShopTab tab(int i) {
        if (i >= 0 && i < tabs.size()) {
            return tabs.get(i);
        }
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public String displayNameOrId() {
        return displayName == null || displayName.isEmpty() ? id : displayName;
    }
}
