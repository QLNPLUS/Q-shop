package com.qshop.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 文本工具:解析 Minecraft 传统 § 颜色代码(&#167;X)为带样式的 Component。
 * 支持颜色 0-9/a-f、k(混淆)/l(加粗)/m(删除线)/n(下划线)/o(斜体)/r(重置)。
 * 未含 § 代码时原样返回,不影响现有文本。
 * <p>注意:不要用 {@code (Component) font.substrByWidth(...)} 强转——裁剪结果可能是
 * 非 Component 的 FormattedText 实现(会 ClassCastException),请用 {@link #clip}。
 */
public final class QText {

    private QText() {
    }

    public static boolean hasCodes(String s) {
        return s != null && s.indexOf('\u00A7') >= 0;
    }

    /** 解析含 § 代码的字符串;无代码时直接返回普通文本 */
    public static Component parse(String s) {
        if (s == null || s.isEmpty()) {
            return Component.literal("");
        }
        if (!hasCodes(s)) {
            return Component.literal(s);
        }
        MutableComponent root = Component.literal("");
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                char code = s.charAt(++i);
                if (buf.length() > 0) {
                    root.append(Component.literal(buf.toString()).withStyle(style));
                    buf.setLength(0);
                }
                style = apply(style, code);
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            root.append(Component.literal(buf.toString()).withStyle(style));
        }
        return root;
    }

    /** 按最大宽度裁剪并解析(保留 § 颜色;宽度按含代码的原始串估算,略微保守) */
    public static Component clip(String s, Font font, int maxWidth) {
        if (s == null || s.isEmpty() || maxWidth <= 0) {
            return Component.literal("");
        }
        if (font.width(s) <= maxWidth) {
            return parse(s);
        }
        return parse(font.plainSubstrByWidth(s, maxWidth));
    }

    /** 按最大宽度裁剪已有组件(标题/翻译等);裁剪时退化为纯文本(丢失样式,但绝不崩溃) */
    public static Component clip(Component c, Font font, int maxWidth) {
        if (c == null || maxWidth <= 0) {
            return Component.literal("");
        }
        if (font.width(c) <= maxWidth) {
            return c;
        }
        return Component.literal(font.plainSubstrByWidth(c.getString(), maxWidth));
    }

    private static Style apply(Style style, char code) {
        ChatFormatting fmt = ChatFormatting.getByCode(code);
        if (fmt == null) {
            return style;
        }
        switch (fmt) {
            case RESET -> {
                return Style.EMPTY;
            }
            case BOLD -> {
                return style.withBold(true);
            }
            case ITALIC -> {
                return style.withItalic(true);
            }
            case UNDERLINE -> {
                return style.withUnderlined(true);
            }
            case STRIKETHROUGH -> {
                return style.withStrikethrough(true);
            }
            case OBFUSCATED -> {
                return style.withObfuscated(true);
            }
            default -> {
                TextColor color = TextColor.fromLegacyFormat(fmt);
                return color == null ? style : style.withColor(color);
            }
        }
    }
}
