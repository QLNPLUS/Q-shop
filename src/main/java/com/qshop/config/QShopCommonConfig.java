package com.qshop.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * QShop common configuration (config/qshop-common.toml).
 */
public final class QShopCommonConfig {

    public static final ForgeConfigSpec SPEC;
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

    private static boolean validRule(Object value) {
        return value instanceof String && splitRule((String) value) != null;
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
