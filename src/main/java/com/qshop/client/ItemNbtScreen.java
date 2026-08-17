package com.qshop.client;

import com.qshop.util.QText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 物品详情编辑子窗口:数量 + NBT(多行编辑,支持 Ctrl+A/X/C/V 等快捷键)。
 * 保存后通过回调把新物品写回父界面(展示物品 / 交易物品均可编辑)。
 */
public class ItemNbtScreen extends Screen {

    private static final int GUI_W = 250;
    private static final int GUI_H = 280;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 72;

    private final Screen parent;
    private final ItemStack stack;
    private final Consumer<ItemStack> onSave;
    private int left;
    private int top;
    private EditBox countBox;
    private MultilineTextBox nbtBox;

    public ItemNbtScreen(Screen parent, ItemStack stack, Consumer<ItemStack> onSave) {
        super(Component.literal(""));
        this.parent = parent;
        this.stack = stack;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;

        countBox = new EditBox(this.font, left + CONTROL_X + 2, top + 28, 66, 14, Component.literal(""));
        countBox.setMaxLength(7);
        countBox.setFilter(s -> s.matches("\\d{0,7}"));
        countBox.setValue(String.valueOf(stack.getCount()));
        countBox.setBordered(false);
        addRenderableWidget(countBox);

        // NBT 框下移,避免与"NBT"标签重叠
        nbtBox = new MultilineTextBox(left + LABEL_X + 2, top + 58, 222, 180, this.font);
        nbtBox.setValue(stack.getTag() == null ? "" : new SnbtPrinterTagVisitor().visit(stack.getTag()));
        addRenderableWidget(nbtBox);

        addRenderableWidget(new QButton(left + 12, top + 261, 110, 16,
                Component.translatable("qshop.gui.save"), b -> save()));
        addRenderableWidget(new QButton(left + 128, top + 261, 110, 16,
                Component.translatable("qshop.gui.cancel"), b -> back()));
    }

    private void back() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        back();
    }

    private void save() {
        ItemStack out = stack.copy();
        int c = 1;
        try {
            c = Integer.parseInt(countBox.getValue().trim());
        } catch (Exception ignored) {
        }
        out.setCount(Mth.clamp(c, 1, 1000));
        String nbt = nbtBox.getValue().trim();
        if (nbt.isEmpty()) {
            out.setTag(null);
        } else {
            try {
                out.setTag(TagParser.parseTag(nbt));
            } catch (Exception ignored) {
                // 非法 SNBT 保留原 NBT
            }
        }
        onSave.accept(out);
        back();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        countBox.setFocused(false);
        nbtBox.setFocused(false);
        if (countBox.mouseClicked(mouseX, mouseY, button)) {
            countBox.setFocused(true);
            return true;
        }
        if (nbtBox.mouseClicked(mouseX, mouseY, button)) {
            nbtBox.setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (countBox.isFocused() && countBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nbtBox.isFocused() && nbtBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (countBox.isFocused() && countBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (nbtBox.isFocused() && nbtBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ShopTextures.panelEdit(g, left, top);

        if (!stack.isEmpty()) {
            // 顶部图标/名称与交易条目编辑界面保持一致
            g.renderItem(stack, left + LABEL_X, top + 2);
            g.drawString(this.font, QText.clip(stack.getHoverName().getString(), this.font, 180),
                    left + 32, top + 5, 0xFFFFFF);
        }

        g.drawString(this.font, Component.translatable("qshop.gui.count_field"), left + LABEL_X, top + 29, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("qshop.gui.nbt"), left + LABEL_X, top + 47, 0xFFFFFF);

        // 输入框背景(无边框 EditBox 文字画在左上角,材质向上/左扩展 2px 使其视觉居中)
        ShopTextures.input(g, left + CONTROL_X, top + 27, 70, 12, countBox.isFocused());
        ShopTextures.input(g, left + LABEL_X, top + 57, 226, 182, nbtBox.isFocused());

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
