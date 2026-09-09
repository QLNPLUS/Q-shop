package com.qshop.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * QShop 服务端配置(config/qshop-server.toml):
 * <ul>
 *   <li>showTradeMessages:是否显示交易提示消息(购买成功/失败/限购等聊天消息)</li>
 *   <li>tradeMessagesInActionBar:是否改为在物品栏上方(statsMessage/actionbar)区域显示,避免刷屏聊天栏</li>
 *   <li>allowOverflowPurchases:是否允许购买超过背包容量的物品</li>
 * </ul>
 */
public final class QShopServerConfig {

    public static final ModConfigSpec SPEC;
    /** 是否显示交易提示消息(购买成功/失败等) */
    public static final ModConfigSpec.BooleanValue SHOW_TRADE_MESSAGES;
    /** 是否在物品栏上方(statsMessage/actionbar)区域显示交易提示 */
    public static final ModConfigSpec.BooleanValue TRADE_MESSAGES_IN_ACTION_BAR;
    /** 是否允许购买超过背包容量的物品 */
    public static final ModConfigSpec.BooleanValue ALLOW_OVERFLOW_PURCHASES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment(
                "QShop 服务端设置 / QShop server settings",
                "",
                "交易提示消息:购买成功/失败、限购、货币不足、物品不足、背包空间不足等反馈。",
                "Trade feedback messages: purchase success/failure, limit reached, not enough currency/items, no inventory space, etc.")
                .push("messages");
        SHOW_TRADE_MESSAGES = b
                .comment(
                        "是否显示交易提示消息(购买成功或失败时在聊天里弹出的消息)。",
                        "Whether to show trade notification messages (messages popped in chat on purchase success or failure).",
                        "默认 true / Default: true")
                .define("showTradeMessages", true);
        TRADE_MESSAGES_IN_ACTION_BAR = b
                .comment(
                        "是否改为在物品栏上方(statsMessage/actionbar)区域显示交易提示,而不是聊天栏。",
                        "Whether to show trade messages in the statsMessage area (above the hotbar) instead of the chat.",
                        "开启后可防止聊天记录刷屏 / Enabling this prevents chat log spam.",
                        "默认 false / Default: false")
                .define("tradeMessagesInActionBar", false);
        b.pop();
        b.comment(
                "购买设置 / Purchase settings",
                "允许购买数量超过当前背包可容纳数量时,物品会掉落到地面。",
                "When enabled, items that exceed inventory capacity are dropped on the ground.")
                .push("inventory");
        ALLOW_OVERFLOW_PURCHASES = b
                .comment(
                        "是否允许购买超过背包容量的物品。",
                        "Whether to allow purchases that exceed inventory capacity.",
                        "默认 false / Default: false")
                .define("allowOverflowPurchases", false);
        b.pop();
        SPEC = b.build();
    }

    public static boolean showTradeMessages() {
        return SHOW_TRADE_MESSAGES.get();
    }

    public static boolean tradeMessagesInActionBar() {
        return TRADE_MESSAGES_IN_ACTION_BAR.get();
    }

    public static boolean allowOverflowPurchases() {
        return ALLOW_OVERFLOW_PURCHASES.get();
    }

    private QShopServerConfig() {
    }
}
