package com.qshop.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * QShop common and client-shared configuration (config/qshop-common.toml).
 */
public final class QShopCommonConfig {

    /** Preferred starting value; the usable range follows the current Minecraft GUI Scale. */
    public static final double DEFAULT_GUI_SCALE = 5.0D;
    public static final double GUI_SCALE_STEP = 0.2D;

    public static final ForgeConfigSpec SPEC;
    /** Whether to show the translucent tab list fade masks. */
    public static final ForgeConfigSpec.BooleanValue SHOW_FADE_MASKS;
    /** Hex RGB color used by the tab list fade masks. */
    public static final ForgeConfigSpec.ConfigValue<String> FADE_COLOR;
    /** Whether the optional client layout debugger is enabled. */
    public static final ForgeConfigSpec.BooleanValue ENABLE_LAYOUT_DEBUG;
    /** Last layout selected by the local client (standard or wide). */
    public static final ForgeConfigSpec.ConfigValue<String> LAST_LAYOUT;
    /** Whether the local client should reopen the shop search box after restart. */
    public static final ForgeConfigSpec.BooleanValue SEARCH_ACTIVE;
    /** Local QShop component scale preference, bounded for the current Minecraft GUI scale and window. */
    public static final ForgeConfigSpec.ConfigValue<Double> GUI_SCALE;
    /** Whether a death applies currency retention rules. */
    public static final ForgeConfigSpec.BooleanValue LOSE_CURRENCY_ON_DEATH;
    /** Retention used for currencies without an explicit rule. */
    public static final ForgeConfigSpec.DoubleValue DEFAULT_CURRENCY_RETENTION;
    /** Per-currency rules in the form currencyId=retention (0..1). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURRENCY_RETENTION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment(
                "QShop 通用设置 / QShop common settings",
                "这些设置在单人和服务端环境中都生效 / These settings apply to client and server environments.")
                .push("death");
        LOSE_CURRENCY_ON_DEATH = b
                .comment(
                        "玩家死亡时是否按下方规则减少货币。关闭时完整保留所有货币。",
                        "Whether death applies the currency retention rules. When false, all currencies are kept.",
                        "默认 false / Default: false")
                .define("loseCurrencyOnDeath", false);
        DEFAULT_CURRENCY_RETENTION = b
                .comment(
                        "未单独配置的货币死亡后保留比例，范围 0.0~1.0。0.2 = 保留 20%。",
                        "Retention for currencies without a rule, from 0.0 to 1.0. 0.2 keeps 20%.",
                        "默认 0.0 / Default: 0.0")
                .defineInRange("defaultCurrencyRetention", 0.0D, 0.0D, 1.0D);
        CURRENCY_RETENTION = b
                .comment(
                        "按货币 id 覆盖保留比例，每项格式为 currencyId=比例，例如 coins=0.2。",
                        "Per-currency retention overrides, one entry per line: currencyId=ratio, e.g. coins=0.2.",
                        "也接受 currencyId:比例 / currencyId:ratio。未列出的货币使用 defaultCurrencyRetention。")
                .defineList("currencyRetention", List.of(), QShopCommonConfig::validRule);
        b.pop();

        b.comment(
                "客户端界面设置 / Client GUI settings",
                "这些选项也保存在 qshop-common.toml，以便客户端和服务端使用同一份配置。")
                .push("client");
        SHOW_FADE_MASKS = b.comment(
                        "是否显示子商店列表上下半透明渐隐遮罩。",
                        "Whether to show the translucent fade masks on the tab list.",
                        "默认 true / Default: true")
                .define("showFadeMasks", true);
        FADE_COLOR = b.comment(
                        "遮罩颜色(十六进制 RRGGBB,如 636363)。",
                        "Fade mask color (hex RRGGBB, e.g. 636363).",
                        "默认 636363 / Default: 636363")
                .define("fadeColor", "636363");
        ENABLE_LAYOUT_DEBUG = b.comment(
                        "启用商店布局调试编辑器(F8)。默认关闭。",
                        "Enable the shop layout debug editor (F8). Default: false.")
                .define("enableLayoutDebug", false);
        LAST_LAYOUT = b.comment(
                        "客户端上次选择的商店布局。可选 standard(7x3) 或 wide(8x4)。",
                        "The last shop layout selected by the client. Use standard(7x3) or wide(8x4).",
                        "默认 standard / Default: standard")
                .define("lastLayout", "standard", QShopCommonConfig::validLayout);
        SEARCH_ACTIVE = b.comment(
                        "是否在商店界面启用搜索框。会在重启游戏后恢复。",
                        "Whether the shop search box is active. The state is restored after restarting the game.",
                        "默认 false / Default: false")
                .define("searchActive", false);
        GUI_SCALE = b.comment(
                        "QShop 界面使用自身缩放值，并随 Minecraft GUI Scale 根据当前窗口动态调整可用范围。",
                        "QShop uses its own local scale value and dynamically adjusts its usable range for the current window and Minecraft GUI Scale.",
                        "默认偏好值 5.0 / Default preference: 5.0")
                .define("guiScale", DEFAULT_GUI_SCALE, QShopCommonConfig::validGuiScale);
        b.pop();
        SPEC = b.build();
    }

    /** Returns the retention ratio for a currency, clamped to 0..1. */
    public static double currencyRetention(String currencyId) {
        if (currencyId != null) {
            for (String rule : CURRENCY_RETENTION.get()) {
                String[] pair = splitRule(rule);
                if (pair != null && currencyId.equals(pair[0])) {
                    return parseRatio(pair[1], DEFAULT_CURRENCY_RETENTION.get());
                }
            }
        }
        return clamp(DEFAULT_CURRENCY_RETENTION.get());
    }

    public static boolean loseCurrencyOnDeath() {
        return LOSE_CURRENCY_ON_DEATH.get();
    }

    /** Parses the client fade color, falling back to 0x636363 for invalid input. */
    public static int fadeColor() {
        try {
            return Integer.parseInt(FADE_COLOR.get().trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0x636363;
        }
    }

    public static boolean showFadeMasks() {
        return SHOW_FADE_MASKS.get();
    }

    public static boolean layoutDebugEnabled() {
        return ENABLE_LAYOUT_DEBUG.get();
    }

    public static boolean lastLayoutWide() {
        return "wide".equalsIgnoreCase(LAST_LAYOUT.get());
    }

    /** Persists the local layout choice immediately in qshop-common.toml. */
    public static void setLastLayout(boolean wide) {
        String value = wide ? "wide" : "standard";
        if (!value.equalsIgnoreCase(LAST_LAYOUT.get())) {
            LAST_LAYOUT.set(value);
            SPEC.save();
        }
    }

    public static boolean searchActive() {
        return SEARCH_ACTIVE.get();
    }

    /** Persists the local search button state immediately in qshop-common.toml. */
    public static void setSearchActive(boolean active) {
        if (SEARCH_ACTIVE.get() != active) {
            SEARCH_ACTIVE.set(active);
            SPEC.save();
        }
    }

    public static double guiScale() {
        double value = GUI_SCALE.get();
        if (!Double.isFinite(value) || value <= 0.0D) {
            setGuiScale(DEFAULT_GUI_SCALE);
            return DEFAULT_GUI_SCALE;
        }
        return value;
    }

    /** Persists the QShop-only component scale immediately. */
    public static void setGuiScale(double scale) {
        double value = Double.isFinite(scale) && scale > 0.0D ? scale : DEFAULT_GUI_SCALE;
        if (Math.abs(GUI_SCALE.get() - value) > 0.0001D) {
            GUI_SCALE.set(value);
            SPEC.save();
        }
    }

    private static boolean validRule(Object value) {
        return value instanceof String && splitRule((String) value) != null;
    }

    private static boolean validLayout(Object value) {
        return value instanceof String
                && ("standard".equalsIgnoreCase((String) value) || "wide".equalsIgnoreCase((String) value));
    }

    private static boolean validGuiScale(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double numeric = ((Number) value).doubleValue();
        return Double.isFinite(numeric) && numeric > 0.0D;
    }

    private static String[] splitRule(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        int separator = text.indexOf('=');
        if (separator < 0) {
            separator = text.indexOf(':');
        }
        if (separator <= 0 || separator >= text.length() - 1) {
            return null;
        }
        String id = text.substring(0, separator).trim();
        String ratio = text.substring(separator + 1).trim();
        if (id.startsWith("\"") && id.endsWith("\"")) {
            id = id.substring(1, id.length() - 1).trim();
        }
        if (ratio.endsWith(",")) {
            ratio = ratio.substring(0, ratio.length() - 1).trim();
        }
        if (id.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(ratio);
            return Double.isFinite(parsed) && parsed >= 0.0D && parsed <= 1.0D
                    ? new String[]{id, ratio} : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double parseRatio(String raw, double fallback) {
        try {
            return clamp(Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            return clamp(fallback);
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private QShopCommonConfig() {
    }
}
