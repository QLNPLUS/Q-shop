package com.qshop.shop;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个交易条目。
 *
 * <p>BUY/SELL 使用 {@link #item}(含数量与 NBT,如 8 个橡木原木);
 * BARTER 使用 {@link #give}(玩家付出)与 {@link #receive}(玩家获得)。
 *
 * <p>{@link #price} 是每个交易单位的价格(以 {@link #currencyId} 货币计);
 * 对 BARTER 而言是可选的额外货币费用。
 *
 * <p>{@link #globalLimit} / {@link #playerLimit} 按交易单位(购买次数)统计,-1 表示不限,
 * 按 {@link #reset} 周期自动重置。
 */
public class ShopEntry {

    /** 稳定唯一标识(JSON 缺失时自动生成;复制条目时生成新 uuid)。限购计数仍按位置键记录。 */
    public String uuid = "";

    public ShopEntryType type = ShopEntryType.BUY;

    /** 自定义显示名,留空则使用物品名 */
    public String displayName = "";

    /** 自定义描述(悬浮提示中显示),留空则显示物品原本 tooltip */
    public String description = "";

    /** 展示物品(仅作显示用,与实际交易物品无关;留空则显示实际物品) */
    public ItemStack displayItem = ItemStack.EMPTY;

    /** BUY/SELL 的物品(含数量) */
    public ItemStack item = ItemStack.EMPTY;

    /** BARTER:玩家付出的物品 */
    public final List<ItemStack> give = new ArrayList<>();

    /** BARTER:玩家获得的物品 */
    public final List<ItemStack> receive = new ArrayList<>();

    /** 价格货币 id;BARTER 中留空表示无额外货币费用 */
    public String currencyId = "";

    /** 每个交易单位的价格 */
    public double price = 0;

    /** 全服限制(按交易单位/购买次数),-1 不限 */
    public int globalLimit = -1;

    /** 玩家个人限制(按交易单位/购买次数),-1 不限 */
    public int playerLimit = -1;

    /** 限制重置周期 */
    public LimitReset reset = LimitReset.NEVER;

    /** 购买指令列表 */
    public final List<ShopCommand> commands = new ArrayList<>();

    /** 要求完成的 FTB 任务 id(服务端检查;FTB Quests 未安装时忽略) */
    public final List<String> requiredQuests = new ArrayList<>();

    /** 要求的游戏阶段(gamestage / kubejs stages,服务端检查;未安装时忽略) */
    public final List<String> requiredStages = new ArrayList<>();

    /** 阶段显示描述,按 requiredStages 的顺序对应;为空时 Tooltip 回退显示阶段名。 */
    public final List<String> requiredStageDescriptions = new ArrayList<>();

    /** 条件未满足时是否仍在非编辑模式显示该条目(点击后跳转对应 FTB 任务)。 */
    public boolean showWhenRequirementsNotMet = false;

    /**
     * 每个交易单位涉及的物品数量，供 KubeJS 使用 entry.count 读取。
     * BUY/SELL/COMMAND 读取 item 数量，BARTER 读取 receive 总数量。
     */
    public int getCount() {
        if (!item.isEmpty()) {
            return item.getCount();
        }
        int total = 0;
        for (ItemStack stack : receive) {
            total += stack.getCount();
        }
        return total;
    }

    /** 展示用名称:自定义名或展示物品名或实际物品名 */
    public String displayNameOrItem() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (!displayItem.isEmpty()) {
            return displayItem.getHoverName().getString();
        }
        if (!item.isEmpty()) {
            return item.getHoverName().getString();
        }
        if (type == ShopEntryType.COMMAND) {
            return "Command";
        }
        if (!receive.isEmpty()) {
            return receive.get(0).getHoverName().getString();
        }
        if (!give.isEmpty()) {
            return give.get(0).getHoverName().getString();
        }
        return type.name();
    }

    /** 确保有 uuid(为空时生成) */
    public void ensureUuid() {
        if (uuid == null || uuid.isEmpty()) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    /** 深拷贝(右键菜单"复制"用;生成新 uuid,避免与源条目混淆) */
    public ShopEntry copy() {
        ShopEntry c = new ShopEntry();
        c.uuid = java.util.UUID.randomUUID().toString();
        c.type = type;
        c.displayName = displayName;
        c.description = description;
        c.displayItem = displayItem.copy();
        c.item = item.copy();
        for (ItemStack s : give) {
            c.give.add(s.copy());
        }
        for (ItemStack s : receive) {
            c.receive.add(s.copy());
        }
        c.currencyId = currencyId;
        c.price = price;
        c.globalLimit = globalLimit;
        c.playerLimit = playerLimit;
        c.reset = reset;
        for (ShopCommand sc : commands) {
            c.commands.add(new ShopCommand(sc.command, sc.op, sc.silent));
        }
        c.requiredQuests.addAll(requiredQuests);
        c.requiredStages.addAll(requiredStages);
        c.requiredStageDescriptions.addAll(requiredStageDescriptions);
        c.showWhenRequirementsNotMet = showWhenRequirementsNotMet;
        return c;
    }
}
