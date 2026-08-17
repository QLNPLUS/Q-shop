package com.qshop.shop;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * 购买/出售限制的重置周期。
 */
public enum LimitReset {
    /** 永不重置 */
    NEVER,
    /** 每日重置 */
    DAILY,
    /** 每周重置(ISO 周) */
    WEEKLY,
    /** 每月重置 */
    MONTHLY;

    /**
     * 当前周期的键。周期变化时,旧的计数会被自动清零。
     */
    public String periodKey() {
        LocalDate now = LocalDate.now();
        return switch (this) {
            case DAILY -> "d-" + now;
            case WEEKLY -> "w-" + now.getYear() + "-" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHLY -> "m-" + now.getYear() + "-" + now.getMonthValue();
            case NEVER -> "all";
        };
    }

    public static LimitReset fromName(String name) {
        if (name == null) return NEVER;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (Exception e) {
            return NEVER;
        }
    }
}
