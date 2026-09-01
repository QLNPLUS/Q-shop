package com.qshop.client;

import com.qshop.net.EditTabPacket;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import com.qshop.net.RemoveTabPacket;
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
 * 编辑子商店(tab)的名字、图标与任务/阶段要求(图标类似交易条目的展示物品,通过物品选择器选取)。
 */
public class TabEditDialog extends Screen {

    private static final int GUI_W = 250;
    private static final int GUI_H = 200;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 72;
    private static final int ITEM_ACTION_X = 94;

    private final OpenShopPacket data;
    private final int tabIndex;
    private final com.qshop.net.ClientTab tab;
    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox descBox;
    private EditBox questsBox;
    private EditBox stagesBox;
    private EditBox stageDescriptionsBox;
    private QButton iconEditButton;
    private ItemStack icon = ItemStack.EMPTY;
    // 状态字段:从选择器/详情子窗口返回时 init() 会重建控件,必须用字段恢复输入,避免丢失
    private String nameStr = "";
    private String descStr = "";
    private String questsStr = "";
    private String stagesStr = "";
    private String stageDescriptionsStr = "";
    private boolean showWhenRequirementsNotMet = false;

    public TabEditDialog(OpenShopPacket data, int tabIndex) {
        super(Component.translatable("qshop.gui.edit_tab"));
        this.data = data;
        this.tabIndex = tabIndex;
        this.tab = data.tabs.stream().filter(t -> t.serverIndex == tabIndex).findFirst().orElse(null);
        if (this.tab != null) {
            this.icon = this.tab.icon.copy();
            this.nameStr = tab.name == null ? "" : tab.name;
            this.descStr = tab.description == null ? "" : tab.description;
            this.questsStr = String.join(",", tab.requiredQuests);
            this.stagesStr = String.join(",", tab.requiredStages);
            this.stageDescriptionsStr = String.join(",", tab.requiredStageDescriptions);
            this.showWhenRequirementsNotMet = tab.showWhenRequirementsNotMet;
        }
    }

    @Override
    protected void init() {
        ShopLayoutDebug.beginScreen(ShopLayoutDebug.DebugScreen.TAB_SETTINGS);
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        if (tab == null) {
            Minecraft.getInstance().setScreen(new ShopScreen(data, 0, true, data.activeTab));
            return;
        }
        rebuildTabWidgets();
    }

    private void rebuildTabWidgets() {
        clearWidgets();

        nameBox = new QEditBox(this.font,
                tx(ShopLayoutDebug.TabWidget.NAME_ROW, left + CONTROL_X + 2),
                ty(ShopLayoutDebug.TabWidget.NAME_ROW, top + 28),
                164, 14, Component.literal("")).allowSectionSign();
        nameBox.setMaxLength(32);
        nameBox.setBordered(false);
        nameBox.setValue(nameStr);
        nameBox.setResponder(s -> nameStr = s == null ? "" : s);
        addRenderableWidget(nameBox);

        // 任务/阶段要求(逗号分隔多个;子商店未满足时非编辑玩家看不到该子商店)
        questsBox = new QEditBox(this.font,
                tx(ShopLayoutDebug.TabWidget.QUESTS_ROW, left + CONTROL_X + 2),
                ty(ShopLayoutDebug.TabWidget.QUESTS_ROW, top + 68),
                164, 14, Component.literal("")).allowSectionSign();
        questsBox.setMaxLength(200);
        questsBox.setBordered(false);
        questsBox.setValue(questsStr);
        questsBox.setResponder(s -> questsStr = s == null ? "" : s);
        addRenderableWidget(questsBox);
        stagesBox = new QEditBox(this.font,
                tx(ShopLayoutDebug.TabWidget.STAGES_ROW, left + CONTROL_X + 2),
                ty(ShopLayoutDebug.TabWidget.STAGES_ROW, top + 86),
                164, 14, Component.literal("")).allowSectionSign();
        stagesBox.setMaxLength(200);
        stagesBox.setBordered(false);
        stagesBox.setValue(stagesStr);
        stagesBox.setResponder(s -> stagesStr = s == null ? "" : s);
        addRenderableWidget(stagesBox);

        // 阶段描述按 requiredStages 的索引对应,逗号分隔多个描述
        stageDescriptionsBox = new QEditBox(this.font,
                tx(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, left + CONTROL_X + 2),
                ty(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, top + 104),
                164, 14, Component.literal("")).allowSectionSign();
        stageDescriptionsBox.setMaxLength(500);
        stageDescriptionsBox.setBordered(false);
        stageDescriptionsBox.setValue(stageDescriptionsStr);
        stageDescriptionsBox.setResponder(s -> stageDescriptionsStr = s == null ? "" : s);
        addRenderableWidget(stageDescriptionsBox);

        // 描述(悬停在子商店 tab 上时以 tooltip 显示;支持 \n 换行)
        descBox = new QEditBox(this.font,
                tx(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, left + CONTROL_X + 2),
                ty(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, top + 122),
                164, 14, Component.literal("")).allowSectionSign();
        descBox.setMaxLength(500);
        descBox.setBordered(false);
        descBox.setValue(descStr);
        descBox.setResponder(s -> descStr = s == null ? "" : s);
        addRenderableWidget(descBox);

        addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + ITEM_ACTION_X),
                ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 48), 46, 14,
                Component.translatable("qshop.gui.select"), b -> openPicker(s -> icon = s)));
        iconEditButton = addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + ITEM_ACTION_X + 50),
                ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 48), 46, 14,
                Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor()));
        iconEditButton.active = !icon.isEmpty();
        addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + ITEM_ACTION_X + 100),
                ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 48), 44, 14,
                Component.translatable("qshop.gui.clear"), b -> {
                    icon = ItemStack.EMPTY;
                    iconEditButton.active = false;
                }));

        addRenderableWidget(new QCheckbox(
                tx(ShopLayoutDebug.TabWidget.VISIBILITY_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.VISIBILITY_ROW, top + 140),
                Component.translatable("qshop.gui.show_tab_when_unmet"), showWhenRequirementsNotMet,
                v -> showWhenRequirementsNotMet = v));

        addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.DELETE_BUTTON, left + 12),
                ty(ShopLayoutDebug.TabWidget.DELETE_BUTTON, top + 154), 110, 16,
                Component.translatable("qshop.gui.delete_tab"), b -> {
                    // 用服务端子商店序号发送(隐藏过滤后可见序号会错位)
                    QShopNetwork.sendToServer(new RemoveTabPacket(data.shopId, tab.serverIndex));
                    back();
                }));
        addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.SAVE_BUTTON, left + 128),
                ty(ShopLayoutDebug.TabWidget.SAVE_BUTTON, top + 154), 110, 16,
                Component.translatable("qshop.gui.save"), b -> save()));
        addRenderableWidget(new QButton(
                tx(ShopLayoutDebug.TabWidget.CANCEL_BUTTON, left + 12),
                ty(ShopLayoutDebug.TabWidget.CANCEL_BUTTON, top + 176), 226, 16,
                Component.translatable("qshop.gui.cancel"), b -> back()));
    }

    private int tx(ShopLayoutDebug.TabWidget widget, int normalX) {
        return ShopLayoutDebug.x(widget, normalX);
    }

    private int ty(ShopLayoutDebug.TabWidget widget, int normalY) {
        return ShopLayoutDebug.y(widget, normalY);
    }

    private void openPicker(Consumer<ItemStack> setter) {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(this, setter::accept));
    }

    private void openNbtEditor() {
        if (!icon.isEmpty()) {
            Minecraft.getInstance().setScreen(new ItemNbtScreen(this, icon, stack -> icon = stack));
        }
    }

    private void back() {
        Minecraft.getInstance().setScreen(new ShopScreen(data, 0, true, data.activeTab));
    }

    private void save() {
        QShopNetwork.sendToServer(new EditTabPacket(data.shopId, tab.serverIndex, nameStr, descStr, icon,
                splitList(questsStr), splitList(stagesStr), splitStageDescriptions(stageDescriptionsStr),
                showWhenRequirementsNotMet));
        back();
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

    /** 阶段描述保留中间空位,以便空描述回退到对应阶段名。 */
    private static List<String> splitStageDescriptions(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",", -1)) {
            out.add(part.trim());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F8 && ShopLayoutDebug.isConfiguredEnabled()) {
            ShopLayoutDebug.toggle();
            rebuildTabWidgets();
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
                rebuildTabWidgets();
                return true;
            }
        }
        if (nameBox.isFocused() && nameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (descBox.isFocused() && descBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (questsBox.isFocused() && questsBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (stagesBox.isFocused() && stagesBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (stageDescriptionsBox.isFocused() && stageDescriptionsBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean hasFocusedBox() {
        return nameBox.isFocused() || descBox.isFocused() || questsBox.isFocused()
                || stagesBox.isFocused() || stageDescriptionsBox.isFocused();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox.isFocused() && nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (descBox.isFocused() && descBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (questsBox.isFocused() && questsBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (stagesBox.isFocused() && stagesBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (stageDescriptionsBox.isFocused() && stageDescriptionsBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        nameBox.setFocused(false);
        descBox.setFocused(false);
        questsBox.setFocused(false);
        stagesBox.setFocused(false);
        stageDescriptionsBox.setFocused(false);
        if (nameBox.mouseClicked(mouseX, mouseY, button)) {
            nameBox.setFocused(true);
            return true;
        }
        if (descBox.mouseClicked(mouseX, mouseY, button)) {
            descBox.setFocused(true);
            return true;
        }
        if (questsBox.mouseClicked(mouseX, mouseY, button)) {
            questsBox.setFocused(true);
            return true;
        }
        if (stagesBox.mouseClicked(mouseX, mouseY, button)) {
            stagesBox.setFocused(true);
            return true;
        }
        if (stageDescriptionsBox.mouseClicked(mouseX, mouseY, button)) {
            stageDescriptionsBox.setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.background(g, this.width, this.height);
        ShopTextures.panelTab(g,
                tx(ShopLayoutDebug.TabWidget.PANEL, left),
                ty(ShopLayoutDebug.TabWidget.PANEL, top));

        g.drawString(this.font, Component.translatable("qshop.gui.edit_tab"),
                tx(ShopLayoutDebug.TabWidget.TITLE, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.TITLE, top + 8), 0xFFFFFF);

        g.drawString(this.font, Component.translatable("qshop.gui.tab_name"),
                tx(ShopLayoutDebug.TabWidget.NAME_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.NAME_ROW, top + 29), 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.tab_icon"),
                tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 51), 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_quests"),
                tx(ShopLayoutDebug.TabWidget.QUESTS_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.QUESTS_ROW, top + 69), 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_stages"),
                tx(ShopLayoutDebug.TabWidget.STAGES_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.STAGES_ROW, top + 87), 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.stage_descriptions"),
                tx(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, top + 105), 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.tab_desc"),
                tx(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, left + LABEL_X),
                ty(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, top + 123), 0xFFFFFF);

        // 图标(不画 slot 背景)
        int iconX = tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + CONTROL_X + 2);
        int iconY = ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 48);
        if (!icon.isEmpty()) {
            g.renderItem(icon, iconX, iconY);
        }

        for (EditBox box : editBoxes()) {
            ShopTextures.input(g, box.getX() - 2, box.getY() - 1,
                    box.getWidth() + 4, 12, box.isFocused());
        }

        ShopTextures.renderWidgets(this, g, mouseX, mouseY, partialTick);

        if (!icon.isEmpty() && mouseX >= iconX - 2 && mouseX < iconX + 18
                && mouseY >= iconY && mouseY < iconY + 20) {
            g.renderTooltip(this.font, icon, mouseX, mouseY);
        }
        renderDebugOverlay(g);
    }

    private List<EditBox> editBoxes() {
        return List.of(nameBox, questsBox, stagesBox, stageDescriptionsBox, descBox);
    }

    private void renderDebugOverlay(GuiGraphics g) {
        if (!ShopLayoutDebug.isEnabled()) {
            return;
        }
        ShopLayoutDebug.TabWidget widget = ShopLayoutDebug.selectedTab();
        int x;
        int y;
        int w;
        int h;
        switch (widget) {
            case PANEL -> {
                x = tx(ShopLayoutDebug.TabWidget.PANEL, left);
                y = ty(ShopLayoutDebug.TabWidget.PANEL, top);
                w = GUI_W;
                h = GUI_H;
            }
            case TITLE -> {
                x = tx(ShopLayoutDebug.TabWidget.TITLE, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.TITLE, top + 6);
                w = GUI_W - 24;
                h = 18;
            }
            case NAME_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.NAME_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.NAME_ROW, top + 26);
                w = GUI_W - 24;
                h = 16;
            }
            case ICON_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.ICON_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.ICON_ROW, top + 46);
                w = GUI_W - 24;
                h = 18;
            }
            case QUESTS_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.QUESTS_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.QUESTS_ROW, top + 66);
                w = GUI_W - 24;
                h = 16;
            }
            case STAGES_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.STAGES_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.STAGES_ROW, top + 84);
                w = GUI_W - 24;
                h = 16;
            }
            case STAGE_DESCRIPTIONS_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.STAGE_DESCRIPTIONS_ROW, top + 102);
                w = GUI_W - 24;
                h = 16;
            }
            case DESCRIPTION_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.DESCRIPTION_ROW, top + 120);
                w = GUI_W - 24;
                h = 16;
            }
            case VISIBILITY_ROW -> {
                x = tx(ShopLayoutDebug.TabWidget.VISIBILITY_ROW, left + LABEL_X);
                y = ty(ShopLayoutDebug.TabWidget.VISIBILITY_ROW, top + 138);
                w = GUI_W - 24;
                h = 20;
            }
            case DELETE_BUTTON -> {
                x = tx(ShopLayoutDebug.TabWidget.DELETE_BUTTON, left + 12);
                y = ty(ShopLayoutDebug.TabWidget.DELETE_BUTTON, top + 154);
                w = 110;
                h = 16;
            }
            case SAVE_BUTTON -> {
                x = tx(ShopLayoutDebug.TabWidget.SAVE_BUTTON, left + 128);
                y = ty(ShopLayoutDebug.TabWidget.SAVE_BUTTON, top + 154);
                w = 110;
                h = 16;
            }
            case CANCEL_BUTTON -> {
                x = tx(ShopLayoutDebug.TabWidget.CANCEL_BUTTON, left + 12);
                y = ty(ShopLayoutDebug.TabWidget.CANCEL_BUTTON, top + 176);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
