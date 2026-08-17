package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 使用 QShop 材质的勾选框。
 */
public class QCheckbox extends AbstractButton {

    private boolean selected;
    private final Consumer<Boolean> onChanged;

    public QCheckbox(int x, int y, Component message, boolean selected, Consumer<Boolean> onChanged) {
        super(x, y, 0, 14, message);
        this.selected = selected;
        this.onChanged = onChanged;
        this.width = 16 + Minecraft.getInstance().font.width(message);
    }

    public boolean selected() {
        return selected;
    }

    @Override
    public void onPress() {
        selected = !selected;
        if (onChanged != null) {
            onChanged.accept(selected);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.checkbox(g, getX(), getY() + (height - 12) / 2, selected, isHovered());
        var font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), getX() + 16, getY() + (height - font.lineHeight) / 2, 0xFFFFFF);
    }

    /** 交互区域 = 勾选框材质非透明像素(+ 文字标签) */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseX >= getX() + 16 && mouseX <= getX() + 16 + Minecraft.getInstance().font.width(getMessage())
                && mouseY >= getY() && mouseY <= getY() + height) {
            return true;
        }
        return ShopTextures.checkboxHit(selected, getX(), getY() + (height - 12) / 2, mouseX, mouseY);
    }
}
