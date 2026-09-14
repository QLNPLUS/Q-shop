package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 使用 QShop 材质的按钮。
 */
public class QButton extends Button {

    /**
     * 文字基准相对"几何居中"的修正量。
     * <p>{@code button.png} 的非透明像素只占 y=1..14（上下各留 1px 透明边），而 MC 默认字体
     * 的行盒是 9px：ASCII 字形实际只占行盒第 2..8 行，中文/全角却几乎占满整行。
     * 于是 {@code (height - 9) / 2} 对中文偏上 —— 在 14px 高的小按钮上只剩上边距 1px、
     * 下边距 2px，肉眼可见。+1 让墨迹块在按钮矩形内真正垂直居中，14px 与 16px 都对齐。
     */
    private static final int TEXT_VISUAL_CENTER_OFFSET = 1;

    public QButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.button(g, getX(), getY(), width, height, isHovered(), active);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 6));
        int color = active ? 0xFFFFFFFF : 0xFF9A9A9A;
        g.centeredText(font, label, getX() + width / 2,
                getY() + textTop(font.lineHeight), color);
    }

    /** 文字绘制用的 y（centeredText 接收的就是文字顶部）。 */
    private int textTop(int lineHeight) {
        return (height - lineHeight) / 2 + TEXT_VISUAL_CENTER_OFFSET;
    }

    /** 交互区域 = 按钮材质非透明像素(修改材质大小即可改变按钮大小) */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return ShopTextures.buttonHit(getX(), getY(), width, height, mouseX, mouseY);
    }
}
