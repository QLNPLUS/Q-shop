package com.qshop.shop;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个子商店(主界面左侧竖向 tab)。
 * 每个子商店有自己的名字、图标(类似交易条目的展示物品)和交易条目列表。
 * <p>{@link #requiredQuests} / {@link #requiredStages} 未满足时,非编辑玩家默认看不到该子商店;
 * {@link #showWhenRequirementsNotMet} 可将其保留为锁定状态。
 */
public class ShopTab {

    /** 稳定唯一标识(JSON 缺失时自动生成) */
    public String uuid = "";

    /** 子商店名称(留空则用商店名) */
    public String name = "";

    /** 子商店图标(留空不显示) */
    public ItemStack icon = ItemStack.EMPTY;

    /** 子商店描述(悬停在 tab 上时以 tooltip 显示;多行用 \n 分隔) */
    public String description = "";

    /** 该子商店的交易条目 */
    public final List<ShopEntry> entries = new ArrayList<>();

    /** 要求完成的 FTB 任务 id(服务端检查;FTB Quests 未安装时忽略) */
    public final List<String> requiredQuests = new ArrayList<>();

    /** 要求的 KubeJS stage(服务端检查;未安装时按不满足处理) */
    public final List<String> requiredStages = new ArrayList<>();

    /** 阶段要求的显示描述,按 requiredStages 索引对应,缺失时回退到阶段 id。 */
    public final List<String> requiredStageDescriptions = new ArrayList<>();

    /** 条件未满足时是否仍在非编辑模式显示该子商店(使用锁定材质)。 */
    public boolean showWhenRequirementsNotMet = false;

    public void ensureUuid() {
        if (uuid == null || uuid.isEmpty()) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    public String displayName() {
        return name == null || name.isEmpty() ? "Tab" : name;
    }
}
