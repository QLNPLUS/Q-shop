package com.qshop.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * QShop 客户端配置(config/qshop-client.toml) / QShop client config:
 * <ul>
 *   <li>showFadeMasks:是否显示子商店列表上下半透明渐隐遮罩(默认 true) / whether to show the translucent fade masks at the top/bottom of the tab list</li>
 *   <li>fadeColor:遮罩颜色(十六进制 RRGGBB,默认 636363) / mask color (hex RRGGBB, default 636363)</li>
 * </ul>
 */
public final class QShopClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_FADE_MASKS;
    public static final ForgeConfigSpec.ConfigValue<String> FADE_COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment(
                "QShop 客户端设置 / QShop client settings",
                "",
                "子商店列表的渐隐遮罩(列表滚动时内容淡出)。",
                "Fade masks on the shop tab list (content fades out while scrolling).")
                .push("mask");
        SHOW_FADE_MASKS = b.comment(
                        "是否显示子商店列表上下半透明渐隐遮罩。",
                        "Whether to show the translucent fade masks at the top/bottom of the tab list.",
                        "默认 true / Default: true")
                .define("showFadeMasks", true);
        FADE_COLOR = b.comment(
                        "遮罩颜色(十六进制 RRGGBB,如 636363)。",
                        "Mask color (hex RRGGBB, e.g. 636363).",
                        "默认 636363 / Default: 636363")
                .define("fadeColor", "636363");
        b.pop();
        SPEC = b.build();
    }

    /** 解析遮罩颜色;非法值回退默认 0x636363 */
    public static int fadeColor() {
        try {
            return Integer.parseInt(FADE_COLOR.get().trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0x636363;
        }
    }

    public static boolean showFadeMasks() {
        return SHOW_FADE_MASKS.get();
    }

    private QShopClientConfig() {
    }
}
