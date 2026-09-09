package com.qshop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Base screen that scales only QShop content around the screen center. */
public abstract class QShopScreen extends Screen {

    private int tooltipMouseX;
    private int tooltipMouseY;
    private ItemStack pendingTooltipStack;
    private List<Component> pendingTooltipLines;

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
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the QShop background at its normal size; only QShop content uses the local matrix.
        ShopTextures.background(graphics, this.width, this.height);

        tooltipMouseX = mouseX;
        tooltipMouseY = mouseY;

        double scale = QShopScreenInput.renderScale();
        int logicalMouseX = QShopScreenInput.toLogicalCoordinate(mouseX, width);
        int logicalMouseY = QShopScreenInput.toLogicalCoordinate(mouseY, height);
        graphics.pose().pushMatrix();
        pendingTooltipStack = null;
        pendingTooltipLines = null;
        graphics.pose().translate(width / 2.0F, height / 2.0F);
        graphics.pose().scale((float) scale);
        graphics.pose().translate(-width / 2.0F, -height / 2.0F);
        try {
            renderContent(graphics, logicalMouseX, logicalMouseY, partialTick);
        } finally {
            graphics.pose().popMatrix();
        }
        if (pendingTooltipStack != null) {
            graphics.setTooltipForNextFrame(this.font, pendingTooltipStack, tooltipMouseX, tooltipMouseY);
        } else if (pendingTooltipLines != null && !pendingTooltipLines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, pendingTooltipLines, Optional.empty(), tooltipMouseX, tooltipMouseY);
        }
    }

    /**
     * Renders a QShop tooltip in Minecraft's normal GUI coordinate system.
     * QShop content is rendered under a centered local scale, but tooltips must
     * keep their native size and use the physical mouse position so Minecraft's
     * edge-clamping logic can place them inside the window.
     */
    protected final void renderQShopTooltip(GuiGraphicsExtractor graphics, ItemStack stack) {
        pendingTooltipLines = null;
        pendingTooltipStack = stack;
    }

    /** Renders a text tooltip without inheriting QShop's local content scale. */
    protected final void renderQShopTooltip(GuiGraphicsExtractor graphics, List<Component> lines) {
        pendingTooltipStack = null;
        pendingTooltipLines = List.copyOf(lines);
    }

    /** Renders QShop components in the local logical coordinate system. */
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return mouseClickedContent(logicalX(event.x()), logicalY(event.y()), event.button());
    }

    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseEvent(mouseX, mouseY, button), false);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double scale = QShopScreenInput.renderScale();
        return mouseDraggedContent(logicalX(event.x()), logicalY(event.y()), event.button(),
                dragX / scale, dragY / scale);
    }

    protected boolean mouseDraggedContent(double mouseX, double mouseY, int button,
                                          double dragX, double dragY) {
        return super.mouseDragged(mouseEvent(mouseX, mouseY, button), dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        return mouseReleasedContent(logicalX(event.x()), logicalY(event.y()), event.button());
    }

    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseEvent(mouseX, mouseY, button));
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (QShopScreenInput.handleScaleWheel(deltaY, getMinecraft().hasShiftDown())) {
            return true;
        }
        return mouseScrolledContent(toLogicalCoordinate(mouseX, width),
                toLogicalCoordinate(mouseY, height), deltaX, deltaY);
    }

    protected boolean mouseScrolledContent(double mouseX, double mouseY,
                                           double deltaX, double deltaY) {
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public final boolean keyPressed(KeyEvent event) {
        return keyPressedContent(event.key(), event.scancode(), event.modifiers());
    }

    protected boolean keyPressedContent(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public final boolean charTyped(CharacterEvent event) {
        return charTypedContent(event.codepoint(), 0);
    }

    protected boolean charTypedContent(int codePoint, int modifiers) {
        return super.charTyped(new CharacterEvent(codePoint));
    }

    protected static MouseButtonEvent mouseEvent(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    protected static KeyEvent keyEvent(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    protected static CharacterEvent characterEvent(int codePoint) {
        return new CharacterEvent(codePoint);
    }

    private double logicalX(double coordinate) {
        return QShopScreenInput.toLogicalCoordinate(coordinate, width);
    }

    private double logicalY(double coordinate) {
        return QShopScreenInput.toLogicalCoordinate(coordinate, height);
    }

    private static double toLogicalCoordinate(double coordinate, int viewportSize) {
        return QShopScreenInput.toLogicalCoordinate(coordinate, viewportSize);
    }
}
