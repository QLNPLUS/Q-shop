package com.qshop.client;

import com.qshop.net.EditShopInfoPacket;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 编辑商店信息:显示名 + 图标 + 默认货币(编辑模式下,在 tab 栏顶部右键商店名呼出)。
 * 支持 § 颜色代码,保存后商店名/标题立即生效。
 */
public class ShopInfoDialog extends QShopScreen {

    private static final int GUI_W = 250;
    private static final int GUI_H = 200;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 72;
    private static final int ITEM_ACTION_X = 94;

    private final OpenShopPacket data;
    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox currencyBox;
    private QButton iconEditButton;
    private ItemStack icon = ItemStack.EMPTY;
    // 状态字段:从选择器返回时 init() 重建控件,用字段恢复,避免输入丢失
    private String nameStr = "";
    private String currencyStr = "";

    public ShopInfoDialog(OpenShopPacket data) {
        super(Component.translatable("qshop.gui.edit_shop"));
        this.data = data;
        this.nameStr = data.shopName;
        this.icon = data.icon.copy();
        this.currencyStr = data.shopCurrency == null ? "" : data.shopCurrency;
    }

    @Override
    protected int qshopContentWidth() {
        return GUI_W;
    }

    @Override
    protected int qshopContentHeight() {
        return GUI_H;
    }

    @Override
    protected void init() {
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;

        nameBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 28, 164, 14, Component.literal("")).allowSectionSign();
        nameBox.setMaxLength(64);
        nameBox.setBordered(false);
        nameBox.setValue(nameStr);
        nameBox.setResponder(s -> nameStr = s == null ? "" : s);
        addRenderableWidget(nameBox);

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

        currencyBox = new QEditBox(this.font, left + CONTROL_X + 2, top + 72, 164, 14, Component.literal(""));
        currencyBox.setMaxLength(32);
        currencyBox.setBordered(false);
        currencyBox.setValue(currencyStr);
        currencyBox.setResponder(s -> currencyStr = s == null ? "" : s);
        addRenderableWidget(currencyBox);

        addRenderableWidget(new QButton(left + 30, top + 106, 90, 16,
                Component.translatable("qshop.gui.save"), b -> save()));
        addRenderableWidget(new QButton(left + 130, top + 106, 90, 16,
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
        QShopNetwork.sendToServer(new EditShopInfoPacket(data.shopId, nameStr, icon, currencyStr));
        back();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox.isFocused() && nameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currencyBox.isFocused() && currencyBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox.isFocused() && nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (currencyBox.isFocused() && currencyBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        nameBox.setFocused(false);
        currencyBox.setFocused(false);
        if (nameBox.mouseClicked(mouseX, mouseY, button)) {
            nameBox.setFocused(true);
            return true;
        }
        if (currencyBox.mouseClicked(mouseX, mouseY, button)) {
            currencyBox.setFocused(true);
            return true;
        }
        return super.mouseClickedContent(mouseX, mouseY, button);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.panelTab(g, left, top);

        g.drawString(this.font, Component.translatable("qshop.gui.edit_shop"), left + LABEL_X, top + 8, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.shop_name"), left + LABEL_X, top + 29, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.tab_icon"), left + LABEL_X, top + 51, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.shop_currency"), left + LABEL_X, top + 73, 0xFFFFFF);

        // 图标(不画 slot 背景)
        if (!icon.isEmpty()) {
            g.renderItem(icon, left + CONTROL_X + 2, top + 48);
        }

        ShopTextures.input(g, left + CONTROL_X, top + 27, 168, 12, nameBox.isFocused());
        ShopTextures.input(g, left + CONTROL_X, top + 71, 168, 12, currencyBox.isFocused());

        // 可用货币提示
        if (!data.currencies.isEmpty()) {
            String hint = data.currencies.stream()
                    .map(c -> c.id)
                    .collect(java.util.stream.Collectors.joining(", "));
            g.drawString(this.font, Component.literal(Component.translatable("qshop.gui.available").getString() + ": " + hint),
                    left + LABEL_X, top + 89, 0xFFFFFF);
        }

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
