package com.qshop.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * 使用 QShop 材质的滑块(1..max 整数取值)。
 */
public class QSlider extends AbstractSliderButton {

    private final Runnable onChanged;

    public QSlider(int x, int y, int w, int h, Runnable onChanged) {
        super(x, y, w, h, Component.literal(""), 0);
        this.onChanged = onChanged;
    }

    @Override
    protected void updateMessage() {
    }

    @Override
    protected void applyValue() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    public void setValueInt(int value, int max) {
        this.value = max <= 0 ? 0 : Math.max(0.0, Math.min(1.0, (double) value / max));
    }

    public int getValueInt(int max) {
        return (int) Math.round(this.value * max);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.track(g, getX(), getY() + (height - 8) / 2, width);
        int knobX = getX() + (int) (this.value * (width - 8));
        ShopTextures.knob(g, knobX, getY() + (height - 14) / 2, isHovered());
    }
}
