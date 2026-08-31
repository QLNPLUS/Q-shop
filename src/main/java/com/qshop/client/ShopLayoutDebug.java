package com.qshop.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qshop.config.QShopCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Optional client-side layout editor. Offsets are persisted outside the world in config/qshop_layout.json.
 * The editor is intentionally gated by qshop-common.toml so normal players never see its controls.
 */
public final class ShopLayoutDebug {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "qshop_layout.json";
    private static final int MAX_OFFSET = 512;
    private static final EnumMap<Layout, EnumMap<Widget, Position>> DEFAULT_POSITIONS = defaultPositions();
    private static final EnumMap<Layout, EnumMap<Widget, Position>> POSITIONS = emptyPositions();
    private static final EnumMap<TradeWidget, Position> DEFAULT_TRADE_POSITIONS = defaultTradePositions();
    private static final EnumMap<TradeWidget, Position> TRADE_POSITIONS = new EnumMap<>(TradeWidget.class);
    private static final EnumMap<PickerWidget, Position> DEFAULT_PICKER_POSITIONS = defaultPickerPositions();
    private static final EnumMap<PickerWidget, Position> PICKER_POSITIONS = new EnumMap<>(PickerWidget.class);
    private static Widget selected = Widget.GRID;
    private static TradeWidget selectedTrade = TradeWidget.PANEL;
    private static PickerWidget selectedPicker = PickerWidget.PANEL;
    private static Layout activeLayout = Layout.STANDARD;
    private static DebugScreen activeScreen = DebugScreen.SHOP;
    private static boolean enabled;
    private static boolean loaded;

    public enum DebugScreen {
        SHOP("Shop"),
        TRADE_SETTINGS("Trade settings"),
        ITEM_PICKER("Item browser");

        private final String label;

        DebugScreen(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Layout {
        STANDARD("7x3"),
        WIDE("8x4");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        private static Layout from(boolean wide) {
            return wide ? WIDE : STANDARD;
        }
    }

    public enum Widget {
        PANEL("Panel"),
        TAB_BAR("Tab bar"),
        GRID("Entry grid"),
        ADD_BUTTON("Add entry button"),
        EDIT_BUTTON("Edit mode button"),
        SEARCH_BOX("Search box"),
        SEARCH_BUTTON("Search button"),
        LAYOUT_BUTTON("Layout button"),
        CLOSE_BUTTON("Close button");

        private final String label;

        Widget(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum TradeWidget {
        PANEL("Panel"),
        HEADER("Header"),
        TITLE_ROW("Title row"),
        DESCRIPTION_ROW("Description row"),
        DISPLAY_ROW("Display item row"),
        PLAYER_GIVE_ROW("Player payment row"),
        PLAYER_ITEM_ROW("Player item row"),
        SHOP_GIVE_ROW("Shop reward row"),
        SHOP_ITEM_ROW("Shop item row"),
        PRICE_ROW("Price row"),
        LIMITS_ROW("Limits row"),
        RESET_ROW("Reset row"),
        REQUIREMENTS_ROW("Requirements row"),
        STAGE_DESCRIPTION_ROW("Stage description row"),
        VISIBILITY_ROW("Visibility option"),
        TYPE_ROW("Derived type row"),
        COMMAND_HEADER("Command header"),
        COMMAND_ROWS("Command rows"),
        BOTTOM_ACTIONS("Bottom actions");

        private final String label;

        TradeWidget(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PickerWidget {
        PANEL("Panel"),
        MODE_BUTTON("Mode button"),
        CLOSE_BUTTON("Close button"),
        BACK_BUTTON("Back button"),
        SEARCH_BOX("Search box"),
        GRID("Item grid");

        private final String label;

        PickerWidget(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private ShopLayoutDebug() {
    }

    public static void beginScreen(boolean wideLayout) {
        activeScreen = DebugScreen.SHOP;
        activeLayout = Layout.from(wideLayout);
        loaded = false;
        load();
    }

    public static void beginScreen(DebugScreen screen) {
        activeScreen = screen == null ? DebugScreen.SHOP : screen;
        loaded = false;
        load();
    }

    /** Selects the offset set used by the currently visible 7x3 or 8x4 screen. */
    public static void setLayout(boolean wideLayout) {
        activeScreen = DebugScreen.SHOP;
        activeLayout = Layout.from(wideLayout);
        load();
    }

    public static boolean isEnabled() {
        return enabled && QShopCommonConfig.layoutDebugEnabled();
    }

    public static boolean isConfiguredEnabled() {
        return QShopCommonConfig.layoutDebugEnabled();
    }

    public static boolean toggle() {
        if (!QShopCommonConfig.layoutDebugEnabled()) {
            return false;
        }
        load();
        enabled = !enabled;
        return enabled;
    }

    public static Widget selected() {
        return selected;
    }

    public static TradeWidget selectedTrade() {
        return selectedTrade;
    }

    public static PickerWidget selectedPicker() {
        return selectedPicker;
    }

    public static void selectNext(boolean reverse) {
        int direction = reverse ? -1 : 1;
        switch (activeScreen) {
            case TRADE_SETTINGS -> {
                TradeWidget[] widgets = TradeWidget.values();
                int index = selectedTrade.ordinal();
                selectedTrade = widgets[Math.floorMod(index + direction, widgets.length)];
            }
            case ITEM_PICKER -> {
                PickerWidget[] widgets = PickerWidget.values();
                int index = selectedPicker.ordinal();
                selectedPicker = widgets[Math.floorMod(index + direction, widgets.length)];
            }
            default -> {
                Widget[] widgets = Widget.values();
                int index = selected.ordinal();
                selected = widgets[Math.floorMod(index + direction, widgets.length)];
            }
        }
    }

    public static int x(Widget widget, int normalX) {
        return normalX + position(widget).x();
    }

    public static int y(Widget widget, int normalY) {
        return normalY + position(widget).y();
    }

    public static int x(TradeWidget widget, int normalX) {
        return normalX + position(widget).x();
    }

    public static int y(TradeWidget widget, int normalY) {
        return normalY + position(widget).y();
    }

    public static int x(PickerWidget widget, int normalX) {
        return normalX + position(widget).x();
    }

    public static int y(PickerWidget widget, int normalY) {
        return normalY + position(widget).y();
    }

    public static void moveSelected(int dx, int dy) {
        if (!isEnabled()) {
            return;
        }
        switch (activeScreen) {
            case TRADE_SETTINGS -> {
                Position current = position(selectedTrade);
                TRADE_POSITIONS.put(selectedTrade,
                        new Position(clamp(current.x() + dx), clamp(current.y() + dy)));
            }
            case ITEM_PICKER -> {
                Position current = position(selectedPicker);
                PICKER_POSITIONS.put(selectedPicker,
                        new Position(clamp(current.x() + dx), clamp(current.y() + dy)));
            }
            default -> {
                Position current = position(selected);
                POSITIONS.get(activeLayout).put(selected,
                        new Position(clamp(current.x() + dx), clamp(current.y() + dy)));
            }
        }
        save();
    }

    public static void renderOverlay(GuiGraphics graphics, Font font,
                                     int x, int y, int width, int height) {
        if (!isEnabled()) {
            return;
        }
        int right = x + Math.max(1, width);
        int bottom = y + Math.max(1, height);
        graphics.fill(x, y, right, y + 1, 0xFFFFD54F);
        graphics.fill(x, bottom - 1, right, bottom, 0xFFFFD54F);
        graphics.fill(x, y, x + 1, bottom, 0xFFFFD54F);
        graphics.fill(right - 1, y, right, bottom, 0xFFFFD54F);

        String context = activeScreen == DebugScreen.SHOP ? activeLayout.label() : activeScreen.label();
        String label = "F8 Debug | " + context + " | Tab: " + selectedLabel()
                + " | arrows: 5px | Alt: 1px";
        Position selectedPosition = selectedPosition();
        String offset = "offset " + selectedPosition.x() + ", " + selectedPosition.y();
        int textWidth = Math.max(font.width(label), font.width(offset));
        int panelX = 4;
        int panelY = 4;
        graphics.fill(panelX - 2, panelY - 2, panelX + textWidth + 4,
                panelY + font.lineHeight * 2 + 3, 0xCC111111);
        graphics.drawString(font, Component.literal(label), panelX, panelY, 0xFFFFD54F, false);
        graphics.drawString(font, Component.literal(offset), panelX,
                panelY + font.lineHeight, 0xFFFFFFFF, false);
    }

    private static Position position(Widget widget) {
        EnumMap<Widget, Position> positions = POSITIONS.get(activeLayout);
        EnumMap<Widget, Position> defaults = DEFAULT_POSITIONS.get(activeLayout);
        return positions.getOrDefault(widget, defaults.getOrDefault(widget, new Position(0, 0)));
    }

    private static Position position(TradeWidget widget) {
        return TRADE_POSITIONS.getOrDefault(widget,
                DEFAULT_TRADE_POSITIONS.getOrDefault(widget, new Position(0, 0)));
    }

    private static Position position(PickerWidget widget) {
        return PICKER_POSITIONS.getOrDefault(widget,
                DEFAULT_PICKER_POSITIONS.getOrDefault(widget, new Position(0, 0)));
    }

    private static Position selectedPosition() {
        return switch (activeScreen) {
            case TRADE_SETTINGS -> position(selectedTrade);
            case ITEM_PICKER -> position(selectedPicker);
            default -> position(selected);
        };
    }

    private static String selectedLabel() {
        return switch (activeScreen) {
            case TRADE_SETTINGS -> selectedTrade.label();
            case ITEM_PICKER -> selectedPicker.label();
            default -> selected.label();
        };
    }

    private static EnumMap<Layout, EnumMap<Widget, Position>> emptyPositions() {
        EnumMap<Layout, EnumMap<Widget, Position>> positions = new EnumMap<>(Layout.class);
        for (Layout layout : Layout.values()) {
            positions.put(layout, new EnumMap<>(Widget.class));
        }
        return positions;
    }

    private static EnumMap<Layout, EnumMap<Widget, Position>> defaultPositions() {
        EnumMap<Layout, EnumMap<Widget, Position>> positions = emptyPositions();
        EnumMap<Widget, Position> standard = positions.get(Layout.STANDARD);
        standard.put(Widget.PANEL, new Position(0, 0));
        standard.put(Widget.TAB_BAR, new Position(0, 0));
        standard.put(Widget.GRID, new Position(0, 0));
        standard.put(Widget.ADD_BUTTON, new Position(0, 0));
        standard.put(Widget.EDIT_BUTTON, new Position(0, 0));
        standard.put(Widget.SEARCH_BOX, new Position(-1, -19));
        standard.put(Widget.SEARCH_BUTTON, new Position(0, 0));
        standard.put(Widget.LAYOUT_BUTTON, new Position(0, 0));
        standard.put(Widget.CLOSE_BUTTON, new Position(0, 0));

        EnumMap<Widget, Position> wide = positions.get(Layout.WIDE);
        wide.put(Widget.PANEL, new Position(0, 0));
        wide.put(Widget.TAB_BAR, new Position(0, 0));
        wide.put(Widget.GRID, new Position(0, 0));
        wide.put(Widget.ADD_BUTTON, new Position(0, 0));
        wide.put(Widget.EDIT_BUTTON, new Position(0, 0));
        wide.put(Widget.SEARCH_BOX, new Position(-1, -20));
        wide.put(Widget.SEARCH_BUTTON, new Position(0, 0));
        wide.put(Widget.LAYOUT_BUTTON, new Position(0, 0));
        wide.put(Widget.CLOSE_BUTTON, new Position(0, 0));
        return positions;
    }

    private static EnumMap<TradeWidget, Position> defaultTradePositions() {
        EnumMap<TradeWidget, Position> positions = new EnumMap<>(TradeWidget.class);
        for (TradeWidget widget : TradeWidget.values()) {
            positions.put(widget, new Position(0, 0));
        }
        return positions;
    }

    private static EnumMap<PickerWidget, Position> defaultPickerPositions() {
        EnumMap<PickerWidget, Position> positions = new EnumMap<>(PickerWidget.class);
        for (PickerWidget widget : PickerWidget.values()) {
            positions.put(widget, new Position(0, 0));
        }
        return positions;
    }

    private static int clamp(int value) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (EnumMap<Widget, Position> positions : POSITIONS.values()) {
            positions.clear();
        }
        TRADE_POSITIONS.clear();
        PICKER_POSITIONS.clear();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject rootObject = root.getAsJsonObject();
            JsonObject layouts = rootObject.getAsJsonObject("layouts");
            if (layouts != null) {
                for (Layout layout : Layout.values()) {
                    JsonElement rawLayout = layouts.get(layout.name().toLowerCase(Locale.ROOT));
                    if (rawLayout != null && rawLayout.isJsonObject()) {
                        JsonObject layoutObject = rawLayout.getAsJsonObject();
                        JsonObject widgets = layoutObject.getAsJsonObject("widgets");
                        if (widgets != null) {
                            readWidgets(widgets, layout);
                        }
                    }
                }
            } else {
                // Version 1 stored one shared widget map. Keep it as the 7x3 offsets;
                // the 8x4 layout starts with independent default offsets.
                JsonObject widgets = rootObject.getAsJsonObject("widgets");
                if (widgets != null) {
                    readWidgets(widgets, Layout.STANDARD);
                }
            }

            JsonObject screens = rootObject.getAsJsonObject("screens");
            if (screens != null) {
                JsonElement trade = screens.get("trade_settings");
                if (trade != null && trade.isJsonObject()) {
                    readTradeWidgets(trade.getAsJsonObject().getAsJsonObject("widgets"));
                }
                JsonElement picker = screens.get("item_picker");
                if (picker != null && picker.isJsonObject()) {
                    readPickerWidgets(picker.getAsJsonObject().getAsJsonObject("widgets"));
                }
            }
        } catch (Exception exception) {
            System.err.println("[qshop] Could not read layout debug JSON: " + exception.getMessage());
        }
    }

    private static void readWidgets(JsonObject widgets, Layout layout) {
        EnumMap<Widget, Position> positions = POSITIONS.get(layout);
        for (Widget widget : Widget.values()) {
            JsonElement raw = widgets.get(widget.name().toLowerCase(Locale.ROOT));
            if (raw == null || !raw.isJsonObject()) {
                continue;
            }
            JsonObject value = raw.getAsJsonObject();
            positions.put(widget, new Position(clamp(readInt(value, "x")), clamp(readInt(value, "y"))));
        }
        // Version 2 used one shared top_controls offset for the text buttons.
        // Keep that user adjustment as the initial offset for both new icons.
        JsonElement legacy = widgets.get("top_controls");
        if (legacy != null && legacy.isJsonObject()) {
            Position legacyPosition = new Position(clamp(readInt(legacy.getAsJsonObject(), "x")),
                    clamp(readInt(legacy.getAsJsonObject(), "y")));
            positions.putIfAbsent(Widget.ADD_BUTTON, legacyPosition);
            positions.putIfAbsent(Widget.EDIT_BUTTON, legacyPosition);
        }
    }

    private static int readInt(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : 0;
    }

    private static void readTradeWidgets(JsonObject widgets) {
        if (widgets == null) {
            return;
        }
        for (TradeWidget widget : TradeWidget.values()) {
            JsonElement raw = widgets.get(widget.name().toLowerCase(Locale.ROOT));
            if (raw != null && raw.isJsonObject()) {
                JsonObject value = raw.getAsJsonObject();
                TRADE_POSITIONS.put(widget,
                        new Position(clamp(readInt(value, "x")), clamp(readInt(value, "y"))));
            }
        }
    }

    private static void readPickerWidgets(JsonObject widgets) {
        if (widgets == null) {
            return;
        }
        for (PickerWidget widget : PickerWidget.values()) {
            JsonElement raw = widgets.get(widget.name().toLowerCase(Locale.ROOT));
            if (raw != null && raw.isJsonObject()) {
                JsonObject value = raw.getAsJsonObject();
                PICKER_POSITIONS.put(widget,
                        new Position(clamp(readInt(value, "x")), clamp(readInt(value, "y"))));
            }
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 3);
        root.addProperty("description", "Component offsets relative to each QShop layout.");
        JsonObject layouts = new JsonObject();
        for (Layout layout : Layout.values()) {
            JsonObject layoutObject = new JsonObject();
            layoutObject.addProperty("label", layout.label());
            JsonObject widgets = new JsonObject();
            for (Widget widget : Widget.values()) {
                Position position = POSITIONS.get(layout).getOrDefault(widget, new Position(0, 0));
                JsonObject value = new JsonObject();
                value.addProperty("x", position.x());
                value.addProperty("y", position.y());
                widgets.add(widget.name().toLowerCase(Locale.ROOT), value);
            }
            layoutObject.add("widgets", widgets);
            layouts.add(layout.name().toLowerCase(Locale.ROOT), layoutObject);
        }
        root.add("layouts", layouts);
        JsonObject screens = new JsonObject();
        screens.add("trade_settings", saveTradeWidgets("Trade settings", TRADE_POSITIONS, DEFAULT_TRADE_POSITIONS));
        screens.add("item_picker", savePickerWidgets("Item browser", PICKER_POSITIONS, DEFAULT_PICKER_POSITIONS));
        root.add("screens", screens);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            System.err.println("[qshop] Could not save layout debug JSON: " + exception.getMessage());
        }
    }

    private static JsonObject saveTradeWidgets(String label,
                                                EnumMap<TradeWidget, Position> positions,
                                                EnumMap<TradeWidget, Position> defaults) {
        JsonObject screen = new JsonObject();
        screen.addProperty("label", label);
        JsonObject widgets = new JsonObject();
        for (TradeWidget widget : TradeWidget.values()) {
            Position position = positions.getOrDefault(widget, defaults.getOrDefault(widget, new Position(0, 0)));
            widgets.add(widget.name().toLowerCase(Locale.ROOT), positionJson(position));
        }
        screen.add("widgets", widgets);
        return screen;
    }

    private static JsonObject savePickerWidgets(String label,
                                                EnumMap<PickerWidget, Position> positions,
                                                EnumMap<PickerWidget, Position> defaults) {
        JsonObject screen = new JsonObject();
        screen.addProperty("label", label);
        JsonObject widgets = new JsonObject();
        for (PickerWidget widget : PickerWidget.values()) {
            Position position = positions.getOrDefault(widget, defaults.getOrDefault(widget, new Position(0, 0)));
            widgets.add(widget.name().toLowerCase(Locale.ROOT), positionJson(position));
        }
        screen.add("widgets", widgets);
        return screen;
    }

    private static JsonObject positionJson(Position position) {
        JsonObject value = new JsonObject();
        value.addProperty("x", position.x());
        value.addProperty("y", position.y());
        return value;
    }

    private record Position(int x, int y) {
    }
}
