package com.qshop.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base screen that scales only QShop content around the screen center. */
public abstract class QShopScreen extends Screen {

    protected QShopScreen(Component title) {
        super(title);
    }

    /** Logical width occupied by this screen's QShop content before local scaling. */
    protected int qshopContentWidth() {
        return 250;
    }

    /** Logical height occupied by this screen's QShop content before local scaling. */
    protected int qshopContentHeight() {
        return 200;
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep Minecraft's dim/background layer at its normal size.
        renderBackground(graphics);

        double scale = QShopScreenInput.renderScale();
        int logicalMouseX = QShopScreenInput.toLogicalCoordinate(mouseX, width);
        int logicalMouseY = QShopScreenInput.toLogicalCoordinate(mouseY, height);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0D, height / 2.0D, 0.0D);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);
        graphics.pose().translate(-width / 2.0D, -height / 2.0D, 0.0D);
        try {
            renderContent(graphics, logicalMouseX, logicalMouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Renders QShop components in the local logical coordinate system. */
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedContent(toLogicalCoordinate(mouseX, width),
                toLogicalCoordinate(mouseY, height), button);
    }

    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button,
                                      double dragX, double dragY) {
        double scale = QShopScreenInput.renderScale();
        return mouseDraggedContent(toLogicalCoordinate(mouseX, width),
                toLogicalCoordinate(mouseY, height), button, dragX / scale, dragY / scale);
    }

    protected boolean mouseDraggedContent(double mouseX, double mouseY, int button,
                                          double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleasedContent(toLogicalCoordinate(mouseX, width),
                toLogicalCoordinate(mouseY, height), button);
    }

    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (QShopScreenInput.handleScaleWheel(delta, Screen.hasShiftDown())) {
            return true;
        }
        return mouseScrolledContent(toLogicalCoordinate(mouseX, width),
                toLogicalCoordinate(mouseY, height), delta);
    }

    protected boolean mouseScrolledContent(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toLogicalCoordinate(mouseX, width), toLogicalCoordinate(mouseY, height));
    }

    private static double toLogicalCoordinate(double coordinate, int viewportSize) {
        return QShopScreenInput.toLogicalCoordinate(coordinate, viewportSize);
    }
}
