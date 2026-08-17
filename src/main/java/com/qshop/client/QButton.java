package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 使用 QShop 材质的按钮。
 */
public class QButton extends Button {

    public QButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.button(g, getX(), getY(), width, height, isHovered(), active);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 6));
        int color = active ? 0xFFFFFFFF : 0xFF9A9A9A;
        g.drawCenteredString(font, label, getX() + width / 2, getY() + (height - font.lineHeight) / 2, color);
    }

    /** 交互区域 = 按钮材质非透明像素(修改材质大小即可改变按钮大小) */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return ShopTextures.buttonHit(getX(), getY(), width, height, mouseX, mouseY);
    }
}
