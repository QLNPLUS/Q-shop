package com.qshop.shop;

import com.qshop.config.QShopCommonConfig;
import net.minecraft.server.MinecraftServer;

/**
 * 购买/出售限制的重置周期。
 */
public enum LimitReset {
    /** 永不重置 */
    NEVER,
    /** 每日重置 */
    DAILY,
    /** 每 7 个 Minecraft 游戏日重置 */
    WEEKLY,
    /** 每 30 个 Minecraft 游戏日重置 */
    MONTHLY;

    /**
     * 当前周期的键，以主世界时钟和配置的重置时刻计算。
     * 周期变化时，旧的计数会在下一次查询时被视为零。
     */
    public String periodKey(MinecraftServer server) {
        long worldTime = server.overworld().getOverworldClockTime();
        long resetTime = QShopCommonConfig.limitResetWorldTime();
        long worldDay = Math.floorDiv(worldTime - resetTime, 24_000L);
        return switch (this) {
            case DAILY -> "d-" + worldDay;
            case WEEKLY -> "w-" + Math.floorDiv(worldDay, 7L);
            case MONTHLY -> "m-" + Math.floorDiv(worldDay, 30L);
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
