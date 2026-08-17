package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * 允许输入 §(颜色代码符号)的输入框。
 * <p>原版 EditBox 的两条路径都会把 '§'(0xA7) 干掉:
 * <ul>
 *   <li>{@code charTyped} 走聊天字符过滤(StringUtil.isAllowedChatCharacter),显式拒绝 §;</li>
 *   <li>{@code insertText}(粘贴/插入)内部调用 StringUtil.filterText,同样剥离 §。</li>
 * </ul>
 * 这里对 § 绕过上述过滤:输入时直接读写 value(经 setValue 只校验 predicate、不过滤 §),
 * 粘贴时逐字符插入,§ 单独处理。
 * <p>渲染:原版(以及 Component.literal)走整串 visitor 会解析 §;这里构造
 * <b>逐字符 FormattedCharSequence</b> 渲染,彻底绕过解析,输入框内**原样显示**
 * "§7aaa" 字面量;保存后由 QText.parse 解释,其他界面显示为灰色 "aaa"。
 */
public class QEditBox extends EditBox {

    private boolean allowSectionSign = false;

    public QEditBox(Font font, int x, int y, int w, int h, Component message) {
        super(font, x, y, w, h, message);
    }

    /** 开启 § 输入(仅对文本类输入框调用;数字类输入框保持过滤) */
    public QEditBox allowSectionSign() {
        this.allowSectionSign = true;
        return this;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (allowSectionSign && codePoint == '\u00A7') {
            if (canConsumeInput()) {
                insertSectionSign();
                return true;
            }
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 粘贴(Ctrl+V):逐字符插入,保留 §(原版 insertText 会剥离)
        if (allowSectionSign && keyCode == 86 && Screen.hasControlDown()) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                for (int i = 0; i < clip.length(); i++) {
                    char c = clip.charAt(i);
                    if (c == '\u00A7') {
                        insertSectionSign();
                    } else {
                        insertText(String.valueOf(c));
                    }
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 在当前光标处插入一个 §(绕过 filterText 过滤) */
    private void insertSectionSign() {
        String v = getValue();
        int pos = Math.min(getCursorPosition(), v.length());
        String nv = v.substring(0, pos) + '\u00A7' + v.substring(pos);
        setValue(nv);
        setCursorPosition(pos + 1);
    }

    // ---------------- 渲染:字面显示 § ----------------

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String value = getValue();
        int innerW = Math.max(0, getWidth() - 4); // bordered=false
        int cursor = Math.min(getCursorPosition(), value.length());
        int cursorPx = font.width(value.substring(0, cursor));
        int displayPos = displayPosFor(value, cursor, cursorPx, innerW);

        // 从 displayPos 起截取能放下的子串(按像素宽度)
        String clipped = font.plainSubstrByWidth(value.substring(Math.min(displayPos, value.length())), innerW);

        // 逐字符 FormattedCharSequence:彻底绕过 § 解析,字面渲染 "§7aaa"
        FormattedCharSequence seq = sink -> {
            for (int i = 0; i < clipped.length(); i++) {
                if (!sink.accept(i, Style.EMPTY, clipped.charAt(i))) {
                    return false;
                }
            }
            return true;
        };

        // 文字基线 = getY()+1(相比上一版下移 1px,与行标签对齐)
        int textTop = getY() + 1;
        g.drawString(font, seq, getX() + 4, textTop, 0xFFFFFF);

        // 光标(聚焦时闪烁;相比上一版上移 3px)
        if (isFocused() && cursor >= displayPos) {
            int rel = cursorPx - font.width(value.substring(0, displayPos));
            int cx = getX() + 4 + Math.min(rel, innerW);
            if (((System.currentTimeMillis() / 500) & 1) == 0) {
                g.fill(cx, getY(), cx + 1, getY() + getHeight() - 4, 0xFFE0E0E0);
            }
        }
    }

    /** 计算滚动起点字符序号,使光标保持在可视区域内 */
    private int displayPosFor(String value, int cursor, int cursorPx, int innerW) {
        if (cursorPx <= innerW) {
            return 0;
        }
        int target = cursorPx - innerW;
        int acc = 0;
        int disp = 0;
        for (int i = 0; i < cursor; i++) {
            int w = Minecraft.getInstance().font.width(String.valueOf(value.charAt(i)));
            if (acc + w > target) {
                break;
            }
            acc += w;
            disp = i + 1;
        }
        return disp;
    }
}

