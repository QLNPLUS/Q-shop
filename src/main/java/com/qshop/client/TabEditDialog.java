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
    private QButton iconEditButton;
    private ItemStack icon = ItemStack.EMPTY;
    // 状态字段:从选择器/详情子窗口返回时 init() 会重建控件,必须用字段恢复输入,避免丢失
    private String nameStr = "";
    private String descStr = "";
    private String questsStr = "";
    private String stagesStr = "";

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
        }
    }

    @Override
    protected void init() {
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        if (tab == null) {
            Minecraft.getInstance().setScreen(new ShopScreen(data, 0, true, data.activeTab));
            return;
        }
        nameBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 28, 164, 14, Component.literal("")).allowSectionSign();
        nameBox.setMaxLength(32);
        nameBox.setBordered(false);
        nameBox.setValue(nameStr);
        nameBox.setResponder(s -> nameStr = s == null ? "" : s);
        addRenderableWidget(nameBox);

        // 任务/阶段要求(逗号分隔多个;子商店未满足时非编辑玩家看不到该子商店)
        questsBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 68, 164, 14, Component.literal("")).allowSectionSign();
        questsBox.setMaxLength(200);
        questsBox.setBordered(false);
        questsBox.setValue(questsStr);
        questsBox.setResponder(s -> questsStr = s == null ? "" : s);
        addRenderableWidget(questsBox);
        stagesBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 86, 164, 14, Component.literal("")).allowSectionSign();
        stagesBox.setMaxLength(200);
        stagesBox.setBordered(false);
        stagesBox.setValue(stagesStr);
        stagesBox.setResponder(s -> stagesStr = s == null ? "" : s);
        addRenderableWidget(stagesBox);

        // 描述(悬停在子商店 tab 上时以 tooltip 显示;支持 \n 换行)
        descBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 104, 164, 14, Component.literal("")).allowSectionSign();
        descBox.setMaxLength(500);
        descBox.setBordered(false);
        descBox.setValue(descStr);
        descBox.setResponder(s -> descStr = s == null ? "" : s);
        addRenderableWidget(descBox);

        addRenderableWidget(new QButton(left + ITEM_ACTION_X, top + 48, 46, 14,
                Component.translatable("qshop.gui.select"), b -> openPicker(s -> icon = s)));
        iconEditButton = addRenderableWidget(new QButton(left + ITEM_ACTION_X + 50, top + 48, 46, 14,
                Component.translatable("qshop.gui.edit_item"), b -> openNbtEditor()));
        iconEditButton.active = !icon.isEmpty();
        addRenderableWidget(new QButton(left + ITEM_ACTION_X + 100, top + 48, 44, 14,
                Component.translatable("qshop.gui.clear"), b -> {
                    icon = ItemStack.EMPTY;
                    iconEditButton.active = false;
                }));

        addRenderableWidget(new QButton(left + 12, top + 126, 110, 16,
                Component.translatable("qshop.gui.delete_tab"), b -> {
                    // 用服务端子商店序号发送(隐藏过滤后可见序号会错位)
                    QShopNetwork.sendToServer(new RemoveTabPacket(data.shopId, tab.serverIndex));
                    back();
                }));
        addRenderableWidget(new QButton(left + 128, top + 126, 110, 16,
                Component.translatable("qshop.gui.save"), b -> save()));
        addRenderableWidget(new QButton(left + 12, top + 158, 226, 16,
                Component.translatable("qshop.gui.cancel"), b -> back()));
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
                splitList(questsStr), splitList(stagesStr)));
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        nameBox.setFocused(false);
        descBox.setFocused(false);
        questsBox.setFocused(false);
        stagesBox.setFocused(false);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.background(g, this.width, this.height);
        ShopTextures.panelTab(g, left, top);

        g.drawString(this.font, Component.translatable("qshop.gui.edit_tab"), left + LABEL_X, top + 8, 0xFFFFFF);

        g.drawString(this.font, Component.translatable("qshop.gui.tab_name"), left + LABEL_X, top + 29, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.tab_icon"), left + LABEL_X, top + 51, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_quests"), left + LABEL_X, top + 69, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.req_stages"), left + LABEL_X, top + 87, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.tab_desc"), left + LABEL_X, top + 105, 0xFFFFFF);

        // 图标(不画 slot 背景)
        if (!icon.isEmpty()) {
            g.renderItem(icon, left + CONTROL_X + 2, top + 48);
        }

        ShopTextures.input(g, left + CONTROL_X, top + 27, 168, 12, nameBox.isFocused());
        ShopTextures.input(g, left + CONTROL_X, top + 67, 168, 12, questsBox.isFocused());
        ShopTextures.input(g, left + CONTROL_X, top + 85, 168, 12, stagesBox.isFocused());
        ShopTextures.input(g, left + CONTROL_X, top + 103, 168, 12, descBox.isFocused());

        ShopTextures.renderWidgets(this, g, mouseX, mouseY, partialTick);

        if (!icon.isEmpty() && mouseX >= left + CONTROL_X && mouseX < left + CONTROL_X + 20
                && mouseY >= top + 48 && mouseY < top + 68) {
            g.renderTooltip(this.font, icon, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
