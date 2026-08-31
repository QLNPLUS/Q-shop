package com.qshop.client;

import com.qshop.net.ClientShopEntry;
import com.qshop.net.EditShopPacket;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import com.qshop.shop.LimitReset;
import com.qshop.shop.ShopCommand;
import com.qshop.shop.ShopEntryType;
import com.qshop.util.QText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑交易条目子窗口(游戏内编辑,仅创造模式)。
 * <p>采用简化交易模型:玩家提供(货币/物品)+ 商店提供(货币/物品/指令)勾选切换,
 * 组合推导条目类型。布局为"流式重排":切换勾选后,隐藏的行消失、后续行上移补齐,
 * 不会留下空洞;指令区独立成块,不与首行指令冲突。
 */
public class ShopEditDialog extends Screen {

    private static final int GUI_W = 250;
    private static final int GUI_H = 280;
    private static final int MAX_COMMANDS = 3;
    private static final int ROW_PITCH = 19;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 72;
    private static final int SECOND_LABEL_X = 140;
    private static final int SECOND_CONTROL_X = 186;
    private static final int OPTION_2_X = 134;
    private static final int OPTION_3_X = 184;
    private static final int ITEM_ACTION_X = 94;

    private static class CommandData {
        String cmd = "";
        boolean op = false;
        boolean silent = true;
    }

    private final OpenShopPacket data;
    private final int entryIndex;
    private final ClientShopEntry entry;
    private final int backScroll;
    private final boolean backEditMode;

    // ---- 交易形状(勾选状态,可由用户切换;保存时按组合推导类型) ----
    private boolean giveItemMode = true;
    private int shopMode = 0;

    private String titleStr = "";
    private String descStr = "";
    private ItemStack displayItem = ItemStack.EMPTY;
    private ItemStack playerItem = ItemStack.EMPTY;
    private ItemStack shopItem = ItemStack.EMPTY;
    private String priceStr = "0";
    private String globalStr = "-1";
    private String playerStr = "-1";
    private int currencyIdx = 0;
    private LimitReset reset = LimitReset.NEVER;
    private String questsStr = "";
    private String stagesStr = "";
    private final List<CommandData> commands = new ArrayList<>();

    private int left;
    private int top;

    // ---- 流式布局行位置(rebuild 时按当前勾选状态计算,隐藏行 = -1) ----
    private int rowTitle, rowDesc, rowDisplay, rowPlayerGive, rowPlayerItem,
            rowShopGive, rowShopItem, rowPrice, rowLimits, rowReset, rowReqs, rowCmdHead;
    private int commandBaseY;

    public ShopEditDialog(OpenShopPacket data, int entryIndex, int backScroll, boolean backEditMode) {
        super(Component.translatable("qshop.gui.edit_title"));
        this.data = data;
        this.entry = data.entries.get(entryIndex);
        this.entryIndex = this.entry.serverIndex >= 0 ? this.entry.serverIndex : entryIndex;
        this.backScroll = backScroll;
        this.backEditMode = backEditMode;

        this.titleStr = entry.displayName == null ? "" : entry.displayName;
        this.descStr = entry.description == null ? "" : entry.description;
        this.displayItem = entry.displayItem.copy();
        switch (entry.type) {
            case BUY -> {
                giveItemMode = false;
                shopMode = 1;
                shopItem = entry.item.copy();
            }
            case SELL -> {
                giveItemMode = true;
                shopMode = 0;
                playerItem = entry.item.copy();
            }
            case BARTER -> {
                giveItemMode = true;
                shopMode = 1;
                playerItem = entry.give.isEmpty() ? ItemStack.EMPTY : entry.give.get(0).copy();
                shopItem = entry.receive.isEmpty() ? ItemStack.EMPTY : entry.receive.get(0).copy();
            }
            case COMMAND -> {
                giveItemMode = !entry.item.isEmpty();
                shopMode = 2;
                playerItem = entry.item.copy();
            }
        }
        this.priceStr = com.qshop.currency.CurrencyRegistry.format(entry.price);
        this.globalStr = String.valueOf(entry.globalLimit);
        this.playerStr = String.valueOf(entry.playerLimit);
        for (int i = 0; i < data.currencies.size(); i++) {
            if (data.currencies.get(i).id.equals(entry.currencyId)) {
                this.currencyIdx = i;
                break;
            }
        }
        this.reset = LimitReset.fromName(entry.resetName);
        for (ShopCommand sc : entry.commands) {
            CommandData cd = new CommandData();
            cd.cmd = sc.command;
            cd.op = sc.op;
            cd.silent = sc.silent;
            commands.add(cd);
        }
        while (commands.isEmpty()) {
            commands.add(new CommandData());
        }
        this.questsStr = String.join(",", entry.requiredQuests);
        this.stagesStr = String.join(",", entry.requiredStages);
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

    @Override
    protected void init() {
        ShopLayoutDebug.beginScreen(ShopLayoutDebug.DebugScreen.TRADE_SETTINGS);
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        rebuild();
    }

    /** 返回主商店界面(保留当前子商店) */
    private void back() {
        Minecraft.getInstance().setScreen(new ShopScreen(data, backScroll, backEditMode, data.activeTab));
    }

    /** 打开物品选择器,选中后写回对应字段并重建界面 */
    private void openPicker(Consumer<ItemStack> setter) {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(this, stack -> {
            setter.accept(stack);
            rebuild();
        }));
    }

    /** 打开物品详情子窗口(数量 + NBT 多行编辑) */
    private void openNbtEditor(ItemStack stack, Consumer<ItemStack> setter) {
        Minecraft.getInstance().setScreen(new ItemNbtScreen(this, stack, setter));
    }

    /** 流式计算各行的 Y(隐藏行为 -1;指令区为表头行,指令行在其下逐行排布) */
    private void computeRows() {
        boolean shopCurrencyEnabled = giveItemMode;
        if (!shopCurrencyEnabled && shopMode == 0) {
            shopMode = 1;
        }
        int y = top + 18;
        rowTitle = ty(ShopLayoutDebug.TradeWidget.TITLE_ROW, y);
        y += ROW_PITCH;
        rowDesc = ty(ShopLayoutDebug.TradeWidget.DESCRIPTION_ROW, y);
        y += ROW_PITCH;
        rowDisplay = ty(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, y);
        y += ROW_PITCH;
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
        rowLimits = ty(ShopLayoutDebug.TradeWidget.LIMITS_ROW, y);
        y += ROW_PITCH;
        rowReset = ty(ShopLayoutDebug.TradeWidget.RESET_ROW, y);
        y += ROW_PITCH;
        rowReqs = ty(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, y);
        y += ROW_PITCH;
        rowCmdHead = -1;
        commandBaseY = -1;
        if (shopMode == 2) {
            commandBaseY = y;
            rowCmdHead = ty(ShopLayoutDebug.TradeWidget.COMMAND_HEADER, y);
        }
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

        // 标题 / 描述
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.TITLE_ROW, left + CONTROL_X), rowTitle,
                168, 32, titleStr, s -> titleStr = s));
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.DESCRIPTION_ROW, left + CONTROL_X), rowDesc,
                168, 32, descStr, s -> descStr = s));

        // 展示物品:选择 / 编辑(数量+NBT)/ 清除
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + ITEM_ACTION_X), rowDisplay, 46, 14,
                Component.translatable("qshop.gui.select"), b -> openPicker(s -> displayItem = s)));
        if (!displayItem.isEmpty()) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + ITEM_ACTION_X + 50), rowDisplay, 46, 14,
                    Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(displayItem, s -> {
                        displayItem = s;
                        rebuild();
                    })));
        }
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + ITEM_ACTION_X + 100), rowDisplay, 44, 14,
                Component.translatable("qshop.gui.clear"), b -> {
                    displayItem = ItemStack.EMPTY;
                    rebuild();
                }));

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

        // 玩家物品
        if (giveItemMode) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + ITEM_ACTION_X), rowPlayerItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> playerItem = s)));
            if (!playerItem.isEmpty()) {
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PLAYER_ITEM_ROW, left + ITEM_ACTION_X + 50), rowPlayerItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(playerItem, s -> {
                            playerItem = s;
                            rebuild();
                        })));
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

        // 商店物品
        if (shopMode == 1) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X), rowShopItem, 46, 14,
                    Component.translatable("qshop.gui.select"), b -> openPicker(s -> shopItem = s)));
            if (!shopItem.isEmpty()) {
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X + 50), rowShopItem, 46, 14,
                        Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor(shopItem, s -> {
                            shopItem = s;
                            rebuild();
                        })));
                addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.SHOP_ITEM_ROW, left + ITEM_ACTION_X + 100), rowShopItem, 44, 14,
                        Component.translatable("qshop.gui.clear"), b -> {
                            shopItem = ItemStack.EMPTY;
                            rebuild();
                        }));
            }
        }

        // 价格 / 货币(纯物品交换 / 物品+指令不显示)
        if (showPrice()) {
            addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + CONTROL_X), rowPrice,
                    58, 9, "\\d{0,9}", priceStr, s -> priceStr = s));
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.PRICE_ROW, left + SECOND_CONTROL_X), rowPrice, 54, 14, currencyButtonText(), b -> {
                currencyIdx = (currencyIdx + 1) % Math.max(1, data.currencies.size());
                b.setMessage(currencyButtonText());
            }));
        }

        // 限制
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.LIMITS_ROW, left + CONTROL_X), rowLimits,
                58, 7, "-?\\d{0,7}", globalStr, s -> globalStr = s));
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.LIMITS_ROW, left + SECOND_CONTROL_X), rowLimits,
                54, 7, "-?\\d{0,7}", playerStr, s -> playerStr = s));

        // 重置周期
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.RESET_ROW, left + CONTROL_X), rowReset, 168, 14,
                Component.translatable("qshop.reset." + reset.name()), b -> {
                    LimitReset[] values = LimitReset.values();
                    reset = values[(reset.ordinal() + 1) % values.length];
                    b.setMessage(Component.translatable("qshop.reset." + reset.name()));
                }));

        // 任务 / 阶段要求(逗号分隔多个)
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + CONTROL_X), rowReqs,
                58, 200, questsStr, s -> questsStr = s));
        addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + SECOND_CONTROL_X), rowReqs,
                54, 200, stagesStr, s -> stagesStr = s));

        // 指令块(商店提供指令时):表头行放"添加指令"按钮,指令行从表头下方逐行排布
        if (shopMode == 2) {
            addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.COMMAND_HEADER, left + CONTROL_X), rowCmdHead, 168, 14,
                    Component.translatable("qshop.gui.add_command"), b -> {
                        if (commands.size() < MAX_COMMANDS) {
                            commands.add(new CommandData());
                            rebuild();
                        }
                    }));
            for (int i = 0; i < commands.size() && i < MAX_COMMANDS; i++) {
                CommandData cd = commands.get(i);
                int y = ty(ShopLayoutDebug.TradeWidget.COMMAND_ROWS,
                        commandBaseY + ROW_PITCH + i * ROW_PITCH);
                addRenderableWidget(stateBox(tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + 12), y,
                        84, 300, cd.cmd, s -> cd.cmd = s));
                addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + 100), y,
                        Component.translatable("qshop.gui.op"), cd.op, v -> cd.op = v));
                addRenderableWidget(new QCheckbox(tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + 150), y,
                        Component.translatable("qshop.gui.silent"), cd.silent, v -> cd.silent = v));
                final int idx = i;
                addRenderableWidget(new QIconButton(tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + 224), y,
                        ShopTextures.Icon.MINUS, () -> {
                    commands.remove(idx);
                    if (commands.isEmpty()) {
                        commands.add(new CommandData());
                    }
                    rebuild();
                }));
            }
        }

        // 固定底部操作区；最拥挤的三条指令状态仍与按钮保留 5px 可见间距。
        int btnY = top + 261;
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 12),
                ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, btnY), 110, 16,
                Component.translatable("qshop.gui.save"), b -> save()));
        addRenderableWidget(new QButton(tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 128),
                ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, btnY), 110, 16,
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
            return entry.currencyId;
        }
        return data.currencies.get(currencyIdx).displayName;
    }

    /** 货币按钮文字(截断,避免盖住"货币"标签) */
    private Component currencyButtonText() {
        return Component.literal(this.font.plainSubstrByWidth(currencyName(), 64));
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

    private void save() {
        ShopEntryType type = derivedType();
        double price = 0;
        try {
            price = Math.max(0, Double.parseDouble(priceStr.trim()));
        } catch (Exception ignored) {
        }
        int globalLimit = -1;
        try {
            globalLimit = Math.max(-1, Integer.parseInt(globalStr.trim()));
        } catch (Exception ignored) {
        }
        int playerLimit = -1;
        try {
            playerLimit = Math.max(-1, Integer.parseInt(playerStr.trim()));
        } catch (Exception ignored) {
        }
        String currency = data.currencies.isEmpty() ? entry.currencyId : data.currencies.get(currencyIdx).id;

        // 按组合推导发送的物品字段
        ItemStack sendItem;
        ItemStack sendGive;
        int itemCount;
        String itemNbt;
        switch (type) {
            case SELL -> {
                sendItem = playerItem;
                sendGive = ItemStack.EMPTY;
            }
            case BUY -> {
                sendItem = shopItem;
                sendGive = ItemStack.EMPTY;
            }
            case BARTER -> {
                sendItem = shopItem;   // 玩家获得
                sendGive = playerItem;  // 玩家付出
            }
            default -> {
                sendItem = giveItemMode ? playerItem : ItemStack.EMPTY; // COMMAND:物品代价(可选)
                sendGive = ItemStack.EMPTY;
            }
        }
        itemCount = sendItem.isEmpty() ? 1 : Mth.clamp(sendItem.getCount(), 1, 1000);
        itemNbt = sendItem.getTag() == null ? "" : new SnbtPrinterTagVisitor().visit(sendItem.getTag());

        List<ShopCommand> cmds;
        if (type == ShopEntryType.COMMAND) {
            cmds = new ArrayList<>();
            for (int i = 0; i < commands.size() && i < MAX_COMMANDS; i++) {
                CommandData cd = commands.get(i);
                if (!cd.cmd.isBlank()) {
                    cmds.add(new ShopCommand(cd.cmd.trim(), cd.op, cd.silent));
                }
            }
        } else {
            cmds = List.of(); // 非指令交易:清除指令
        }

        QShopNetwork.sendToServer(new EditShopPacket(data.shopId, serverTabIndex(), entryIndex, (byte) type.ordinal(),
                price, currency, globalLimit, playerLimit, reset.name(), cmds,
                titleStr, descStr, displayItem, sendItem, sendGive, itemCount, itemNbt,
                splitList(questsStr), splitList(stagesStr)));
        back();
    }

    /** 可见子商店序号 → 服务端子商店序号(编辑界面内可见序号即服务端序号,防御性转换) */
    private int serverTabIndex() {
        return data.activeTab;
    }

    // ---------------- 输入路由 ----------------

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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ShopTextures.panelEdit(g,
                tx(ShopLayoutDebug.TradeWidget.PANEL, left),
                ty(ShopLayoutDebug.TradeWidget.PANEL, top));

        ShopEntryType type = derivedType();
        int headerX = tx(ShopLayoutDebug.TradeWidget.HEADER, left);
        int headerY = ty(ShopLayoutDebug.TradeWidget.HEADER, top);
        ItemStack topIcon = !playerItem.isEmpty() ? playerItem
                : (!shopItem.isEmpty() ? shopItem
                : (!displayItem.isEmpty() ? displayItem : ItemStack.EMPTY));
        if (!topIcon.isEmpty()) {
            g.renderItem(topIcon, headerX + LABEL_X, headerY + 2);
            g.drawString(this.font, QText.clip(topIcon.getHoverName().getString(), this.font, 110),
                    headerX + 32, headerY + 5, 0xFFFFFF);
        }
        g.drawString(this.font, Component.translatable("qshop.type." + type.name()),
                headerX + 200, headerY + 5, 0xFFAA00);

        // drawString 的 Y 是字形顶部；按 14px 行高垂直居中。
        g.drawString(this.font, Component.translatable("qshop.gui.title_field"),
                tx(ShopLayoutDebug.TradeWidget.TITLE_ROW, left + LABEL_X), rowTitle + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.desc_field"),
                tx(ShopLayoutDebug.TradeWidget.DESCRIPTION_ROW, left + LABEL_X), rowDesc + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.display_item"),
                tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + LABEL_X), rowDisplay + 2, 0xFFFFFF);
        drawItemIcon(g, displayItem,
                tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + CONTROL_X), rowDisplay);
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
        g.drawString(this.font, Component.translatable("qshop.gui.global_limit"),
                tx(ShopLayoutDebug.TradeWidget.LIMITS_ROW, left + LABEL_X), rowLimits + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.player_limit"),
                tx(ShopLayoutDebug.TradeWidget.LIMITS_ROW, left + SECOND_LABEL_X), rowLimits + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.reset"),
                tx(ShopLayoutDebug.TradeWidget.RESET_ROW, left + LABEL_X), rowReset + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_quests"),
                tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + LABEL_X), rowReqs + 2, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_stages"),
                tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + SECOND_LABEL_X), rowReqs + 2, 0xFFFFFF);
        if (shopMode == 2) {
            g.drawString(this.font, Component.translatable("qshop.gui.commands"),
                    tx(ShopLayoutDebug.TradeWidget.COMMAND_HEADER, left + LABEL_X), rowCmdHead + 2, 0xFFFFFF);
        }

        // 输入框背景(无边框 EditBox 文字画在左上角,材质向上/左扩展 2px 使其视觉居中)
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
            case HEADER -> {
                x = tx(ShopLayoutDebug.TradeWidget.HEADER, left);
                y = ty(ShopLayoutDebug.TradeWidget.HEADER, top);
                w = GUI_W - 12;
                h = 20;
            }
            case TITLE_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.TITLE_ROW, left + LABEL_X);
                y = rowTitle;
                w = 226;
                h = 34;
            }
            case DESCRIPTION_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.DESCRIPTION_ROW, left + LABEL_X);
                y = rowDesc;
                w = 226;
                h = 34;
            }
            case DISPLAY_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.DISPLAY_ROW, left + LABEL_X);
                y = rowDisplay;
                w = 226;
                h = 16;
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
            case LIMITS_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.LIMITS_ROW, left + LABEL_X);
                y = rowLimits;
                w = 226;
                h = 16;
            }
            case RESET_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.RESET_ROW, left + LABEL_X);
                y = rowReset;
                w = 226;
                h = 16;
            }
            case REQUIREMENTS_ROW -> {
                x = tx(ShopLayoutDebug.TradeWidget.REQUIREMENTS_ROW, left + LABEL_X);
                y = rowReqs;
                w = 226;
                h = 16;
            }
            case COMMAND_HEADER -> {
                if (rowCmdHead < 0) return;
                x = tx(ShopLayoutDebug.TradeWidget.COMMAND_HEADER, left + LABEL_X);
                y = rowCmdHead;
                w = 226;
                h = 16;
            }
            case COMMAND_ROWS -> {
                if (rowCmdHead < 0) return;
                x = tx(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, left + LABEL_X);
                y = ty(ShopLayoutDebug.TradeWidget.COMMAND_ROWS, commandBaseY + ROW_PITCH);
                w = 226;
                h = Math.max(16, Math.min(MAX_COMMANDS, commands.size()) * ROW_PITCH);
            }
            case BOTTOM_ACTIONS -> {
                x = tx(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, left + 12);
                y = ty(ShopLayoutDebug.TradeWidget.BOTTOM_ACTIONS, top + 261);
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
