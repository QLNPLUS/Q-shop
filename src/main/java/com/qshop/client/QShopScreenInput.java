package com.qshop.client;

import com.qshop.config.QShopCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Shared keyboard and local coordinate behavior for the QShop screen chain. */
public final class QShopScreenInput {

    private static final double MIN_SCALE_RATIO = 0.6D;
    private static final double SCREEN_MARGIN_LOGICAL_UNITS = 8.0D;

    private QShopScreenInput() {
    }

    /** Returns the persisted scale used by QShop's own rendering matrix. */
    public static double scale() {
        return clampScale(QShopCommonConfig.guiScale());
    }

    /** Returns QShop's local matrix scale in Minecraft's current GUI coordinate system. */
    public static double renderScale() {
        return scale();
    }

    /**
     * Calculates the usable QShop zoom range for the current screen. The upper
     * bound keeps the complete screen-specific layout inside the physical window;
     * the lower bound follows that available size instead of being fixed.
     */
    public static ScaleBounds scaleBounds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof QShopScreen qshopScreen)) {
            double fallback = QShopCommonConfig.DEFAULT_GUI_SCALE;
            return new ScaleBounds(fallback, fallback);
        }

        double nativeGuiScale = minecraft.getWindow().getGuiScale();
        if (!Double.isFinite(nativeGuiScale) || nativeGuiScale <= 0.0D) {
            nativeGuiScale = 1.0D;
        }

        // Use GUI logical units for the local matrix. Native GUI Scale therefore
        // changes the available QShop range instead of being cancelled out.
        double logicalWidth = Math.min(qshopScreen.width,
                minecraft.getWindow().getWidth() / nativeGuiScale);
        double logicalHeight = Math.min(qshopScreen.height,
                minecraft.getWindow().getHeight() / nativeGuiScale);
        double contentWidth = Math.max(1.0D, qshopScreen.qshopContentWidth());
        double contentHeight = Math.max(1.0D, qshopScreen.qshopContentHeight());
        double fitWidth = (logicalWidth - SCREEN_MARGIN_LOGICAL_UNITS * 2.0D) / contentWidth;
        double fitHeight = (logicalHeight - SCREEN_MARGIN_LOGICAL_UNITS * 2.0D) / contentHeight;
        double maximum = Math.min(fitWidth, fitHeight);
        if (!Double.isFinite(maximum) || maximum <= 0.0D) {
            maximum = Double.MIN_NORMAL;
        }
        return new ScaleBounds(maximum * MIN_SCALE_RATIO, maximum);
    }

    /** Handles QShop-local zoom without changing Minecraft's global GUI scale. */
    public static boolean handleScaleWheel(double delta, boolean shiftDown) {
        if (!shiftDown || delta == 0.0D) {
            return false;
        }

        double next = clampScale(scale() + (delta > 0.0D
                ? QShopCommonConfig.GUI_SCALE_STEP : -QShopCommonConfig.GUI_SCALE_STEP));
        QShopCommonConfig.setGuiScale(next);
        return true;
    }

    /** Handles the configured inventory key for the main shop screen only. */
    public static boolean handleInventoryKey(Screen screen, int keyCode, int scanCode,
                                             boolean inputFocused) {
        if (inputFocused) {
            return false;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            screen.onClose();
            return true;
        }
        return false;
    }

    /** Converts a screen-space mouse coordinate into QShop's unscaled coordinates. */
    public static int toLogicalCoordinate(double coordinate, int viewportSize) {
        double center = viewportSize / 2.0D;
        return (int) Math.round(center + (coordinate - center) / renderScale());
    }

    /** Converts a QShop coordinate into the coordinate drawn by its local matrix. */
    public static double toScaledCoordinate(double coordinate, int viewportSize) {
        double center = viewportSize / 2.0D;
        return center + (coordinate - center) * renderScale();
    }

    private static double clampScale(double value) {
        ScaleBounds bounds = scaleBounds();
        if (!Double.isFinite(value)) {
            return bounds.minimum();
        }
        return Math.max(bounds.minimum(), Math.min(bounds.maximum(), value));
    }

    public record ScaleBounds(double minimum, double maximum) {
    }
}
