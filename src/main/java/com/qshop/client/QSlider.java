package com.qshop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * 使用 QShop 材质的滑块(1..max 整数取值)。
 */
public class QSlider extends AbstractSliderButton {

    private static final int KNOB_WIDTH = 8;
    private static final int KNOB_HEIGHT = 14;

    private final Runnable onChanged;
    private boolean handleDragging;

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

    /** 仅把手本体可交互；轨道空白区域点击不会跳转数值。 */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int knobX = getX() + (int) (this.value * (width - KNOB_WIDTH));
        int knobY = getY() + (height - KNOB_HEIGHT) / 2;
        return mouseX >= knobX && mouseX < knobX + KNOB_WIDTH
                && mouseY >= knobY && mouseY < knobY + KNOB_HEIGHT;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            handleDragging = false;
            return false;
        }
        handleDragging = super.mouseClicked(event, doubleClick);
        return handleDragging;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (!handleDragging || event.button() != 0) {
            return false;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean wasDragging = handleDragging;
        handleDragging = false;
        if (!wasDragging) {
            return false;
        }
        super.mouseReleased(event);
        return true;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.track(g, getX(), getY() + (height - 8) / 2, width);
        int knobX = getX() + (int) (this.value * (width - KNOB_WIDTH));
        ShopTextures.knob(g, knobX, getY() + (height - KNOB_HEIGHT) / 2, isMouseOver(mouseX, mouseY));
    }
}
