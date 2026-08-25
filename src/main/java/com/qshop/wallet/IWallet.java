package com.qshop.wallet;

import java.util.Map;

/**
 * 玩家钱包:多种非物品货币余额 + 个人购买限制计数。
 * 通过 Forge Capability 持久化在玩家数据中。
 */
public interface IWallet {

    double getBalance(String currencyId);

    void setBalance(String currencyId, double amount);

    /** 加余额,返回新余额 */
    double add(String currencyId, double amount);

    /** 扣余额,余额不足时返回 false 且不扣除 */
    boolean take(String currencyId, double amount);

    boolean has(String currencyId, double amount);

    /** 全部货币余额快照 */
    Map<String, Double> snapshot();

    // ---- 个人限购(按交易单位/购买次数计数,按 "shopId|entryUuid" 为键,按周期键区分) ----

    int getLimitCount(String key, String period);

    void addLimitCount(String key, int amount, String period);

    /** 删除指定键的全部个人限购计数(含所有周期) */
    void clearLimitCount(String key);

    /** 从旧实例复制(死亡重生/换维度时保留数据) */
    void copyFrom(IWallet other);
}
