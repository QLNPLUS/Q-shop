package com.qshop.util;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * 可本地化显示名工具（客户端与服务端通用）。
 * <p>商店、子商店、货币与交易条目的显示名既可以是普通文本（支持 § 颜色代码），
 * 也可以是 {@code lang:<语言键>}，例如 {@code lang:qshop.currency.coins}。
 * 名称为空时使用调用方给出的默认语言键（按 id 生成）；当前语言缺少该键时，
 * 回退到调用方给出的字面量（通常是 id）。</p>
 */
public final class QLang {

    /** 语言键前缀：以此开头的显示名按翻译键解析 */
    public static final String PREFIX = "lang:";

    private QLang() {
    }

    public static boolean isKey(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    /** 去掉 lang: 前缀；普通文本原样返回 */
    public static String keyOf(String s) {
        return isKey(s) ? s.substring(PREFIX.length()) : s;
    }

    /**
     * 显示名 → Component。
     * <p>使用 {@code translatableWithFallback}：客户端按自己的语言解析，
     * 键缺失时显示字面量，因此服务端不需要持有客户端的语言文件。</p>
     *
     * @param raw          配置里的原始显示名（可为空或 lang: 键）
     * @param fallbackKey  raw 为空时使用的默认语言键（可为 null）
     * @param fallbackText 语言键缺失时显示的字面量（可为 null）
     */
    public static Component name(String raw, String fallbackKey, String fallbackText) {
        if (raw != null && !raw.isEmpty() && !isKey(raw)) {
            return Component.literal(raw);
        }
        String key = isKey(raw) ? keyOf(raw) : fallbackKey;
        if (key == null || key.isEmpty()) {
            return Component.literal(fallbackText == null ? "" : fallbackText);
        }
        String fallback = fallbackText == null || fallbackText.isEmpty() ? key : fallbackText;
        return Component.translatableWithFallback(key, fallback);
    }

    /**
     * 显示名 → 当前语言的纯文本（用于拼接消息、FTB 任务进度等）。
     * 参数含义同 {@link #name(String, String, String)}。
     */
    public static String text(String raw, String fallbackKey, String fallbackText) {
        if (raw != null && !raw.isEmpty() && !isKey(raw)) {
            return raw;
        }
        String key = isKey(raw) ? keyOf(raw) : fallbackKey;
        if (key == null || key.isEmpty()) {
            return fallbackText == null ? "" : fallbackText;
        }
        String fallback = fallbackText == null || fallbackText.isEmpty() ? key : fallbackText;
        return Language.getInstance().getOrDefault(key, fallback);
    }
}
