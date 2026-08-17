package com.qshop.shop;

/**
 * 交易条目类型。
 * BUY     - 玩家用货币从商店购买物品
 * SELL    - 玩家把物品卖给商店换取货币
 * BARTER  - 以物换物(可附加货币费用)
 * COMMAND - 指令购买:支付货币后执行指令(无物品交换)
 */
public enum ShopEntryType {
    BUY,
    SELL,
    BARTER,
    COMMAND;

    public static ShopEntryType fromName(String name) {
        if (name == null) return BUY;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (Exception e) {
            return BUY;
        }
    }
}
