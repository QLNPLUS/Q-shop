package com.qshop.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

/**
 * 使用 QShop 材质的小图标按钮(关闭/布局切换/加号/减号/垃圾桶)。
 */
public class QIconButton extends AbstractButton {

    private final ShopTextures.Icon icon;
    private final Runnable action;

    public QIconButton(int x, int y, ShopTextures.Icon icon, Runnable action) {
        super(x, y, 16, 16, Component.literal(""));
        this.icon = icon;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.icon(g, getX() + 2, getY() + 2, icon, isHovered());
    }

    /** 交互区域 = 图标材质非透明像素 */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return ShopTextures.iconHit(icon, getX() + 2, getY() + 2, mouseX, mouseY);
    }
}
