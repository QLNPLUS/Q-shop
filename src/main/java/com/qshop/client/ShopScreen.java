package com.qshop.client;

import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.config.QShopCommonConfig;
import com.qshop.net.AddTabPacket;
import com.qshop.net.ClientShopEntry;
import com.qshop.net.ClientTab;
import com.qshop.net.CopyEntryPacket;
import com.qshop.net.MoveTabPacket;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import com.qshop.net.RemoveEntryPacket;
import com.qshop.net.RemoveTabPacket;
import com.qshop.net.ReorderEntryPacket;
import com.qshop.net.SwapEntryPacket;
import com.qshop.net.TradePacket;
import com.qshop.shop.ShopEntryType;
import com.qshop.util.QText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

/**
 * 商店主界面:可切换 7x3 / 8x4 的列 x 行网格,平滑滚动动画;
 * 点击条目打开交易子窗口;编辑模式支持拖拽交换排序、右键菜单(悬浮式,不替换界面)。
 */
public class ShopScreen extends QShopScreen {

    private static final int GUI_W = 250;
    private static final int GUI_W_WIDE = 280;
    private static final int GUI_H = 200;
    // 原版 renderItemDecorations 会把基础层数量文字提升约 +200Z；幽灵必须高于它。
    private static final float DRAG_LAYER_Z = 350.0f;
    private static final float TAB_MASK_LAYER_Z = 300.0f;
    private static final float TRADE_LAYER_Z = 400.0f;
    private static final float MENU_LAYER_Z = 600.0f;

    // ---- 左侧子商店 tab 栏(在主面板左侧) ----
    private static final int TAB_BAR_W = 46;
    private static final int TAB_W = 40;
    private static final int TAB_H = 24;
    private static final int TAB_PITCH = 26;
    /** tab 列表区域:从 y+38 到 y+38+TAB_LIST_H(给底部余额留位) */
    private static final int TAB_LIST_H = 146;
    /** 列表上下渐隐遮罩高度(滚动时内容淡出;也是留白死区,保证条目能完整显示) */
    private static final int TAB_MASK_H = 10;
    private int tabScroll = 0;
    private float tabScrollAnim = 0;

    final OpenShopPacket data;
    private final List<ClientTab> visibleTabs = new ArrayList<>();
    int scroll = 0;
    boolean editMode = false;

    /**
     * 编辑模式开关的记忆(客户端会话级):开启后跨关闭/重开商店界面保持,
     * 需手动关闭才能回到非编辑模式。生存模式(服务端 editing=false)优先级更高,强制关闭。
     */
    private static boolean rememberedEditMode = false;

    int activeTab = 0;
    private int requestedServerTab = 0;

    private int left;
    private int top;
    private boolean wideLayout;
    private float rowAnim = 0;

    // ---- 当前布局几何(标准 7x3 / 宽面板 8x4) ----
    private int cols = 6;
    private int rows = 3;
    private int cellW = 38;
    private int cellH = 48;
    private int stepX = 38;
    private int stepY = 49;
    private int visible = 18;

    // ---- 编辑模式拖拽排序状态 ----
    private int pressedIndex = -1;
    private boolean dragActive = false;

    // ---- 编辑模式右键菜单(悬浮在商店界面上,不替换屏幕) ----
    private static final int MENU_W = 96;
    private static final int MENU_H = 80;
    private int menuIndex = -1;
    private int menuX = 0;
    private int menuY = 0;

    // ---- 编辑模式:子商店(tab)右键菜单(编辑/删除/上移/下移) ----
    private int tabMenuIndex = -1;
    private int tabMenuX = 0;
    private int tabMenuY = 0;

    // ---- 交易悬浮窗(小窗口悬浮在主商店之上,不关闭主界面) ----
    private static final int TRADE_W = 150;
    private static final int TRADE_H = 133;
    private int tradeIndex = -1;
    private int tradeMaxUnits = 0;
    private boolean tradeSyncing = false;
    private boolean overlayPointerCapture = false;
    private EditBox tradeUnitsBox;
    private QSlider tradeSlider;
    private final List<AbstractWidget> tradeWidgets = new ArrayList<>();

    // ---- 商店搜索 ----
    private static final int SEARCH_BUTTON_SIZE = 16;
    private static final int SEARCH_BOX_H = 14;
    private static final int SEARCH_BOX_WIDTH_REDUCTION = 30;
    private static final int SEARCH_TEXT_X_OFFSET = 4;
    private static final int SEARCH_TEXT_Y_OFFSET = 3;
    private boolean searchActive;
    private String searchQuery = "";
    private EditBox searchBox;

    /** Search text is rendered one pixel lower without moving the hitbox or input background. */
    private static final class SearchEditBox extends EditBox {
        private SearchEditBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h,
                              Component message) {
            super(font, x, y, w, h, message);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.pose().pushPose();
            g.pose().translate(SEARCH_TEXT_X_OFFSET, SEARCH_TEXT_Y_OFFSET, 0.0D);
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.pose().popPose();
        }
    }

    public ShopScreen(OpenShopPacket data) {
        super(QText.parse(data.shopName.isEmpty() ? data.shopId : data.shopName));
        this.data = data;
        this.wideLayout = QShopCommonConfig.lastLayoutWide();
        this.searchActive = QShopCommonConfig.searchActive();
        // 全新打开:恢复记忆的编辑模式(生存模式 editing=false 时强制关闭,优先级更高)
        this.editMode = data.editing && rememberedEditMode;
        if (!data.tabs.isEmpty()) {
            this.requestedServerTab = data.tabs.get(0).serverIndex;
        }
    }

    public ShopScreen(OpenShopPacket data, int scroll, boolean editMode) {
        this(data);
        this.scroll = Math.max(0, scroll);
        // 编辑模式必须同时有服务端权限(创造+op),否则强制关闭;并更新记忆状态
        this.editMode = editMode && data.editing;
        rememberedEditMode = this.editMode;
    }

    public ShopScreen(OpenShopPacket data, int scroll, boolean editMode, int activeServerTab) {
        this(data, scroll, editMode);
        this.requestedServerTab = Math.max(0, activeServerTab);
    }

    @Override
    protected int qshopContentWidth() {
        return TAB_BAR_W + 6 + panelWidth();
    }

    @Override
    protected int qshopContentHeight() {
        return GUI_H;
    }

    @Override
    protected void init() {
        ShopLayoutDebug.beginScreen(wideLayout);
        configureLayout();
        applyVisibleTabs(requestedServerTab);
        applyActiveTabEntries();
        rowAnim = scroll / (float) cols;
        scrollActiveTabIntoView();
        tabScrollAnim = tabScroll;

        layout();
    }

    private void configureLayout() {
        int width = wideLayout ? GUI_W_WIDE : GUI_W;
        int groupWidth = TAB_BAR_W + 6 + width;
        int groupLeft = (this.width - groupWidth) / 2;
        // left stores the panel origin; include the tab bar in the centered group.
        this.left = groupLeft + TAB_BAR_W + 6;
        this.top = (this.height - GUI_H) / 2;
        if (wideLayout) {
            // 8x4:略缩小格子和行距，保留 200px 面板高度，并为滚动条留出右侧空间。
            cols = 8;
            rows = 4;
            cellW = 31;
            stepX = 32;
            cellH = 29;
            stepY = 41;
        } else {
            // 标准布局固定为 7x3，和原有高 GUI 缩放下的布局一致。
            cols = 7;
            rows = 3;
            cellW = 32;
            stepX = 33;
            cellH = 31;
            stepY = 44;
        }
        visible = cols * rows;
    }

    private int panelWidth() {
        return wideLayout ? GUI_W_WIDE : GUI_W;
    }

    private int panelX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.PANEL, left);
    }

    private int panelY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.PANEL, top);
    }

    private int gridX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.GRID, left + (wideLayout ? 8 : 11));
    }

    private int gridY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.GRID, top + (wideLayout ? 37 : 40));
    }

    private int gridViewportHeight() {
        return wideLayout ? GUI_H - 37 : rows * stepY;
    }

    private int tabBarX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.TAB_BAR, left - TAB_BAR_W - 6);
    }

    private int tabBarY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.TAB_BAR, top);
    }

    private int layoutButtonX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.LAYOUT_BUTTON, left + panelWidth() - 38);
    }

    private int layoutButtonY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.LAYOUT_BUTTON, top + 6);
    }

    private int searchButtonX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.SEARCH_BUTTON, left + panelWidth() - 56);
    }

    private int searchButtonY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.SEARCH_BUTTON, top + 6);
    }

    private int searchBoxX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.SEARCH_BOX, left + 8);
    }

    private int searchBoxY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.SEARCH_BOX, top + 22);
    }

    private int searchBoxWidth() {
        return panelWidth() - 70 - SEARCH_BOX_WIDTH_REDUCTION;
    }

    private int closeButtonX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.CLOSE_BUTTON, left + panelWidth() - 20);
    }

    private int closeButtonY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.CLOSE_BUTTON, top + 6);
    }

    private int addButtonX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.ADD_BUTTON, left + panelWidth() - 92);
    }

    private int addButtonY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.ADD_BUTTON, top + 6);
    }

    private int editButtonX() {
        return ShopLayoutDebug.x(ShopLayoutDebug.Widget.EDIT_BUTTON, left + panelWidth() - 74);
    }

    private int editButtonY() {
        return ShopLayoutDebug.y(ShopLayoutDebug.Widget.EDIT_BUTTON, top + 6);
    }

    private void layout() {
        closeMenu();
        closeTrade();
        this.clearWidgets();
        searchBox = null;

        if (searchActive) {
            searchBox = new SearchEditBox(this.font, searchBoxX(), searchBoxY(), searchBoxWidth(), SEARCH_BOX_H,
                    Component.translatable("qshop.gui.search"));
            searchBox.setMaxLength(128);
            searchBox.setBordered(false);
            searchBox.setHint(Component.translatable("qshop.gui.search_hint"));
            searchBox.setValue(searchQuery);
            searchBox.setResponder(value -> {
                searchQuery = value == null ? "" : value;
                applyActiveTabEntries();
                scroll = 0;
                rowAnim = 0;
            });
            addRenderableWidget(searchBox);
        }

        // 顶行控件全部使用独立的 16x16 图标和独立布局偏移。
        if (data.editing && editMode) {
            QIconButton addButton = new QIconButton(addButtonX(), addButtonY(), ShopTextures.Icon.ADD,
                    this::openAdd);
            addButton.setTooltip(Tooltip.create(Component.translatable("qshop.gui.add")));
            addRenderableWidget(addButton);
        }
        if (data.editing) {
            QIconButton editButton = new QIconButton(editButtonX(), editButtonY(), ShopTextures.Icon.EDIT,
                    () -> {
                        int preferredTab = activeServerTabIndex();
                        editMode = !editMode;
                        rememberedEditMode = editMode;
                        scroll = 0;
                        rowAnim = 0;
                        applyVisibleTabs(preferredTab);
                        applyActiveTabEntries();
                        layout();
                    });
            editButton.setActive(editMode);
            editButton.setTooltip(Tooltip.create(Component.translatable("qshop.gui.edit")));
            addRenderableWidget(editButton);
        }
        QIconButton searchButton = new QIconButton(searchButtonX(), searchButtonY(), ShopTextures.Icon.SEARCH,
                this::toggleSearch);
        searchButton.setActive(searchActive);
        searchButton.setTooltip(Tooltip.create(Component.translatable("qshop.gui.search")));
        addRenderableWidget(searchButton);

        QIconButton layoutButton = new QIconButton(layoutButtonX(), layoutButtonY(), ShopTextures.Icon.LAYOUT,
                this::toggleLayout);
        layoutButton.setTooltip(Tooltip.create(Component.translatable("qshop.gui.layout_switch")));
        addRenderableWidget(layoutButton);
        addRenderableWidget(new QIconButton(closeButtonX(), closeButtonY(), ShopTextures.Icon.CLOSE, this::onClose));
    }

    private void toggleSearch() {
        searchActive = !searchActive;
        QShopCommonConfig.setSearchActive(searchActive);
        if (!searchActive) {
            searchQuery = "";
        }
        applyActiveTabEntries();
        scroll = 0;
        rowAnim = 0;
        layout();
        if (searchActive && searchBox != null) {
            searchBox.setFocused(true);
        }
    }

    private void toggleLayout() {
        wideLayout = !wideLayout;
        QShopCommonConfig.setLastLayout(wideLayout);
        ShopLayoutDebug.setLayout(wideLayout);
        scroll = 0;
        rowAnim = 0;
        closeTrade();
        configureLayout();
        layout();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (wideLayout) {
            ShopTextures.panelWide(g, panelX(), panelY());
        } else {
            ShopTextures.panel(g, panelX(), panelY());
        }

        // 左侧子商店 tab 栏(顶部商店名,中间子商店,底部余额)
        renderTabBar(g, mouseX, mouseY);

        // 空商店提示(网格区域居中)
        if (data.entries.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("qshop.gui.empty"),
                    gridX() + (cols * stepX) / 2, gridY() + gridViewportHeight() / 2 - 4, 0xFFFFFF);
        }

        // 滚动动画(以"行"为单位插值,时间基准,帧率无关)
        int maxScroll = maxScroll();
        scroll = Mth.clamp(scroll, 0, maxScroll);
        float target = scroll / (float) cols;
        float delta = Minecraft.getInstance().getDeltaFrameTime();
        rowAnim += (target - rowAnim) * Math.min(1.0f, delta * 15f);
        if (Math.abs(target - rowAnim) < 0.005f) {
            rowAnim = target;
        }

        // 网格(手动渲染,平滑滚动;裁剪到网格视口,防止溢出面板)
        int hovered = menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0 ? indexAt(mouseX, mouseY) : -1;
        int gx = gridX();
        int gy = gridY();
        int baseRow = (int) Math.floor(rowAnim);
        float frac = rowAnim - baseRow;
        ShopTextures.enableScissor(g, gx, gy, cols * stepX, gridViewportHeight());
        for (int r = 0; r <= rows; r++) {
            int y = gy + (int) (r * stepY - frac * stepY);
            for (int c = 0; c < cols; c++) {
                int index = (baseRow + r) * cols + c;
                if (index < 0 || index >= data.entries.size()) {
                    continue;
                }
                drawCell(g, index, gx + c * stepX, y, index == hovered, mouseX, mouseY, false);
            }
        }
        ShopTextures.disableScissor(g);

        // 主网格滚动条(可滚动时才显示)
        if (maxScroll() > 0) {
            int contentRows = Math.max(rows, (int) Math.ceil(data.entries.size() / (float) cols));
            int zoneY = gy;
            int zoneH = gridViewportHeight();
            int knobH = Math.max(8, zoneH * rows / contentRows);
            int knobY = zoneY + (zoneH - knobH) * (scroll / cols) / Math.max(1, contentRows - rows);
            int sbX = gx + cols * stepX + 2;
            ShopTextures.scrollTrack(g, sbX, zoneY, zoneH);
            ShopTextures.scrollKnob(g, sbX - 1, knobY, knobH);
        }

        if (searchActive && searchBox != null) {
            ShopTextures.input(g, searchBoxX(), searchBoxY(), searchBoxWidth(), SEARCH_BOX_H,
                    searchBox.isFocused());
        }

        // 主界面控件先绘制，浮层不会再被关闭/编辑按钮覆盖。
        renderWidgets(g, mouseX, mouseY, partialTick, false);

        // 悬浮 tooltip(菜单/交易窗打开时不显示,避免盖住它们)
        // 槽位部分 = 物品 tooltip;价格条部分 = 完整价格 / 交换数量(与余额 tooltip 同理)
        if (!dragActive && menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0 && hovered >= 0) {
            int slot = cellH;
            int c = hovered % cols;
            int r = hovered / cols - baseRow;
            int cellY = gy + (int) (r * stepY - frac * stepY);
            if (mouseY >= cellY && mouseY <= cellY + slot) {
                renderCellTooltip(g, hovered, mouseX, mouseY);
            } else if (mouseY > cellY + slot && mouseY <= cellY + slot + 13) {
                renderPriceTooltip(g, hovered, mouseX, mouseY);
            }
        }
        int hoveredTab = tabIndexAt(mouseX, mouseY);
        if (!dragActive && menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0 && hoveredTab >= 0) {
            ClientTab ct = visibleTabs.get(hoveredTab);
            if (ct != null) {
                List<Component> lines = tabTooltipLines(ct);
                if (lines.size() > 1) {
                    g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                } else if (!ct.icon.isEmpty()) {
                    g.renderTooltip(this.font, ct.icon, mouseX, mouseY);
                }
            }
        }

        flushAll(g);

        // tab 栏渐隐遮罩:在全部基础层落屏后立即绘制,确保压在图标/文字之上
        renderTabMasks(g);

        // 拖拽幽灵高于主界面的物品和控件。
        if (dragActive && pressedIndex >= 0 && menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DRAG_LAYER_Z);
            int tgt = indexAt(mouseX, mouseY);
            if (tgt >= 0 && tgt != pressedIndex) {
                int tc = tgt % cols;
                int viewRow = tgt / cols - baseRow;
                int ty = gy + (int) (viewRow * stepY - frac * stepY);
                ShopTextures.slot(g, gx + tc * stepX, ty, cellH, cellH, true, true);
            }
            drawCell(g, pressedIndex, (int) mouseX - cellW / 2, (int) mouseY - 24, true, mouseX, mouseY, true);
            // 数量徽标:原版 renderItemDecorations 内部硬编码 z+200,会把数字顶到幽灵层之上;
            // 这里在幽灵图层(DRAG_LAYER_Z)内手动绘制,数字属于幽灵本身
            ClientShopEntry ge = data.entries.get(pressedIndex);
            ItemStack gicon = cellIcon(ge);
            if (!gicon.isEmpty() && gicon.getCount() != 1) {
                int gslot = cellH;
                int gix = (int) mouseX - cellW / 2 + (gslot - 16) / 2;
                int giy = (int) mouseY - 24 + (gslot - 16) / 2;
                String cnt = String.valueOf(gicon.getCount());
                g.drawString(this.font, cnt, gix + 17 - this.font.width(cnt), giy + 9, 0xFFFFFF, true);
            }
            g.flush();
            g.pose().popPose();
        }

        // 交易窗口和它自己的控件位于同一个显式浮层。
        if (tradeIndex >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, TRADE_LAYER_Z);
            drawTradePanel(g);
            renderWidgets(g, mouseX, mouseY, partialTick, true);
            g.flush();
            g.pose().popPose();
        }

        // 右键菜单始终位于最高层(条目菜单与 tab 菜单互斥,不会同时出现)。
        if (menuIndex >= 0 || tabMenuIndex >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, MENU_LAYER_Z);
            if (menuIndex >= 0) {
                renderMenu(g, mouseX, mouseY);
            }
            if (tabMenuIndex >= 0) {
                renderTabMenu(g, mouseX, mouseY);
            }
            g.flush();
            g.pose().popPose();
        }

        renderLayoutDebug(g);

    }

    private void renderLayoutDebug(GuiGraphics g) {
        if (!ShopLayoutDebug.isEnabled()) {
            return;
        }
        ShopLayoutDebug.Widget widget = ShopLayoutDebug.selected();
        int x;
        int y;
        int w;
        int h;
        switch (widget) {
            case PANEL -> {
                x = panelX();
                y = panelY();
                w = panelWidth();
                h = GUI_H;
            }
            case TAB_BAR -> {
                x = tabBarX();
                y = tabBarY();
                w = TAB_BAR_W;
                h = GUI_H;
            }
            case GRID -> {
                x = gridX();
                y = gridY();
                w = cols * stepX;
                h = gridViewportHeight();
            }
            case ADD_BUTTON -> {
                x = addButtonX();
                y = addButtonY();
                w = 16;
                h = 16;
            }
            case EDIT_BUTTON -> {
                x = editButtonX();
                y = editButtonY();
                w = 16;
                h = 16;
            }
            case SEARCH_BOX -> {
                x = searchBoxX();
                y = searchBoxY();
                w = searchBoxWidth();
                h = SEARCH_BOX_H;
            }
            case SEARCH_BUTTON -> {
                x = searchButtonX();
                y = searchButtonY();
                w = SEARCH_BUTTON_SIZE;
                h = SEARCH_BUTTON_SIZE;
            }
            case LAYOUT_BUTTON -> {
                x = layoutButtonX();
                y = layoutButtonY();
                w = 16;
                h = 16;
            }
            case CLOSE_BUTTON -> {
                x = closeButtonX();
                y = closeButtonY();
                w = 12;
                h = 12;
            }
            default -> {
                return;
            }
        }
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, MENU_LAYER_Z + 50.0f);
        ShopLayoutDebug.renderOverlay(g, this.font, x, y, w, h);
        g.flush();
        g.pose().popPose();
    }

    private void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float partialTick, boolean tradeLayer) {
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && tradeWidgets.contains(widget) == tradeLayer) {
                widget.render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    /** 子商店 tooltip:描述、任务/阶段要求以及锁定任务跳转提示。 */
    private List<Component> tabTooltipLines(ClientTab tab) {
        String desc = tab.description == null ? "" : tab.description;
        List<Component> lines = new ArrayList<>();
        if (!desc.isEmpty() || !tab.requiredQuests.isEmpty() || !tab.requiredStages.isEmpty()) {
            lines.add(QText.parse(tab.name == null || tab.name.isEmpty() ? "Tab" : tab.name));
        }
        if (!desc.isEmpty()) {
            for (String line : desc.split("\\n")) {
                if (!line.isBlank()) {
                    lines.add(QText.parse(line));
                }
            }
        }
        if (!tab.requiredQuests.isEmpty()) {
            lines.add(Component.translatable("qshop.msg.req_quest").append(": ")
                    .append(String.join(", ", FtbQuestClient.questNames(tab.requiredQuests)))
                    .withStyle(s -> s.withColor(0xFFAA55)));
            if (!editMode && !tab.requirementsMet) {
                lines.add(Component.translatable("qshop.msg.quest_click_hint")
                        .withStyle(s -> s.withColor(0xFFAA55)));
            }
        }
        if (!tab.requiredStages.isEmpty()) {
            lines.add(Component.translatable("qshop.msg.req_stage").append(": " + stageRequirementLabels(tab))
                    .withStyle(s -> s.withColor(0xFFAA55)));
        }
        return lines;
    }

    private static String stageRequirementLabels(ClientTab tab) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < tab.requiredStages.size(); i++) {
            String stage = tab.requiredStages.get(i);
            String description = i < tab.requiredStageDescriptions.size()
                    ? tab.requiredStageDescriptions.get(i) : "";
            labels.add(description == null || description.isBlank() ? stage : description);
        }
        return String.join(", ", labels);
    }

    /** 刷新 GUI 缓冲(双保险:GuiGraphics 缓冲 + 全局 renderBuffers 缓冲) */
    private static void flushAll(GuiGraphics g) {
        g.flush();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    /** 左侧子商店 tab 栏:顶部商店名,中间可滚动的子商店列表,底部余额 */
    private void renderTabBar(GuiGraphics g, int mouseX, int mouseY) {
        int x = tabBarX();
        int y = tabBarY();
        ShopTextures.tabBar(g, x, y);

        // 顶部:商店图标 + 名称
        if (!data.icon.isEmpty()) {
            g.renderItem(data.icon, x + (TAB_BAR_W - 16) / 2, y + 3);
        }
        g.drawCenteredString(this.font, QText.clip(this.title, this.font, TAB_BAR_W - 4),
                x + TAB_BAR_W / 2, y + 21, 0xFFFFFF);
        g.fill(x + 4, y + 32, x + TAB_BAR_W - 4, y + 33, 0xFF444444);

        // 子商店列表使用独立裁剪区和平滑像素滚动。
        int ty0 = y + 38;
        int endY = ty0 + TAB_LIST_H;
        tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll());
        float delta = Minecraft.getInstance().getDeltaFrameTime();
        tabScrollAnim += (tabScroll - tabScrollAnim) * Math.min(1.0f, delta * 15f);
        if (Math.abs(tabScroll - tabScrollAnim) < 0.05f) {
            tabScrollAnim = tabScroll;
        }
        ShopTextures.enableScissor(g, x + 3, ty0, TAB_W, TAB_LIST_H);
        boolean tabInteractive = menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0;
        for (int i = 0; i < visibleTabs.size(); i++) {
            int ty = Math.round(ty0 + i * TAB_PITCH - tabScrollAnim);
            if (ty + TAB_H <= ty0 || ty >= endY) {
                continue;
            }
            ClientTab t = visibleTabs.get(i);
            boolean sel = i == activeTab;
            boolean hover = tabInteractive && mouseX >= x + 3 && mouseX <= x + 3 + TAB_W
                    && mouseY >= Math.max(ty, ty0) && mouseY <= Math.min(ty + TAB_H, endY);
            ShopTextures.tabButton(g, x + 3, ty, sel, hover, !t.requirementsMet);
            if (!t.icon.isEmpty()) {
                g.renderItem(t.icon, x + 3 + (TAB_W - 16) / 2, ty + 1);
            }
            // 名称限制在按钮内部,避免压到下一项(支持 § 颜色代码)
            String name = t.name == null || t.name.isEmpty() ? data.shopName : t.name;
            float textScale = 0.55f;
            Component n = QText.clip(name, this.font, (int) ((TAB_W - 4) / textScale));
            float nx = x + 3 + (TAB_W - this.font.width(n) * textScale) / 2f;
            float ny = ty + 17;
            g.pose().pushPose();
            g.pose().translate(nx, ny, 0);
            g.pose().scale(textScale, textScale, 1.0f);
            g.drawString(this.font, n, 0, 0, 0xFFFFFF);
            g.pose().popPose();
        }

        // 编辑模式的添加按钮参与同一滚动内容，不再与最后一个 tab 重叠。
        if (data.editing && editMode) {
            int ay = tabAddY();
            if (ay + 14 > ty0 && ay < endY) {
                boolean ah = tabInteractive && mouseX >= x + 3 && mouseX <= x + 3 + TAB_W
                        && mouseY >= Math.max(ay, ty0) && mouseY <= Math.min(ay + 14, endY);
                ShopTextures.button(g, x + 3, ay, TAB_W, 14, ah, true);
                g.drawCenteredString(this.font, "+", x + 3 + TAB_W / 2, ay + 2, 0xFFFFFF);
            }
        }
        ShopTextures.disableScissor(g);

        // 遮罩移入 render() 末尾绘制(renderTabMasks),确保压在图标/文字之上

        // tab 列表滚动条(可滚动时才显示)
        if (maxTabScroll() > 0) {
            int zoneY = ty0 + TAB_MASK_H;
            int zoneH = TAB_LIST_H - TAB_MASK_H * 2;
            int total = maxTabScroll() + zoneH;
            int knobH = Math.max(8, zoneH * zoneH / total);
            int knobY = zoneY + (zoneH - knobH) * tabScroll / maxTabScroll();
            ShopTextures.scrollTrack(g, x + TAB_BAR_W - 4, zoneY, zoneH);
            ShopTextures.scrollKnob(g, x + TAB_BAR_W - 5, knobY, knobH);
        }

        // 底部:余额(0.6 字号 + K/M 缩写;悬停显示完整余额 tooltip;支持 § 颜色)
        Currency cur = displayCurrency();
        if (cur != null) {
            double v = data.balances.getOrDefault(cur.id, 0D);
            String full = cur.displayName + " " + CurrencyRegistry.format(v);
            Component clipped = QText.clip(cur.displayName + " " + compactPrice(v), this.font,
                    (int) ((TAB_BAR_W - 4) / 0.6f));
            float bw = this.font.width(clipped) * 0.6f;
            float bx = x + (TAB_BAR_W - bw) / 2f;
            float by = y + GUI_H - 10;
            g.pose().pushPose();
            g.pose().translate(bx, by, 0);
            g.pose().scale(0.6f, 0.6f, 1.0f);
            g.drawString(this.font, clipped, 0, 0, 0xFFFFFF);
            g.pose().popPose();
            if (menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0
                    && mouseX >= bx - 2 && mouseX <= bx + bw + 2
                    && mouseY >= by - 8 && mouseY <= by + 5) {
                g.renderTooltip(this.font, List.of(QText.parse(full)), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    /** tab 列表最大滚动量:底部为遮罩留死区,保证最后一个子商店/添加按钮能完整显示 */
    private int maxTabScroll() {
        int count = visibleTabs.size();
        int tabsBottom = count == 0 ? 0 : (count - 1) * TAB_PITCH + TAB_H;
        int addBottom = data.editing && editMode ? count * TAB_PITCH + 14 : 0;
        return Math.max(0, Math.max(tabsBottom, addBottom) - (TAB_LIST_H - TAB_MASK_H));
    }

    /** 编辑模式“添加子商店”按钮位置：作为列表末尾的一项参与平滑滚动。 */
    private int tabAddY() {
        int ty0 = tabBarY() + 38;
        return Math.round(ty0 + visibleTabs.size() * TAB_PITCH - tabScrollAnim);
    }

    /**
     * tab 列表渐隐遮罩:在 render() 末尾(flushAll 之后)用 fillImmediate 立即绘制。
     * 此时主界面所有基础层内容(含物品图标/文字)已落屏,遮罩必然绘制在最上层。
     * 遮罩左右各缩进 2px(总宽减少 4px);仅在确有内容被遮挡时显示。
     */
    private void renderTabMasks(GuiGraphics g) {
        int x = tabBarX();
        int ty0 = tabBarY() + 38;
        int endY = ty0 + TAB_LIST_H;
        int mx = x + 2;
        int mw = TAB_BAR_W - 4;
        g.pose().pushPose();
        g.pose().translate(0, 0, TAB_MASK_LAYER_Z);
        if (tabScrollAnim > 0.05f) {
            ShopTextures.tabFadeTop(g, mx, ty0, mw);
        }
        if (tabScrollAnim < maxTabScroll() - 0.05f) {
            ShopTextures.tabFadeBottom(g, mx, endY - 10, mw);
        }
        g.flush();
        g.pose().popPose();
    }

    private int tabIndexAt(double mouseX, double mouseY) {
        int x = tabBarX();
        int y = tabBarY() + 38;
        int endY = y + TAB_LIST_H;
        if (mouseX < x + 3 || mouseX > x + 3 + TAB_W || mouseY < y || mouseY > endY) {
            return -1;
        }
        for (int i = 0; i < visibleTabs.size(); i++) {
            int tabY = Math.round(y + i * TAB_PITCH - tabScrollAnim);
            if (mouseY >= Math.max(tabY, y) && mouseY <= Math.min(tabY + TAB_H, endY)) {
                return i;
            }
        }
        return -1;
    }

    /** 切换到指定子商店 */
    private void switchTab(int i) {
        if (i < 0 || i >= visibleTabs.size() || i == activeTab) {
            return;
        }
        activeTab = i;
        data.activeTab = activeServerTabIndex();
        applyActiveTabEntries();
        scroll = 0;
        rowAnim = 0;
        scrollActiveTabIntoView();
    }

    /** 根据编辑模式生成可见子商店列表，并尽量保留当前服务端 tab。 */
    private void applyVisibleTabs(int preferredServerTab) {
        visibleTabs.clear();
        for (ClientTab tab : data.tabs) {
            if (!data.editing || editMode || tab.requirementsMet || tab.showWhenRequirementsNotMet) {
                visibleTabs.add(tab);
            }
        }
        activeTab = 0;
        for (int i = 0; i < visibleTabs.size(); i++) {
            if (visibleTabs.get(i).serverIndex == preferredServerTab) {
                activeTab = i;
                break;
            }
        }
        data.activeTab = activeServerTabIndex();
        tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll());
    }

    /** 当前可见 tab 对应的服务端真实序号。 */
    int activeServerTabIndex() {
        if (visibleTabs.isEmpty()) {
            return -1;
        }
        return serverTabIndex(activeTab);
    }

    private void scrollActiveTabIntoView() {
        int itemTop = activeTab * TAB_PITCH;
        int itemBottom = itemTop + TAB_H;
        if (itemTop < tabScroll + TAB_MASK_H) {
            tabScroll = itemTop - TAB_MASK_H;
        } else if (itemBottom > tabScroll + TAB_LIST_H - TAB_MASK_H) {
            tabScroll = itemBottom - (TAB_LIST_H - TAB_MASK_H);
        }
        tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll());
    }

    private void applyActiveTabEntries() {
        if (activeTab < 0 || activeTab >= visibleTabs.size()) {
            data.entries = new ArrayList<>();
            return;
        }
        List<ClientShopEntry> all = visibleTabs.get(activeTab).entries;
        if ((!data.editing || editMode) && !hasSearchQuery()) {
            data.entries = all;
            return;
        }
        List<ClientShopEntry> visibleEntries = new ArrayList<>();
        for (ClientShopEntry entry : all) {
            boolean normalVisible = editMode || ((entry.requirementsMet || entry.showWhenRequirementsNotMet)
                    && !entryLimitReached(entry));
            if (normalVisible && matchesSearch(entry)) {
                visibleEntries.add(entry);
            }
        }
        data.entries = visibleEntries;
    }

    private static boolean entryLimitReached(ClientShopEntry entry) {
        return (entry.globalLimit > 0 && entry.usedGlobal >= entry.globalLimit)
                || (entry.playerLimit > 0 && entry.usedPlayer >= entry.playerLimit);
    }

    private boolean hasSearchQuery() {
        return searchActive && !searchQuery.trim().isEmpty();
    }

    /** REI-like search: every whitespace-separated token must match the entry. */
    private boolean matchesSearch(ClientShopEntry entry) {
        if (!hasSearchQuery()) {
            return true;
        }
        String[] tokens = searchQuery.toLowerCase(Locale.ROOT).trim().split("\\s+");
        for (String token : tokens) {
            if (!matchesSearchToken(entry, token)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSearchToken(ClientShopEntry entry, String token) {
        if (token.isEmpty()) {
            return true;
        }
        if (token.startsWith("#")) {
            String wantedTag = token.substring(1);
            return !wantedTag.isEmpty() && searchStacks(entry).stream()
                    .anyMatch(stack -> stack.getTags()
                            .anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(wantedTag)));
        }
        if (token.startsWith("@")) {
            String wantedNamespace = token.substring(1);
            return !wantedNamespace.isEmpty() && searchStacks(entry).stream()
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .anyMatch(id -> id != null && id.getNamespace().toLowerCase(Locale.ROOT).contains(wantedNamespace));
        }
        // Prefer the transaction's custom name when present, while retaining
        // item name/ID matching as a fallback for familiar searches.
        if (entry.displayName != null && !entry.displayName.isBlank()
                && containsIgnoreCase(entry.displayName, token)) {
            return true;
        }
        return searchStacks(entry).stream().anyMatch(stack -> {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = id == null ? "" : id.toString();
            return containsIgnoreCase(itemId, token)
                    || containsIgnoreCase(stack.getHoverName().getString(), token);
        });
    }

    private static boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static List<ItemStack> searchStacks(ClientShopEntry entry) {
        List<ItemStack> stacks = new ArrayList<>();
        // Search exactly the item rendered in the trade slot. This keeps filtering
        // aligned with custom displayItem overrides and each trade type's icon rules.
        addSearchStack(stacks, cellIcon(entry));
        return stacks;
    }

    private static void addSearchStack(List<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    private int serverIndex(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= data.entries.size()) {
            return visibleIndex;
        }
        int serverIndex = data.entries.get(visibleIndex).serverIndex;
        return serverIndex >= 0 ? serverIndex : visibleIndex;
    }

    /** 可见子商店序号 → 服务端子商店序号(隐藏的子商店被过滤后序号会错位) */
    private int serverTabIndex(int visibleTab) {
        if (visibleTab < 0 || visibleTab >= visibleTabs.size()) {
            return visibleTab;
        }
        int serverIndex = visibleTabs.get(visibleTab).serverIndex;
        return serverIndex >= 0 ? serverIndex : visibleTab;
    }

    private void addTab() {
        QShopNetwork.sendToServer(new AddTabPacket(data.shopId,
                Component.translatable("qshop.tab.default_name", data.tabs.size() + 1).getString()));
    }

    void openTabEdit(int tabIndex) {
        Minecraft.getInstance().setScreen(new TabEditDialog(data, serverTabIndex(tabIndex)));
    }

    /** 打开商店信息编辑(名称/图标) */
    void openShopInfo() {
        Minecraft.getInstance().setScreen(new ShopInfoDialog(data));
    }

    /** 右键菜单：所有内容继承当前 PoseStack 的显式浮层深度。 */
    private void renderMenu(GuiGraphics g, int mouseX, int mouseY) {
        ShopTextures.menuPanel(g, menuX, menuY, MENU_W, MENU_H);
        Component[] labels = {
                Component.translatable("qshop.gui.remove"),
                Component.translatable("qshop.gui.copy"),
                Component.translatable("qshop.gui.move_up"),
                Component.translatable("qshop.gui.move_down")
        };
        for (int i = 0; i < labels.length; i++) {
            int bx = menuX + 4;
            int by = menuY + 4 + i * 18;
            boolean hover = ShopTextures.buttonHit(bx, by, MENU_W - 8, 14, mouseX, mouseY);
            ShopTextures.button(g, bx, by, MENU_W - 8, 14, hover, true);
            g.drawCenteredString(this.font, labels[i], bx + (MENU_W - 8) / 2, by + 3, 0xFFFFFFFF);
        }
        // 在弹出层的 PoseStack 恢复前提交文字和贴图。
        g.flush();
    }

    /** tab 右键菜单:编辑 / 删除 / 上移 / 下移(与条目菜单同款布局) */
    private void renderTabMenu(GuiGraphics g, int mouseX, int mouseY) {
        ShopTextures.menuPanel(g, tabMenuX, tabMenuY, MENU_W, MENU_H);
        Component[] labels = {
                Component.translatable("qshop.gui.edit_short"),
                Component.translatable("qshop.gui.remove"),
                Component.translatable("qshop.gui.move_up"),
                Component.translatable("qshop.gui.move_down")
        };
        for (int i = 0; i < labels.length; i++) {
            int bx = tabMenuX + 4;
            int by = tabMenuY + 4 + i * 18;
            boolean hover = ShopTextures.buttonHit(bx, by, MENU_W - 8, 14, mouseX, mouseY);
            ShopTextures.button(g, bx, by, MENU_W - 8, 14, hover, true);
            g.drawCenteredString(this.font, labels[i], bx + (MENU_W - 8) / 2, by + 3, 0xFFFFFFFF);
        }
        g.flush();
    }

    /** 允许滚动到最后一行完整对齐:最大滚动 = ceil(size/cols)*cols - 可见数 */
    private int maxScroll() {
        return Math.max(0, (int) Math.ceil(data.entries.size() / (float) cols) * cols - visible);
    }

    private void drawCell(GuiGraphics g, int index, int x, int y, boolean hover, int mouseX, int mouseY, boolean noCount) {
        ClientShopEntry e = data.entries.get(index);
        // 方形槽 1:1(不含下方价格),价格文字在槽下方
        int slot = cellH;
        ShopTextures.slot(g, x, y, slot, slot, hover, editMode, !editMode && !e.requirementsMet);
        ItemStack icon = cellIcon(e);
        if (!icon.isEmpty()) {
            // 图标在槽内居中
            int ix = x + (slot - 16) / 2;
            int iy = y + (slot - 16) / 2;
            g.renderItem(icon, ix, iy);
            if (!noCount) {
                g.renderItemDecorations(this.font, icon, ix, iy);
            }
        }
        // 购买/出售角标(左上角)
        ShopTextures.typeIcon(g, x + 1, y + 1, e.type);
        // 价格/数量:槽下方,固定宽度灰色背景条(材质)+ 缩小字号(0.6)白字(下移 2px 居中)
        // 左右各留 1px 边距(比槽宽少 2px),文本区域与之匹配
        int barY = y + slot + 1;
        int drawY = barY + 2;
        int barW = cellW - 2;
        int barX = x + 1;
        ShopTextures.priceBar(g, barX, barY, barW);
        // 交换与"物品+指令":数量 + 物品小图标(交换 = 玩家付出物,指令 = 代价物品);
        // 出售/购买/指令(货币):价格文字
        boolean iconMode = e.type == ShopEntryType.BARTER
                || (e.type == ShopEntryType.COMMAND && !e.item.isEmpty());
        if (iconMode) {
            ItemStack gs = e.type == ShopEntryType.BARTER
                    ? (!e.give.isEmpty() ? e.give.get(0) : (!e.receive.isEmpty() ? e.receive.get(0) : ItemStack.EMPTY))
                    : e.item;
            if (!gs.isEmpty()) {
                String count = gs.getCount() + "×";
                int textW = (int) (this.font.width(count) * 0.6f);
                int content = textW + 1 + 8;
                int contentX = barX + (barW - content) / 2;
                g.pose().pushPose();
                g.pose().translate(contentX, drawY, 0);
                g.pose().scale(0.6f, 0.6f, 1.0f);
                g.drawString(this.font, count, 0, 0, 0xFFFFFF);
                g.pose().popPose();
                g.pose().pushPose();
                g.pose().translate(contentX + textW + 1, drawY - 1, 0);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.renderItem(gs, 0, 0);
                g.pose().popPose();
            }
        } else {
            Component text = QText.clip(cellText(e, currencyName(e.currencyId)), this.font, cellW - 2);
            int textW = (int) (this.font.width(text) * 0.6f);
            int textX = barX + (barW - textW) / 2;
            g.pose().pushPose();
            g.pose().translate(textX, drawY, 0);
            g.pose().scale(0.6f, 0.6f, 1.0f);
            g.drawString(this.font, text, 0, 0, 0xFFFFFF);
            g.pose().popPose();
        }
        if (editMode) {
            // 删除按钮 8x8(触发面积与按钮一致,比槽右缘缩进 1px)
            int tx = x + cellW - 11;
            int ty = y + 2;
            boolean trashHover = hover && mouseX >= tx && mouseX <= tx + 8 && mouseY >= ty && mouseY <= ty + 8;
            ShopTextures.trashIcon(g, tx, ty, trashHover);
        }
    }

    /** 由鼠标坐标计算悬浮/点击的条目序号,-1 表示不在网格内(向下取整,避免网格上方误判为第一行) */
    private int indexAt(double mouseX, double mouseY) {
        int gx = gridX();
        int gy = gridY();
        if (mouseY < gy || mouseY >= gy + gridViewportHeight()) {
            return -1;
        }
        int baseRow = (int) Math.floor(rowAnim);
        float frac = rowAnim - baseRow;
        int c = (int) Math.floor((mouseX - gx) / stepX);
        int r = (int) Math.floor((mouseY - gy + frac * stepY) / stepY);
        int index = (baseRow + r) * cols + c;
        if (c < 0 || c >= cols || r < 0 || r >= rows || index < 0 || index >= data.entries.size()) {
            return -1;
        }
        return index;
    }

    /** 鼠标是否在网格下方的空白区域(面板内):拖到这里 = 移到末尾 */
    private boolean belowGrid(double mouseX, double mouseY) {
        int gx = gridX();
        int gy = gridY();
        int c = (int) Math.floor((mouseX - gx) / stepX);
        return c >= 0 && c < cols && mouseY >= gy + gridViewportHeight() && mouseY <= panelY() + GUI_H;
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        // 浮层在按下阶段关闭后，同一次鼠标序列仍由浮层消费，不能落到底层条目。
        if (overlayPointerCapture && menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0) {
            return true;
        }
        // 条目右键菜单打开时:只响应菜单按钮(透明度命中检测);菜单内空白不关闭
        if (menuIndex >= 0) {
            overlayPointerCapture = true;
            // 清除可能卡住的拖拽状态(幽灵会盖在菜单上)
            pressedIndex = -1;
            dragActive = false;
            int bx = menuX + 4;
            int by = menuY + 4;
            for (int i = 0; i < 4; i++) {
                int bxx = bx;
                int byy = by + i * 18;
                if (ShopTextures.buttonHit(bxx, byy, MENU_W - 8, 14, mouseX, mouseY)) {
                    menuAction(i);
                    closeMenu();
                    return true;
                }
            }
            boolean inside = mouseX >= menuX && mouseX <= menuX + MENU_W
                    && mouseY >= menuY && mouseY <= menuY + MENU_H;
            if (inside) {
                return true; // 菜单面板内空白:保持打开
            }
            closeMenu();
            return true;
        }
        // tab 右键菜单打开时:行为同条目菜单(编辑/删除/上移/下移)
        if (tabMenuIndex >= 0) {
            overlayPointerCapture = true;
            pressedIndex = -1;
            dragActive = false;
            int bx = tabMenuX + 4;
            int by = tabMenuY + 4;
            for (int i = 0; i < 4; i++) {
                int bxx = bx;
                int byy = by + i * 18;
                if (ShopTextures.buttonHit(bxx, byy, MENU_W - 8, 14, mouseX, mouseY)) {
                    tabMenuAction(i);
                    closeTabMenu();
                    return true;
                }
            }
            boolean inside = mouseX >= tabMenuX && mouseX <= tabMenuX + MENU_W
                    && mouseY >= tabMenuY && mouseY <= tabMenuY + MENU_H;
            if (inside) {
                return true;
            }
            closeTabMenu();
            return true;
        }
        // 交易悬浮窗打开时:只响应交易窗内的控件;面板内空白不关闭
        if (tradeIndex >= 0) {
            overlayPointerCapture = true;
            pressedIndex = -1;
            dragActive = false;
            int px = left + (panelWidth() - TRADE_W) / 2;
            int py = top + 24 + (GUI_H - 24 - TRADE_H) / 2;
            boolean inside = mouseX >= px && mouseX <= px + TRADE_W && mouseY >= py && mouseY <= py + TRADE_H;
            // 输入框优先(显式聚焦)
            if (button == 0 && tradeUnitsBox != null
                    && mouseX >= tradeUnitsBox.getX() && mouseX <= tradeUnitsBox.getX() + tradeUnitsBox.getWidth()
                    && mouseY >= tradeUnitsBox.getY() && mouseY <= tradeUnitsBox.getY() + tradeUnitsBox.getHeight()) {
                tradeUnitsBox.setFocused(true);
                tradeUnitsBox.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            for (var w : tradeWidgets) {
                if (w != tradeUnitsBox && w.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (inside) {
                return true; // 交易窗面板内空白:保持打开
            }
            closeTrade();
            return true;
        }
        // 搜索框使用自定义事件分发:先明确切换焦点,避免点击被主界面网格消费。
        if (searchBox != null) {
            searchBox.setFocused(false);
            if (searchBox.mouseClicked(mouseX, mouseY, button)) {
                searchBox.setFocused(true);
                return true;
            }
        }
        // 左侧 tab 栏(左键永远切换子商店;编辑模式右键 = tab 菜单)
        int tabX = tabBarX();
        int tabY = tabBarY();
        if (mouseX >= tabX && mouseX <= tabX + TAB_BAR_W && mouseY >= tabY && mouseY <= tabY + GUI_H) {
            // 编辑模式:左键/右键顶部商店名称/图标区域 = 编辑商店信息(名字/图标/货币)
            // 与子商店不同:子商店左键要切换所以只能右键出菜单;商店标题无切换语义,左键直接编辑
            if (editMode && (button == 0 || button == 1) && mouseY >= tabY + 3 && mouseY < tabY + 38) {
                openShopInfo();
                return true;
            }
            int ty0 = tabY + 38;
            int endY = ty0 + TAB_LIST_H;
            for (int i = 0; i < visibleTabs.size(); i++) {
                int ty = Math.round(ty0 + i * TAB_PITCH - tabScrollAnim);
                if (ty + TAB_H <= ty0 || ty >= endY) {
                    continue;
                }
                if (mouseX >= tabX + 3 && mouseX <= tabX + 3 + TAB_W
                        && mouseY >= Math.max(ty, ty0) && mouseY <= Math.min(ty + TAB_H, endY)) {
                    if (button == 1 && editMode) {
                        // 编辑模式:右键 = tab 菜单(编辑/删除/上移/下移)
                        openTabMenu(i, (int) mouseX, (int) mouseY);
                    } else if (button == 0 || button == 1) {
                        ClientTab tab = visibleTabs.get(i);
                        if (!editMode && !tab.requirementsMet) {
                            FtbQuestClient.openFirstQuest(tab.requiredQuests);
                        } else {
                            switchTab(i);
                        }
                    }
                    return true;
                }
            }
            // 编辑模式:添加子商店按钮
            if (editMode) {
                int ay = tabAddY();
                if (mouseX >= tabX + 3 && mouseX <= tabX + 3 + TAB_W
                        && mouseY >= Math.max(ay, ty0) && mouseY <= Math.min(ay + 14, endY)
                        && ay + 14 > ty0 && ay < endY) {
                    addTab();
                    return true;
                }
            }
            return true;
        }
        // 按钮优先(避免与滚动中条目/删除按钮重叠时误触)
        for (var w : this.children()) {
            if (w.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            int gx = gridX();
            int gy = gridY();
            int baseRow = (int) Math.floor(rowAnim);
            float frac = rowAnim - baseRow;
            int c = (int) Math.floor((mouseX - gx) / stepX);
            int r = (int) Math.floor((mouseY - gy + frac * stepY) / stepY);
            int cellX = gx + c * stepX;
            int cellY = gy + (int) (r * stepY - frac * stepY);
            // 编辑模式:右键 = 上下文菜单(删除/复制/上移/下移)
            if (editMode && button == 1) {
                openMenu(index, (int) mouseX, (int) mouseY);
                return true;
            }
            // 编辑模式:格子右上角垃圾桶(8x8,左移 1px)= 删除条目
            int trashX = cellX + cellW - 11;
            int trashY = cellY + 2;
            if (editMode && mouseX >= trashX && mouseX <= trashX + 8 && mouseY >= trashY && mouseY <= trashY + 8) {
                removeEntry(index);
                return true;
            }
            if (editMode) {
                // 记录按下位置,等待拖动(交换)或松开(编辑)
                pressedIndex = index;
                dragActive = false;
                return true;
            }
            openTrade(index);
            return true;
        }
        return super.mouseClickedContent(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseDraggedContent(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (overlayPointerCapture && menuIndex < 0 && tabMenuIndex < 0 && tradeIndex < 0) {
            return true;
        }
        // 菜单打开时吞掉一切拖动;交易窗打开时只响应窗内控件
        if (menuIndex >= 0 || tabMenuIndex >= 0) {
            return true;
        }
        if (tradeIndex >= 0) {
            for (var w : tradeWidgets) {
                // EditBox 独占拖拽(选择文本),会吞掉滑块的拖动,这里跳过它
                if (w == tradeUnitsBox) {
                    continue;
                }
                if (w.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true;
                }
            }
            return true;
        }
        if (editMode && pressedIndex >= 0) {
            dragActive = true;
            return true;
        }
        return super.mouseDraggedContent(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        if (overlayPointerCapture) {
            overlayPointerCapture = false;
            pressedIndex = -1;
            dragActive = false;
            if (tradeIndex >= 0) {
                for (var w : tradeWidgets) {
                    w.mouseReleased(mouseX, mouseY, button);
                }
            }
            return true;
        }
        // 菜单打开时吞掉一切释放,并清除拖拽状态;交易窗打开时只响应窗内控件
        if (menuIndex >= 0 || tabMenuIndex >= 0) {
            pressedIndex = -1;
            dragActive = false;
            return true;
        }
        if (tradeIndex >= 0) {
            pressedIndex = -1;
            dragActive = false;
            for (var w : tradeWidgets) {
                if (w.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        if (pressedIndex >= 0) {
            int index = pressedIndex;
            pressedIndex = -1;
            if (dragActive) {
                dragActive = false;
                int target = indexAt(mouseX, mouseY);
                if (target >= 0 && target != index) {
                    // 拖到某个格子上 = 交换位置
                    swapEntries(index, target);
                } else if (target < 0 && belowGrid(mouseX, mouseY)) {
                    // 拖到网格下方空白 = 移到末尾
                    reorderInsert(index, data.entries.size() - 1);
                }
                return true;
            }
            // 未拖动 = 单击:进入编辑界面
            openEdit(index);
            return true;
        }
        return super.mouseReleasedContent(mouseX, mouseY, button);
    }

    /** 本地预览交换并通知服务端 */
    private void swapEntries(int a, int b) {
        var entries = data.entries;
        if (a < 0 || a >= entries.size() || b < 0 || b >= entries.size() || a == b) {
            return;
        }
        int serverA = serverIndex(a);
        int serverB = serverIndex(b);
        ClientShopEntry tmp = entries.get(a);
        entries.set(a, entries.get(b));
        entries.set(b, tmp);
        reindexEditedEntries();
        QShopNetwork.sendToServer(new SwapEntryPacket(data.shopId, serverTabIndex(activeTab), serverA, serverB));
    }

    /**
     * 本地预览排序并通知服务端:把 from 条目移动到"原序号 to 处"(插到原 to 之前;to==size 时移到末尾)。
     */
    private void reorderInsert(int from, int to) {
        var entries = data.entries;
        if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size() || from == to) {
            return;
        }
        if (to == from + 1) {
            return; // 位置不变(插到下一个之前 = 原位置)
        }
        int serverFrom = serverIndex(from);
        int serverTo = serverIndex(to);
        ClientShopEntry entry = entries.remove(from);
        entries.add(to, entry);
        reindexEditedEntries();
        QShopNetwork.sendToServer(new ReorderEntryPacket(data.shopId, serverTabIndex(activeTab), serverFrom, serverTo));
    }

    private void reindexEditedEntries() {
        if (!data.editing || !editMode) {
            return;
        }
        for (int i = 0; i < data.entries.size(); i++) {
            data.entries.get(i).serverIndex = i;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F8 && ShopLayoutDebug.isConfiguredEnabled()) {
            ShopLayoutDebug.toggle();
            layout();
            return true;
        }
        if (ShopLayoutDebug.isEnabled()
                && !(tradeUnitsBox != null && tradeUnitsBox.isFocused())
                && !(searchBox != null && searchBox.isFocused())) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                ShopLayoutDebug.selectNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            int step = (modifiers & GLFW.GLFW_MOD_ALT) != 0 ? 1 : 5;
            int dx = 0;
            int dy = 0;
            if (keyCode == GLFW.GLFW_KEY_LEFT) dx = -step;
            if (keyCode == GLFW.GLFW_KEY_RIGHT) dx = step;
            if (keyCode == GLFW.GLFW_KEY_UP) dy = -step;
            if (keyCode == GLFW.GLFW_KEY_DOWN) dy = step;
            if (dx != 0 || dy != 0) {
                ShopLayoutDebug.moveSelected(dx, dy);
                // Widgets hold their bounds, so rebuild them immediately after
                // changing an offset instead of waiting for the next screen init.
                layout();
                return true;
            }
        }
        if (menuIndex >= 0 && keyCode == 256) {
            closeMenu();
            return true;
        }
        if (tabMenuIndex >= 0 && keyCode == 256) {
            closeTabMenu();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()
                && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (tradeIndex >= 0) {
            if (keyCode == 256) {
                closeTrade();
                return true;
            }
            if (tradeUnitsBox != null && tradeUnitsBox.isFocused()
                    && tradeUnitsBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (QShopScreenInput.handleInventoryKey(this, keyCode, scanCode, hasFocusedInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean hasFocusedInput() {
        return (searchBox != null && searchBox.isFocused())
                || (tradeUnitsBox != null && tradeUnitsBox.isFocused());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()
                && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (tradeIndex >= 0 && tradeUnitsBox != null && tradeUnitsBox.isFocused()
                && tradeUnitsBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean mouseScrolledContent(double mouseX, double mouseY, double delta) {
        if (menuIndex >= 0 || tabMenuIndex >= 0 || tradeIndex >= 0) {
            return true; // 菜单/交易窗打开时锁定商店滚动
        }
        // 鼠标在左侧 tab 栏上时:滚动子商店列表
        int tabX = tabBarX();
        if (mouseX >= tabX && mouseX <= tabX + TAB_BAR_W && mouseY >= tabBarY() && mouseY <= tabBarY() + GUI_H) {
            int ns = Mth.clamp(tabScroll - wheelDirection(delta) * TAB_PITCH, 0, maxTabScroll());
            if (ns != tabScroll) {
                tabScroll = ns;
            }
            return true;
        }
        int ns = Mth.clamp(scroll - wheelDirection(delta) * cols, 0, maxScroll());
        if (ns != scroll) {
            scroll = ns;
            return true;
        }
        return super.mouseScrolledContent(mouseX, mouseY, delta);
    }

    private static int wheelDirection(double delta) {
        return delta > 0 ? 1 : delta < 0 ? -1 : 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 打开交易悬浮窗(小窗口,不替换主商店界面) */
    void openTrade(int entryIndex) {
        if (tradeIndex >= 0) {
            closeTrade();
        }
        if (entryIndex < 0 || entryIndex >= data.entries.size()) {
            return;
        }
        ClientShopEntry e = data.entries.get(entryIndex);
        if (!editMode && !e.requirementsMet) {
            FtbQuestClient.openFirstQuest(e.requiredQuests);
            return;
        }
        // 清除可能卡住的拖拽状态(幽灵会盖在交易窗上)
        pressedIndex = -1;
        dragActive = false;
        tradeIndex = entryIndex;
        tradeMaxUnits = computeTradeMaxUnits(e);
        int px = left + (panelWidth() - TRADE_W) / 2;
        int py = top + 24 + (GUI_H - 24 - TRADE_H) / 2;

        tradeUnitsBox = new EditBox(this.font, px + 47, py + 63, 56, 14, Component.literal(""));
        tradeUnitsBox.setMaxLength(7);
        tradeUnitsBox.setFilter(s -> s.matches("\\d{0,7}"));
        tradeUnitsBox.setBordered(false);
        tradeUnitsBox.setValue("");
        tradeUnitsBox.setResponder(s -> onTradeBoxChanged());
        addTradeWidget(tradeUnitsBox);

        tradeSlider = new QSlider(px + 8, py + 77, TRADE_W - 16, 12, this::onTradeSliderChanged);
        tradeSlider.setValueInt(1, Math.max(1, tradeMaxUnits));
        addTradeWidget(tradeSlider);

        addTradeWidget(new QButton(px + 8, py + TRADE_H - 21, 66, 16,
                Component.translatable("qshop.gui.trade"), b -> confirmTrade()));
        addTradeWidget(new QButton(px + TRADE_W - 74, py + TRADE_H - 21, 66, 16,
                Component.translatable("qshop.gui.cancel"), b -> closeTrade()));
    }

    private void addTradeWidget(AbstractWidget w) {
        tradeWidgets.add(w);
        addRenderableWidget(w);
    }

    private void closeTrade() {
        tradeIndex = -1;
        for (var w : tradeWidgets) {
            removeWidget(w);
        }
        tradeWidgets.clear();
        tradeUnitsBox = null;
        tradeSlider = null;
    }

    /** 服务端刷新交易数据时调用，确保旧交易窗口不能继续提交旧索引。 */
    void interruptForDataRefresh() {
        closeTrade();
        closeMenu();
        closeTabMenu();
        pressedIndex = -1;
        dragActive = false;
        overlayPointerCapture = false;
    }

    private void onTradeBoxChanged() {
        if (tradeSyncing || tradeUnitsBox == null) {
            return;
        }
        int input = parseTradeInput(tradeUnitsBox.getValue());
        int effective = tradeMaxUnits > 0 ? Math.min(input, tradeMaxUnits) : input;
        tradeSyncing = true;
        tradeSlider.setValueInt(effective, Math.max(1, tradeMaxUnits));
        tradeSyncing = false;
    }

    private void onTradeSliderChanged() {
        if (tradeSyncing || tradeSlider == null) {
            return;
        }
        int v = Math.max(1, tradeSlider.getValueInt(Math.max(1, tradeMaxUnits)));
        tradeSyncing = true;
        tradeUnitsBox.setValue(String.valueOf(v));
        tradeSyncing = false;
    }

    private void confirmTrade() {
        int units = parseTradeInput(tradeUnitsBox.getValue());
        QShopNetwork.sendToServer(new TradePacket(data.shopId, serverTabIndex(activeTab), serverIndex(tradeIndex), units));
        closeTrade();
    }

    private static int parseTradeInput(String s) {
        try {
            return Math.max(1, Integer.parseInt(s.trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    /** 客户端预估最大可交易单位数(限额/余额/背包库存) */
    private int computeTradeMaxUnits(ClientShopEntry e) {
        int itemsPerUnit = e.type == ShopEntryType.BARTER
                ? totalTradeCount(e.receive)
                : (e.type == ShopEntryType.COMMAND ? 1 : e.item.getCount());
        if (itemsPerUnit <= 0) {
            return 0;
        }
        int max = Integer.MAX_VALUE;
        if (e.globalLimit > 0) {
            max = Math.min(max, Math.max(0, e.globalLimit - e.usedGlobal));
        }
        if (e.playerLimit > 0) {
            max = Math.min(max, Math.max(0, e.playerLimit - e.usedPlayer));
        }
        double balance = data.balances.getOrDefault(e.currencyId, 0D);
        switch (e.type) {
            case BUY, COMMAND -> {
                // COMMAND 带物品代价时,按物品库存折算(同 SELL)
                if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
                    int have = countTradeItems(e.item);
                    max = Math.min(max, have / e.item.getCount());
                }
                if (e.price > 0) {
                    // 用 long 计算并夹到 int 上限,避免大余额时 (int) 强转溢出(变成负数/错误上限)
                    long byBalance = (long) (balance / e.price);
                    max = (int) Math.min(max, Math.min(byBalance, Integer.MAX_VALUE));
                }
            }
            case SELL -> {
                int have = countTradeItems(e.item);
                max = Math.min(max, have / e.item.getCount());
            }
            case BARTER -> {
                // 以物换物:不读取售价,上限只看付出物品库存
                for (ItemStack g : e.give) {
                    max = Math.min(max, countTradeItems(g) / g.getCount());
                }
            }
        }
        return Math.max(0, max);
    }

    /** 无法交易时的具体原因(限购/货币不足/物品不足);与 computeTradeMaxUnits 的判定顺序一致 */
    private Component tradeBlockReason(ClientShopEntry e) {
        int itemsPerUnit = e.type == ShopEntryType.BARTER
                ? totalTradeCount(e.receive)
                : (e.type == ShopEntryType.COMMAND ? 1 : e.item.getCount());
        if (itemsPerUnit <= 0) {
            return Component.translatable("qshop.gui.cannot_trade");
        }
        if ((e.globalLimit > 0 && e.usedGlobal >= e.globalLimit)
                || (e.playerLimit > 0 && e.usedPlayer >= e.playerLimit)) {
            return Component.translatable("qshop.msg.limit_reached");
        }
        switch (e.type) {
            case BUY, COMMAND -> {
                // COMMAND 带物品代价时,按物品库存折算(同 SELL)
                if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
                    if (countTradeItems(e.item) < e.item.getCount()) {
                        return Component.translatable("qshop.msg.not_enough_items");
                    }
                }
                if (e.price > 0) {
                    double balance = data.balances.getOrDefault(e.currencyId, 0D);
                    if (balance < e.price) {
                        return Component.translatable("qshop.msg.not_enough_currency");
                    }
                }
            }
            case SELL -> {
                if (countTradeItems(e.item) < e.item.getCount()) {
                    return Component.translatable("qshop.msg.not_enough_items");
                }
            }
            case BARTER -> {
                for (ItemStack g : e.give) {
                    if (countTradeItems(g) < g.getCount()) {
                        return Component.translatable("qshop.msg.not_enough_items");
                    }
                }
            }
        }
        return Component.translatable("qshop.gui.cannot_trade");
    }

    private static int countTradeItems(ItemStack target) {
        Player player = Minecraft.getInstance().player;
        if (player == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(s, target)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private static int totalTradeCount(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack s : stacks) {
            total += s.getCount();
        }
        return total;
    }

    /** 交易悬浮窗内容(面板 + 居中信息 + 数量/合计) */
    private void drawTradePanel(GuiGraphics g) {
        ClientShopEntry e = data.entries.get(tradeIndex);
        int px = left + (panelWidth() - TRADE_W) / 2;
        int py = top + 24 + (GUI_H - 24 - TRADE_H) / 2;
        ShopTextures.tradePanel(g, px, py, TRADE_W, TRADE_H);

        Component typeLabel = Component.translatable("qshop.type." + e.type.name());
        int typeX = px + TRADE_W - this.font.width(typeLabel) - 6;

        // 物品 + 名称(按类型标签的实际宽度动态避让;支持 § 颜色代码)
        ItemStack icon = cellIcon(e);
        if (!icon.isEmpty()) {
            g.renderItem(icon, px + 6, py + 4);
            int nameWidth = Math.max(0, typeX - (px + 24) - 4);
            Component nameC = QText.parse(e.displayName.isEmpty() ? icon.getHoverName().getString() : e.displayName);
            g.drawString(this.font, QText.clip(nameC, this.font, nameWidth), px + 24, py + 6, 0xFFFFFF);
        }
        // 类型标签(出售/购买/交换/指令)
        g.drawString(this.font, typeLabel, typeX, py + 6, 0xFFAA00);
        int cx = px + TRADE_W / 2;

        // 价格行(以物换物不显示价格,也不显示"免费")
        if (e.type != ShopEntryType.BARTER) {
            StringBuilder priceText = new StringBuilder();
            if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
                priceText.append(e.item.getCount()).append("× ").append(e.item.getHoverName().getString());
            }
            if (e.price > 0) {
                if (priceText.length() > 0) {
                    priceText.append(" + ");
                }
                // 显示完整价格(不缩写 K/M/B)
                priceText.append(CurrencyRegistry.format(e.price)).append(" ").append(currencyName(e.currencyId));
            }
            String priceLine = priceText.length() == 0
                    ? Component.translatable("qshop.gui.free").getString() : priceText.toString();
            drawCenteredClipped(g, QText.parse(priceLine), cx, py + 25, TRADE_W - 12, 0xFFFFFF);
        } else {
            // 以物换物:显示需要的物品(数量× 玩家付出物)
            ItemStack gs = !e.give.isEmpty() ? e.give.get(0) : ItemStack.EMPTY;
            String need = Component.translatable("qshop.gui.need").getString() + ": "
                    + (gs.isEmpty() ? "?" : gs.getCount() + "× " + gs.getHoverName().getString());
            drawCenteredClipped(g, QText.parse(need), cx, py + 25, TRADE_W - 12, 0xFFFFFF);
        }

        // 是否限购
        StringBuilder limit = new StringBuilder();
        if (e.globalLimit > 0 || e.playerLimit > 0) {
            limit.append(Component.translatable("qshop.gui.is_limited").getString());
            if (e.globalLimit > 0) {
                limit.append(" ").append(Component.translatable("qshop.gui.tip_global").getString())
                        .append(" ").append(e.usedGlobal).append("/").append(e.globalLimit);
            }
            if (e.playerLimit > 0) {
                limit.append(" ").append(Component.translatable("qshop.gui.tip_player").getString())
                        .append(" ").append(e.usedPlayer).append("/").append(e.playerLimit);
            }
        } else {
            limit.append(Component.translatable("qshop.gui.not_limited").getString());
        }
        drawCenteredClipped(g, QText.parse(limit.toString()), cx, py + 37, TRADE_W - 16, 0xFFFFFF);

        // 最大交易次数(无法交易时区分具体原因:限购/货币不足/物品不足)
        if (tradeMaxUnits <= 0) {
            drawCenteredClipped(g, tradeBlockReason(e), cx, py + 49, TRADE_W - 12, 0xFFFFFF);
        } else {
            drawCenteredClipped(g,
                    Component.translatable("qshop.gui.max_trade_times").append(": " + tradeMaxUnits),
                    cx, py + 49, TRADE_W - 12, 0xFFFFFF);
        }

        // 数量输入框(居中;空值时显示"数量"占位提示)
        ShopTextures.input(g, px + 45, py + 62, 60, 12, tradeUnitsBox.isFocused());
        String boxVal = tradeUnitsBox.getValue();
        if (boxVal == null || boxVal.isEmpty()) {
            drawCenteredClipped(g, Component.translatable("qshop.gui.units_hint"),
                    cx, py + 63, 60, 0x808080);
        }

        // 合计(以物换物显示"N× 获得物";物品+指令显示"总需求物品量× 付出物";其余显示完整总价,不缩写 K/M/B)
        int input = parseTradeInput(tradeUnitsBox.getValue());
        int units = tradeMaxUnits > 0 ? Math.min(input, tradeMaxUnits) : input;
        if (e.type == ShopEntryType.BARTER) {
            ItemStack r = !e.receive.isEmpty() ? e.receive.get(0) : ItemStack.EMPTY;
            Component t = QText.parse(units + "× " + (r.isEmpty() ? "?" : r.getHoverName().getString()));
            drawCenteredClipped(g, t, cx, py + 93, TRADE_W - 12, 0xFFFFFF);
        } else if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
            // 物品+指令:每次交易消耗一件物品(数量 e.item.getCount()),合计显示总需求物品量
            ItemStack cost = e.item;
            Component t = QText.parse((units * cost.getCount()) + "× " + cost.getHoverName().getString());
            drawCenteredClipped(g, t, cx, py + 93, TRADE_W - 12, 0xFFFFFF);
        } else {
            drawCenteredClipped(g, Component.translatable("qshop.gui.total_price",
                            CurrencyRegistry.format(e.price * units), currencyName(e.currencyId)),
                    cx, py + 93, TRADE_W - 12, 0xFFFFFF);
        }
    }

    private void drawCenteredClipped(GuiGraphics g, Component text, int centerX, int y, int maxWidth, int color) {
        g.drawCenteredString(this.font, QText.clip(text, this.font, maxWidth), centerX, y, color);
    }

    void openEdit(int entryIndex) {
        Minecraft.getInstance().setScreen(new ShopEditDialog(data, entryIndex, scroll, editMode));
    }

    void openAdd() {
        Minecraft.getInstance().setScreen(new ShopEditDialog(data, scroll, editMode));
    }

    /** 打开编辑模式右键菜单(悬浮在商店界面上,不替换屏幕) */
    void openMenu(int entryIndex, int mx, int my) {
        // 清除可能卡住的拖拽状态(幽灵会盖在菜单上)
        pressedIndex = -1;
        dragActive = false;
        closeTabMenu();
        menuIndex = entryIndex;
        menuX = Mth.clamp(mx, 2, Math.max(2, width - MENU_W - 2));
        menuY = Mth.clamp(my, 2, Math.max(2, height - MENU_H - 2));
    }

    /** 打开 tab 右键菜单(编辑/删除/上移/下移) */
    private void openTabMenu(int tabIndex, int mx, int my) {
        pressedIndex = -1;
        dragActive = false;
        closeMenu();
        tabMenuIndex = tabIndex;
        tabMenuX = Mth.clamp(mx, 2, Math.max(2, width - MENU_W - 2));
        tabMenuY = Mth.clamp(my, 2, Math.max(2, height - MENU_H - 2));
    }

    /** tab 菜单按钮动作:0 编辑 / 1 删除 / 2 上移 / 3 下移 */
    private void tabMenuAction(int i) {
        int serverTab = serverTabIndex(tabMenuIndex);
        switch (i) {
            case 0 -> openTabEdit(tabMenuIndex);
            case 1 -> QShopNetwork.sendToServer(new RemoveTabPacket(data.shopId, serverTab));
            case 2 -> QShopNetwork.sendToServer(new MoveTabPacket(data.shopId, serverTab, -1));
            case 3 -> QShopNetwork.sendToServer(new MoveTabPacket(data.shopId, serverTab, 1));
            default -> {
            }
        }
    }

    private void closeTabMenu() {
        tabMenuIndex = -1;
    }

    private void closeMenu() {
        menuIndex = -1;
    }

    /** 菜单按钮动作:0 删除 / 1 复制 / 2 上移 / 3 下移 */
    private void menuAction(int i) {
        switch (i) {
            case 0 -> removeEntry(menuIndex);
            case 1 -> QShopNetwork.sendToServer(new CopyEntryPacket(data.shopId, serverTabIndex(activeTab), serverIndex(menuIndex)));
            case 2 -> {
                int source = serverIndex(menuIndex);
                if (source > 0) {
                    QShopNetwork.sendToServer(new ReorderEntryPacket(data.shopId, serverTabIndex(activeTab), source, source - 1));
                }
            }
            case 3 -> {
                int source = serverIndex(menuIndex);
                if (source < visibleTabs.get(activeTab).entries.size() - 1) {
                    QShopNetwork.sendToServer(new ReorderEntryPacket(data.shopId, serverTabIndex(activeTab), source, source + 1));
                }
            }
            default -> {
            }
        }
    }

    void removeEntry(int entryIndex) {
        QShopNetwork.sendToServer(new RemoveEntryPacket(data.shopId, serverTabIndex(activeTab), serverIndex(entryIndex)));
    }

    void onWalletSync(java.util.Map<String, Double> balances) {
        data.balances.clear();
        data.balances.putAll(balances);
    }

    // ---------------- 渲染辅助 ----------------

    private void renderCellTooltip(GuiGraphics g, int index, int mouseX, int mouseY) {
        ClientShopEntry e = data.entries.get(index);
        ItemStack icon = cellIcon(e);
        boolean custom = (e.displayName != null && !e.displayName.isEmpty())
                || (e.description != null && !e.description.isEmpty());
        if (!custom) {
            if (!icon.isEmpty()) {
                g.renderTooltip(this.font, icon, mouseX, mouseY);
            }
            return;
        }
        List<Component> lines = new ArrayList<>();
        String title = e.displayName != null && !e.displayName.isEmpty()
                ? e.displayName : icon.getHoverName().getString();
        lines.add(QText.hasCodes(title)
                ? QText.parse(title)
                : Component.literal(title).withStyle(s -> s.withColor(0xFFFFFF)));
        if (e.description != null && !e.description.isEmpty()) {
            lines.add(QText.hasCodes(e.description)
                    ? QText.parse(e.description)
                    : Component.literal(e.description).withStyle(s -> s.withColor(0xFFFFFF)));
        }
        switch (e.type) {
            case BUY, COMMAND -> {
                if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
                    // 玩家提供物品+商店提供指令:显示需求物品而不是售价(与价格条 tooltip 一致)
                    lines.add(Component.translatable("qshop.gui.need").append(": ")
                            .append(Component.literal(e.item.getCount() + "× " + e.item.getHoverName().getString())
                                    .withStyle(s -> s.withColor(0x55FF55))));
                } else {
                    lines.add(Component.translatable("qshop.gui.buy_price").append(": ")
                            .append(Component.literal(CurrencyRegistry.format(e.price) + " " + currencyName(e.currencyId))
                                    .withStyle(s -> s.withColor(0x55FF55))));
                }
            }
            case SELL -> lines.add(Component.translatable("qshop.gui.sell_price").append(": ")
                    .append(Component.literal(CurrencyRegistry.format(e.price) + " " + currencyName(e.currencyId))
                            .withStyle(s -> s.withColor(0x55FF55))));
            case BARTER -> {
                StringBuilder sb = new StringBuilder();
                for (ItemStack s : e.give) {
                    if (sb.length() > 0) {
                        sb.append(" + ");
                    }
                    sb.append(s.getCount()).append("×").append(s.getHoverName().getString());
                }
                sb.append(" ⇄ ");
                boolean first = true;
                for (ItemStack s : e.receive) {
                    if (!first) {
                        sb.append(" + ");
                    }
                    sb.append(s.getCount()).append("×").append(s.getHoverName().getString());
                    first = false;
                }
                lines.add(Component.literal(sb.toString()).withStyle(s -> s.withColor(0x55FF55)));
            }
        }
        if (e.globalLimit > 0) {
            lines.add(Component.translatable("qshop.gui.tip_global").append(": " + e.usedGlobal + " / " + e.globalLimit)
                    .withStyle(s -> s.withColor(0xFFFF55)));
        }
        if (e.playerLimit > 0) {
            lines.add(Component.translatable("qshop.gui.tip_player").append(": " + e.usedPlayer + " / " + e.playerLimit)
                    .withStyle(s -> s.withColor(0xFFFF55)));
        }
        if (!e.requiredQuests.isEmpty()) {
            lines.add(Component.translatable("qshop.msg.req_quest").append(": "
                            + String.join(", ", FtbQuestClient.questNames(e.requiredQuests)))
                    .withStyle(s -> s.withColor(0xFFAA55)));
            if (!editMode && !e.requirementsMet) {
                lines.add(Component.translatable("qshop.msg.quest_click_hint")
                        .withStyle(s -> s.withColor(0xFFAA55)));
            }
        }
        if (!e.requiredStages.isEmpty()) {
            lines.add(Component.translatable("qshop.msg.req_stage").append(": " + stageRequirementLabels(e))
                    .withStyle(s -> s.withColor(0xFFAA55)));
        }
        if (e.type == ShopEntryType.COMMAND && !e.commands.isEmpty()) {
            lines.add(Component.translatable("qshop.gui.tip_cmds").append(": ")
                    .append(Component.translatable("qshop.gui.tip_cmd_count", e.commands.size()))
                    .withStyle(s -> s.withColor(0xAAAAAA)));
        }
        g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** 阶段描述按 requiredStages 的索引对应;缺失或为空时显示阶段原文。 */
    private static String stageRequirementLabels(ClientShopEntry entry) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entry.requiredStages.size(); i++) {
            String stage = entry.requiredStages.get(i);
            String description = i < entry.requiredStageDescriptions.size()
                    ? entry.requiredStageDescriptions.get(i) : "";
            labels.add(description == null || description.isBlank() ? stage : description);
        }
        return String.join(", ", labels);
    }

    /** 悬停价格条:出售/购买/指令显示完整价格(非 K/M 缩写);交换与"物品+指令"显示"需要: 数量×物品名" */
    private void renderPriceTooltip(GuiGraphics g, int index, int mouseX, int mouseY) {
        ClientShopEntry e = data.entries.get(index);
        String text;
        String need = Component.translatable("qshop.gui.need").getString() + ": ";
        if (e.type == ShopEntryType.BARTER) {
            ItemStack gs = !e.give.isEmpty() ? e.give.get(0) : ItemStack.EMPTY;
            text = need + (gs.isEmpty() ? "?" : gs.getCount() + "× " + gs.getHoverName().getString());
        } else if (e.type == ShopEntryType.COMMAND && !e.item.isEmpty()) {
            text = need + e.item.getCount() + "× " + e.item.getHoverName().getString();
        } else {
            String label = e.type == ShopEntryType.SELL
                    ? Component.translatable("qshop.gui.sell_price").getString()
                    : Component.translatable("qshop.gui.buy_price").getString();
            text = label + ": " + CurrencyRegistry.format(e.price) + " " + currencyName(e.currencyId);
        }
        g.renderTooltip(this.font, List.of(QText.parse(text)), Optional.empty(), mouseX, mouseY);
    }

    private Currency displayCurrency() {
        for (Currency c : data.currencies) {
            if (c.id.equals(data.shopCurrency)) {
                return c;
            }
        }
        return data.currencies.isEmpty() ? null : data.currencies.get(0);
    }

    private String currencyName(String id) {
        for (Currency c : data.currencies) {
            if (c.id.equals(id)) {
                return c.displayName;
            }
        }
        return id;
    }

    private static ItemStack cellIcon(ClientShopEntry e) {
        if (!e.displayItem.isEmpty()) {
            return e.displayItem;
        }
        if (e.type == ShopEntryType.BARTER) {
            // 以物换物:槽位显示商店物品(玩家获得物)
            if (!e.receive.isEmpty()) {
                return e.receive.get(0);
            }
            if (!e.give.isEmpty()) {
                return e.give.get(0);
            }
            return ItemStack.EMPTY;
        }
        if (e.type == ShopEntryType.COMMAND) {
            // 指令交易:无展示物品时默认显示命令方块(不显示玩家提供的代价物品)
            return new ItemStack(net.minecraft.world.item.Items.COMMAND_BLOCK);
        }
        return e.item;
    }

    /** 格子上的价格文字:出售/购买/指令(货币)显示价格+货币(过高价格用 K/M/B 缩写);
     *  交换与"物品+指令"由 drawCell 走图标分支,不走这里 */
    private static String cellText(ClientShopEntry e, String currencyName) {
        return compactPrice(e.price) + " " + currencyName;
    }

    /** 大数缩写:>=1K 用 K,>=1M 用 M,>=1B 用 B(保留 1 位小数) */
    private static String compactPrice(double v) {
        if (v >= 1_000_000_000) {
            return one(v / 1_000_000_000) + "B";
        }
        if (v >= 1_000_000) {
            return one(v / 1_000_000) + "M";
        }
        if (v >= 1_000) {
            return one(v / 1_000) + "K";
        }
        return CurrencyRegistry.format(v);
    }

    private static String one(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
