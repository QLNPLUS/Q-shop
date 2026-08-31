package com.qshop.client;

import com.qshop.net.AddEntryPacket;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import com.qshop.shop.ShopEntryType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑模式:添加交易条目子窗口(简化交易模型)。
 * <p>玩家提供:货币 或 物品(勾选切换);商店提供:货币 / 物品 / 指令(勾选切换)。
 * 由二者组合推导条目类型:物品+货币=出售,货币+物品=购买,物品+物品=交换,货币/物品+指令=指令。
 * 布局为"流式重排":切换勾选后隐藏的行消失、后续行上移,不留空洞。
 */
public class ShopAddDialog extends Screen {

    private static final int GUI_W = 250;
    private static final int GUI_H = 200;
    private static final int ROW_PITCH = 19;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 72;
    private static final int SECOND_LABEL_X = 140;
    private static final int SECOND_CONTROL_X = 186;
    private static final int OPTION_2_X = 134;
    private static final int OPTION_3_X = 184;
    private static final int ITEM_ACTION_X = 94;

    private final OpenShopPacket data;
    private final int backScroll;
    private final boolean backEditMode;

    // ---- 交易形状(勾选状态) ----
    /** true = 玩家提供物品;false = 玩家提供货币 */
    private boolean giveItemMode = true;
    /** 0 = 商店提供货币;1 = 商店提供物品;2 = 商店提供指令 */
    private int shopMode = 0;

    private ItemStack playerItem = ItemStack.EMPTY;
    private ItemStack shopItem = ItemStack.EMPTY;
    private String priceStr = "1";
    private int currencyIdx = 0;
    private String cmdStr = "";
    private String questsStr = "";
    private String stagesStr = "";
    private boolean showWhenRequirementsNotMet = false;

    private int left;
    private int top;

    // ---- 流式布局行位置(隐藏行 = -1) ----
    private int rowPlayerGive, rowPlayerItem, rowShopGive, rowShopItem, rowPrice, rowReqCmd;
    private int rowReqCmdBaseY;

    public ShopAddDialog(OpenShopPacket data, int backScroll, boolean backEditMode) {
        super(Component.translatable("qshop.gui.add"));
        this.data = data;
        this.backScroll = backScroll;
        this.backEditMode = backEditMode;
        for (int i = 0; i < data.currencies.size(); i++) {
            if (data.currencies.get(i).id.equals(data.shopCurrency)) {
                this.currencyIdx = i;
                break;
            }
        }
    }

    private ShopEntryType derivedType() {
        if (shopMode == 2) {
            return ShopEntryType.COMMAND;
        }
        if (giveItemMode && shopMode == 1) {
            return ShopEntryType.BARTER;
        }
        return giveItemMode ? ShopEntryType.SELL : ShopEntryType.BUY;
    }

    /** 是否显示价格行:仅当玩家提供货币,或玩家提供物品+商店提供货币时(严格二选一模型) */
    private boolean showPrice() {
        return giveItemMode ? shopMode == 0 : true;
    }

    /** 返回主商店界面(保留当前子商店) */
    private void back() {
        Minecraft.getInstance().setScreen(new ShopScreen(data, backScroll, backEditMode, data.activeTab));
    }

    private void openPicker(Consumer<ItemStack> setter) {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(this, stack -> {
            setter.accept(stack);
            rebuild();
        }));
    }

    @Override
    protected void init() {
        ShopLayoutDebug.beginScreen(ShopLayoutDebug.DebugScreen.TRADE_SETTINGS);
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        rebuild();
    }

    /** 流式计算各行的 Y(隐藏行为 -1;指令文本/任务阶段共用一行) */
    private void computeRows() {
        if (!giveItemMode && shopMode == 0) {
            shopMode = 1;
        }
        int y = top + 8;
        rowPlayerGive = ty(ShopLayoutDebug.TradeWidget.PLAYER_GIVE_ROW, y);
        y += ROW_PITCH;
        rowPlayerItem = -1;
        if (giveItemMode) {
            rowPlayerItem = ty(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, y);
            y += ROW_PITCH;
        }
        rowShopGive = ty(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, y);
        y += ROW_PITCH;
        rowShopItem = -1;
        if (shopMode == 1) {
            rowShopItem = ty(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, y);
            y += ROW_PITCH;
        }
        rowPrice = -1;
        if (showPrice()) {
            rowPrice = ty(ShopLayoutDebug.TradeWidget.PRICE_ROW, y);
            y += ROW_PITCH;
        }
        rowReqCmdBaseY = y;
        rowReqCmd = ty(shopMode == 2
                        ? ShopLayoutDebug.TradeWidget.COMMAND_ROWS
                        : ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, y);
    }

    private int tx(ShopLayoutDebug.TradeWidget widget, int normalX) {
        return ShopLayoutDebug.x(widget, normalX);
    }

    private int ty(ShopLayoutDebug.TradeWidget widget, int normalY) {
        return ShopLayoutDebug.y(widget, normalY);
    }

    private void rebuild() {
        this.clearWidgets();
        computeRows();

        // ---- 玩家提供 ----
        addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.PLAYER_GIVE_ROW, left + CONTROL_X), rowPlayerGive,
                Component.translatable("qshop.gui.currency"), !giveItemMode, v -> {
                    if (v) {
                        giveItemMode = false;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.PLAYER_GIVE_ROW, left + OPTION_2_X), rowPlayerGive,
                Component.translatable("qshop.gui.item"), giveItemMode, v -> {
                    if (v) {
                        giveItemMode = true;
                        // 勾选"玩家提供物品"后直接清零价格,避免残留价格导致"货币+物品都要"
                        priceStr = "0";
                    }
                    rebuild();
                }));

        // 玩家物品(仅玩家提供物品时)
        if (giveItemMode) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + ITEM_ACTION_X), rowPlayerItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> playerItem = s)));
            if (!playerItem.isEmpty()) {
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + ITEM_ACTION_X + 50), rowPlayerItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(playerItem, s -> playerItem = s)));
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + ITEM_ACTION_X + 100), rowPlayerItem, 44, 14,
                        Component.translatable("qshop.gui.clear"), b -> {
                            playerItem = ItemStack.EMPTY;
                            rebuild();
                        }));
            }
        }

        // ---- 商店提供 ----
        boolean shopCurrencyEnabled = giveItemMode;
        addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, left + CONTROL_X), rowShopGive,
                Component.translatable("qshop.gui.currency"), shopMode == 0, v -> {
                    if (v && shopCurrencyEnabled) {
                        shopMode = 0;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, left + OPTION_2_X), rowShopGive,
                Component.translatable("qshop.gui.item"), shopMode == 1, v -> {
                    if (v) {
                        shopMode = 1;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, left + OPTION_3_X), rowShopGive,
                Component.translatable("qshop.gui.command"), shopMode == 2, v -> {
                    if (v) {
                        shopMode = 2;
                    }
                    rebuild();
                }));

        // 商店物品(仅商店提供物品时)
        if (shopMode == 1) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X), rowShopItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> shopItem = s)));
            if (!shopItem.isEmpty()) {
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X + 50), rowShopItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(shopItem, s -> shopItem = s)));
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X + 100), rowShopItem, 44, 14,
                        Component.translatable("qshop.gui.clear"), b -> {
                            shopItem = ItemStack.EMPTY;
                            rebuild();
                        }));
            }
        }

        // 价格/货币
        if (showPrice()) {
            addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + CONTROL_X), rowPrice,
                    58, 9, "\\d{0,9}", priceStr, s -> priceStr = s));
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + SECOND_CONTROL_X), rowPrice, 54, 14, currencyButtonText(), b -> {
                currencyIdx = (currencyIdx + 1) % Math.max(1, data.currencies.size());
                b.setMessage(currencyButtonText());
            }));
        }

        // 指令文本(商店提供指令时);否则任务/阶段要求
        if (shopMode == 2) {
            addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + CONTROL_X), rowReqCmd,
                    168, 300, cmdStr, s -> cmdStr = s));
        } else {
            addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + CONTROL_X), rowReqCmd,
                    58, 200, questsStr, s -> questsStr = s));
            addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + SECOND_CONTROL_X), rowReqCmd,
                    54, 200, stagesStr, s -> stagesStr = s));
        }

        // 条件未满足时仍显示;该选项与派生类型放在同一紧凑行。
        addRenderableWidget(new QCheckbox(
                tx(ShopLayoutDebug.TradeWidget.VISIBILITY_ROW, left + 128),
                ty(ShopLayoutDebug.TradeWidget.VISIBILITY_ROW, rowReqCmdBaseY + ROW_PITCH),
                Component.translatable("qshop.gui.show_unmet_requirements"), showWhenRequirementsNotMet,
                v -> showWhenRequirementsNotMet = v));

        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 12),
                ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, top + 150), 110, 16,
                Component.translatable("qshop.gui.add_entry"), b -> save()));
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 128),
                ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, top + 150), 110, 16,
                Component.translatable("qshop.gui.cancel"), b -> back()));
    }

    private EditBox stateBox(int x, int y, int w, int maxLen, String value, Consumer<String> onChanged) {
        // 文本类输入框:允许输入 § 颜色代码
        EditBox box = new QEditBox(this.font, x + 2, y + 2, Math.max(1, w - 4), 14, Component.literal("")).allowSectionSign();
        box.setMaxLength(maxLen);
        box.setBordered(false);
        box.setValue(value);
        box.setResponder(onChanged);
        return box;
    }

    private EditBox stateBox(int x, int y, int w, int maxLen, String filter, String value, Consumer<String> onChanged) {
        EditBox box = stateBox(x, y, w, maxLen, value, onChanged);
        box.setFilter(s -> s.matches(filter));
        return box;
    }

    private String currencyName() {
        if (data.currencies.isEmpty()) {
            return data.shopCurrency;
        }
        return data.currencies.get(currencyIdx).displayName;
    }

    /** 货币按钮文字(截断,避免盖住"货币"标签) */
    private Component currencyButtonText() {
        return Component.literal(this.font.plainSubstrByWidth(currencyName(), 64));
    }

    /** 打开物品详情子窗口(数量 + NBT 多行编辑) */
    private void openNbtEditor(ItemStack stack, Consumer<ItemStack> setter) {
        Minecraft.getInstance().setScreen(new ItemNbtScreen(this, stack, s -> {
            setter.accept(s);
            rebuild();
        }));
    }

    private void save() {
        ShopEntryType type = derivedType();
        double price = 1;
        try {
            price = Math.max(0, Double.parseDouble(priceStr.trim()));
        } catch (Exception ignored) {
        }
        String currency = data.currencies.isEmpty() ? data.shopCurrency : data.currencies.get(currencyIdx).id;
        ItemStack item;
        ItemStack give;
        switch (type) {
            case SELL -> {
                item = playerItem;
                give = ItemStack.EMPTY;
            }
            case BUY -> {
                item = shopItem;
                give = ItemStack.EMPTY;
            }
            case BARTER -> {
                item = shopItem;   // 玩家获得
                give = playerItem; // 玩家付出
            }
            default -> {
                item = giveItemMode ? playerItem : ItemStack.EMPTY; // COMMAND:物品代价(可选)
                give = ItemStack.EMPTY;
            }
        }
        QShopNetwork.sendToServer(new AddEntryPacket(data.shopId, serverTabIndex(), (byte) type.ordinal(), price, currency,
                cmdStr, item, give, ItemStack.EMPTY,
                splitList(questsStr), splitList(stagesStr), showWhenRequirementsNotMet));
        back();
    }

    /** 可见子商店序号 → 服务端子商店序号(编辑界面内可见序号即服务端序号,防御性转换) */
    private int serverTabIndex() {
        return data.activeTab;
    }

    private static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String part : s.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F8 && ShopLayoutDebug.isConfiguredEnabled()) {
            ShopLayoutDebug.toggle();
            rebuild();
            return true;
        }
        if (ShopLayoutDebug.isEnabled() && !hasFocusedBox()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                ShopLayoutDebug.selectNext((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            int step = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0 ? 1 : 5;
            int dx = 0;
            int dy = 0;
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) dx = -step;
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) dx = step;
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) dy = -step;
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) dy = step;
            if (dx != 0 || dy != 0) {
                ShopLayoutDebug.moveSelected(dx, dy);
                rebuild();
                return true;
            }
        }
        for (EditBox b : collectBoxes()) {
            if (b.isFocused() && b.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean hasFocusedBox() {
        for (EditBox box : collectBoxes()) {
            if (box.isFocused()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox b : collectBoxes()) {
            if (b.isFocused() && b.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditBox b : collectBoxes()) {
            b.setFocused(false);
        }
        for (EditBox b : collectBoxes()) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                b.setFocused(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<EditBox> collectBoxes() {
        List<EditBox> boxes = new ArrayList<>();
        for (var w : this.children()) {
            if (w instanceof EditBox box) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ShopTextures.panelAdd(g,
                tx(ShopLayoutDebug.TradeWidget.PANEL, left),
                ty(ShopLayoutDebug.TradeWidget.PANEL, top));

        ShopEntryType type = derivedType();

        // drawString 的 Y 是字形顶部；按 14px 行高垂直居中。
        g.drawString(this.font, Component.translatable("qshop.gui.player_give"),
                tx(ShopLayoutDebug.TradeWidget.PLAYER_GIVE_ROW, left + LABEL_X), rowPlayerGive + 2, 0xFFFFFF);
        if (giveItemMode) {
            g.drawString(this.font, Component.translatable("qshop.gui.player_item"),
                    tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + LABEL_X), rowPlayerItem + 2, 0xFFFFFF);
            drawItemIcon(g, playerItem,
                    tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + CONTROL_X), rowPlayerItem);
        }
        g.drawString(this.font, Component.translatable("qshop.gui.shop_give"),
                tx(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, left + LABEL_X), rowShopGive + 2, 0xFFFFFF);
        if (shopMode == 1) {
            g.drawString(this.font, Component.translatable("qshop.gui.shop_item"),
                    tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + LABEL_X), rowShopItem + 2, 0xFFFFFF);
            drawItemIcon(g, shopItem,
                    tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + CONTROL_X), rowShopItem);
        }
        if (showPrice()) {
            g.drawString(this.font, priceLabel(),
                    tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + LABEL_X), rowPrice + 2, 0xFFFFFF);
            g.drawString(this.font, Component.translatable("qshop.gui.currency"),
                    tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + SECOND_LABEL_X), rowPrice + 2, 0xFFFFFF);
        }
        if (shopMode == 2) {
            g.drawString(this.font, Component.translatable("qshop.gui.commands"),
                    tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + LABEL_X), rowReqCmd + 2, 0xFFFFFF);
        } else {
            g.drawString(this.font, Component.translatable("qshop.gui.req_quests"),
                    tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + LABEL_X), rowReqCmd + 2, 0xFFFFFF);
            g.drawString(this.font, Component.translatable("qshop.gui.req_stages"),
                    tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + SECOND_LABEL_X), rowReqCmd + 2, 0xFFFFFF);
        }
        int typeY = ty(ShopLayoutDebug.TradeWidget.TYPE_ROW, rowReqCmdBaseY + ROW_PITCH);
        g.drawString(this.font, Component.translatable("qshop.gui.type"),
                tx(ShopLayoutDebug.TradeWidget.TYPE_ROW, left + LABEL_X), typeY + 9, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.type." + type.name()),
                tx(ShopLayoutDebug.TradeWidget.TYPE_ROW, left + CONTROL_X), typeY + 9, 0xFFAA00);

        for (EditBox b : collectBoxes()) {
            ShopTextures.input(g, b.getX() - 2, b.getY() - 1, b.getWidth() + 4, 12, b.isFocused());
        }

        super.render(g, mouseX, mouseY, partialTick);
        renderDebugOverlay(g);
    }

    private void renderDebugOverlay(GuiGraphics g) {
        if (!ShopLayoutDebug.isEnabled()) {
            return;
        }
        ShopLayoutDebug.TradeWidget widget = ShopLayoutDebug.selectedTrade();
        int x;
        int y;
        int w;
        int h;
        switch (widget) {
            case PANEL -> {
                x = tx(ShopLayoutDebug.TradeWidget.PANEL, left);
                y = ty(ShopLayoutDebug.TradeWidget.PANEL, top);
                w = GUI_W;
                h = GUI_H;
            }
            case PLAYER_GIVE_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.PLAYER_GIVE_ROW, left + LABEL_X);
                y = rowPlayerGive;
                w = 226;
                h = 16;
            }
            case PLAYER_ITEM_ROW -> {
                if (rowPlayerItem < 0) return;
                x = tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + LABEL_X);
                y = rowPlayerItem;
                w = 226;
                h = 16;
            }
            case SHOP_GIVE_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.SHOP_GIVE_ROW, left + LABEL_X);
                y = rowShopGive;
                w = 226;
                h = 16;
            }
            case SHOP_ITEM_ROW -> {
                if (rowShopItem < 0) return;
                x = tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + LABEL_X);
                y = rowShopItem;
                w = 226;
                h = 16;
            }
            case PRICE_ROW -> {
                if (rowPrice < 0) return;
                x = tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + LABEL_X);
                y = rowPrice;
                w = 226;
                h = 16;
            }
            case REQUIREMENTS_ROW, COMMAND_ROWS -> {
                x = tx(widget, left + LABEL_X);
                y = rowReqCmd;
                w = 226;
                h = 16;
            }
            case VISIBILITY_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.VISIBILITY_ROW, left + 128);
                y = ty(ShopLayoutDebug.TradeWidget.VISIBILITY_ROW, rowReqCmdBaseY + ROW_PITCH);
                w = 110;
                h = 16;
            }
            case TYPE_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.TYPE_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TradeWidget.TYPE_ROW, rowReqCmdBaseY + ROW_PITCH);
                w = 226;
                h = 16;
            }
            case BOTTOM_ACTIONS -> {
                x = tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 12);
                y = ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, top + 150);
                w = 226;
                h = 16;
            }
            default -> {
                return;
            }
        }
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500.0f);
        ShopLayoutDebug.renderOverlay(g, this.font, x, y, w, h);
        g.flush();
        g.pose().popPose();
    }

    private String priceLabel() {
        if (giveItemMode && shopMode == 0) {
            return Component.translatable("qshop.gui.reward").getString(); // 商店支付
        }
        return Component.translatable("qshop.gui.pay_price").getString();   // 玩家支付
    }

    /** 物品图标:直接画物品(16x16),不画 slot 背景 */
    private void drawItemIcon(GuiGraphics g, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 2, y);
            g.renderItemDecorations(this.font, stack, x + 2, y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
