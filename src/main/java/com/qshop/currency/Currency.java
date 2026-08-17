package com.qshop.currency;

/**
 * 一种非物品货币(保存在玩家数据里)。
 */
public class Currency {

    public final String id;
    public final String displayName;
    public final int color;

    public Currency(String id, String displayName, int color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    /** 解析 "#RRGGBB" 颜色,失败返回白色 */
    public static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFFFFFF;
        }
    }
}
