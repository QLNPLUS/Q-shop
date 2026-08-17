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

    private int left;
    private int top;

    // ---- 流式布局行位置(隐藏行 = -1) ----
    private int rowPlayerGive, rowPlayerItem, rowShopGive, rowShopItem, rowPrice, rowReqCmd;

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
        rowPlayerGive = y;
        y += ROW_PITCH;
        rowPlayerItem = -1;
        if (giveItemMode) {
            rowPlayerItem = y;
            y += ROW_PITCH;
        }
        rowShopGive = y;
        y += ROW_PITCH;
        rowShopItem = -1;
        if (shopMode == 1) {
            rowShopItem = y;
            y += ROW_PITCH;
        }
        rowPrice = -1;
        if (showPrice()) {
            rowPrice = y;
            y += ROW_PITCH;
        }
        rowReqCmd = y;
    }

    private void rebuild() {
        this.clearWidgets();
        computeRows();

        // ---- 玩家提供 ----
        addRenderableWidget(new QCheckbox(left + CONTROL_X, rowPlayerGive,
                Component.translatable("qshop.gui.currency"), !giveItemMode, v -> {
                    if (v) {
                        giveItemMode = false;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(left + OPTION_2_X, rowPlayerGive,
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
            addRenderableWidget(new QButton(left + ITEM_ACTION_X, rowPlayerItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> playerItem = s)));
            if (!playerItem.isEmpty()) {
                addRenderableWidget(new QButton(left + ITEM_ACTION_X + 50, rowPlayerItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(playerItem, s -> playerItem = s)));
                addRenderableWidget(new QButton(left + ITEM_ACTION_X + 100, rowPlayerItem, 44, 14,
                        Component.translatable("qshop.gui.clear"), b -> {
                            playerItem = ItemStack.EMPTY;
                            rebuild();
                        }));
            }
        }

        // ---- 商店提供 ----
        boolean shopCurrencyEnabled = giveItemMode;
        addRenderableWidget(new QCheckbox(left + CONTROL_X, rowShopGive,
                Component.translatable("qshop.gui.currency"), shopMode == 0, v -> {
                    if (v && shopCurrencyEnabled) {
                        shopMode = 0;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(left + OPTION_2_X, rowShopGive,
                Component.translatable("qshop.gui.item"), shopMode == 1, v -> {
                    if (v) {
                        shopMode = 1;
                    }
                    rebuild();
                }));
        addRenderableWidget(new QCheckbox(left + OPTION_3_X, rowShopGive,
                Component.translatable("qshop.gui.command"), shopMode == 2, v -> {
                    if (v) {
                        shopMode = 2;
                    }
                    rebuild();
                }));

        // 商店物品(仅商店提供物品时)
        if (shopMode == 1) {
            addRenderableWidget(new QButton(left + ITEM_ACTION_X, rowShopItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> shopItem = s)));
            if (!shopItem.isEmpty()) {
                addRenderableWidget(new QButton(left + ITEM_ACTION_X + 50, rowShopItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(shopItem, s -> shopItem = s)));
                addRenderableWidget(new QButton(left + ITEM_ACTION_X + 100, rowShopItem, 44, 14,
                        Component.translatable("qshop.gui.clear"), b -> {
                            shopItem = ItemStack.EMPTY;
                            rebuild();
                        }));
            }
        }

        // 价格/货币
        if (showPrice()) {
            addRenderableWidget(stateBox(left + CONTROL_X, rowPrice, 58, 9, "\\d{0,9}", priceStr, s -> priceStr = s));
            addRenderableWidget(new QButton(left + SECOND_CONTROL_X, rowPrice, 54, 14, currencyButtonText(), b -> {
                currencyIdx = (currencyIdx + 1) % Math.max(1, data.currencies.size());
                b.setMessage(currencyButtonText());
            }));
        }

        // 指令文本(商店提供指令时);否则任务/阶段要求
        if (shopMode == 2) {
            addRenderableWidget(stateBox(left + CONTROL_X, rowReqCmd, 168, 300, cmdStr, s -> cmdStr = s));
        } else {
            addRenderableWidget(stateBox(left + CONTROL_X, rowReqCmd, 58, 200, questsStr, s -> questsStr = s));
            addRenderableWidget(stateBox(left + SECOND_CONTROL_X, rowReqCmd, 54, 200, stagesStr, s -> stagesStr = s));
        }

        addRenderableWidget(new QButton(left + 12, top + 150, 110, 16,
                Component.translatable("qshop.gui.add_entry"), b -> save()));
        addRenderableWidget(new QButton(left + 128, top + 150, 110, 16,
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
                splitList(questsStr), splitList(stagesStr)));
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
        for (EditBox b : collectBoxes()) {
            if (b.isFocused() && b.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        ShopTextures.panelAdd(g, left, top);

        ShopEntryType type = derivedType();

        // drawString 的 Y 是字形顶部；按 14px 行高垂直居中。
        g.drawString(this.font, Component.translatable("qshop.gui.player_give"), left + LABEL_X, rowPlayerGive + 2, 0xFFFFFF);
        if (giveItemMode) {
            g.drawString(this.font, Component.translatable("qshop.gui.player_item"), left + LABEL_X, rowPlayerItem + 2, 0xFFFFFF);
            drawItemIcon(g, playerItem, left + CONTROL_X, rowPlayerItem);
        }
        g.drawString(this.font, Component.translatable("qshop.gui.shop_give"), left + LABEL_X, rowShopGive + 2, 0xFFFFFF);
        if (shopMode == 1) {
            g.drawString(this.font, Component.translatable("qshop.gui.shop_item"), left + LABEL_X, rowShopItem + 2, 0xFFFFFF);
            drawItemIcon(g, shopItem, left + CONTROL_X, rowShopItem);
        }
        if (showPrice()) {
            g.drawString(this.font, priceLabel(), left + LABEL_X, rowPrice + 2, 0xFFFFFF);
            g.drawString(this.font, Component.translatable("qshop.gui.currency"), left + SECOND_LABEL_X, rowPrice + 2, 0xFFFFFF);
        }
        if (shopMode == 2) {
            g.drawString(this.font, Component.translatable("qshop.gui.commands"), left + LABEL_X, rowReqCmd + 2, 0xFFFFFF);
        } else {
            g.drawString(this.font, Component.translatable("qshop.gui.req_quests"), left + LABEL_X, rowReqCmd + 2, 0xFFFFFF);
            g.drawString(this.font, Component.translatable("qshop.gui.req_stages"), left + SECOND_LABEL_X, rowReqCmd + 2, 0xFFFFFF);
        }
        g.drawString(this.font, Component.translatable("qshop.gui.type"), left + LABEL_X, rowReqCmd + ROW_PITCH + 9, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.type." + type.name()), left + CONTROL_X, rowReqCmd + ROW_PITCH + 9, 0xFFAA00);

        for (EditBox b : collectBoxes()) {
            ShopTextures.input(g, b.getX() - 2, b.getY() - 1, b.getWidth() + 4, 12, b.isFocused());
        }

        super.render(g, mouseX, mouseY, partialTick);
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
