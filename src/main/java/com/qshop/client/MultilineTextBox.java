package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 多行文本编辑器:支持回车换行、方向键、Home/End、Shift 选区、Ctrl+A/X/C/V 快捷键、滚轮滚动。
 * 用于 NBT 编辑(大文本框)。
 */
public class MultilineTextBox extends AbstractWidget {

    private static final int MAX_LENGTH = 6000;

    private final Font font;
    private String value = "";
    private int cursor = 0;
    private int selStart = -1;
    private int scrollLines = 0;
    /** Only follow the caret after an edit/navigation action, never every render frame. */
    private boolean cursorVisibilityDirty = true;

    public MultilineTextBox(int x, int y, int w, int h, Font font) {
        super(x, y, w, h, Component.literal(""));
        this.font = font;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String v) {
        value = v == null ? "" : v;
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
        }
        cursor = Math.min(cursor, value.length());
        selStart = -1;
        scrollLines = 0;
        cursorVisibilityDirty = true;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
    }

    // ---------------- 文本行运算 ----------------

    private int lineStart(int line) {
        int idx = 0;
        for (int i = 0; i < line; i++) {
            idx = value.indexOf('\n', idx) + 1;
        }
        return Math.min(idx, value.length());
    }

    private int lineEnd(int line) {
        int s = lineStart(line);
        int nl = value.indexOf('\n', s);
        return nl < 0 ? value.length() : nl;
    }

    private int lineCount() {
        int n = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private int lineOf(int idx) {
        int line = 0;
        for (int i = 0; i < idx && i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int colOf(int idx) {
        return idx - lineStart(lineOf(idx));
    }

    // ---------------- 编辑操作 ----------------

    private void insertText(String text) {
        int a = selStart >= 0 ? Math.min(selStart, cursor) : cursor;
        int b = selStart >= 0 ? Math.max(selStart, cursor) : cursor;
        String next = value.substring(0, a) + text + value.substring(b);
        if (next.length() > MAX_LENGTH) {
            return;
        }
        if (next.equals(value) && cursor == a) {
            return;
        }
        value = next;
        cursor = a + text.length();
        selStart = -1;
        cursorVisibilityDirty = true;
    }

    private void deleteBack() {
        if (selStart >= 0 && selStart != cursor) {
            insertText("");
            return;
        }
        if (cursor > 0) {
            value = value.substring(0, cursor - 1) + value.substring(cursor);
            cursor--;
            cursorVisibilityDirty = true;
        }
    }

    private void deleteForward() {
        if (selStart >= 0 && selStart != cursor) {
            insertText("");
            return;
        }
        if (cursor < value.length()) {
            value = value.substring(0, cursor) + value.substring(cursor + 1);
            cursorVisibilityDirty = true;
        }
    }

    private void copySelection() {
        if (selStart < 0 || selStart == cursor) {
            return;
        }
        int a = Math.min(selStart, cursor);
        int b = Math.max(selStart, cursor);
        Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(a, b));
    }

    private void moveCursor(int dir, boolean shift, boolean ctrl) {
        int oldCursor = cursor;
        if (shift && selStart < 0) {
            selStart = cursor;
        }
        if (!shift) {
            selStart = -1;
        }
        if (ctrl) {
            // 按词移动
            int idx = cursor;
            if (dir > 0) {
                while (idx < value.length() && value.charAt(idx) != ' ') {
                    idx++;
                }
                while (idx < value.length() && value.charAt(idx) == ' ') {
                    idx++;
                }
            } else {
                while (idx > 0 && value.charAt(idx - 1) == ' ') {
                    idx--;
                }
                while (idx > 0 && value.charAt(idx - 1) != ' ') {
                    idx--;
                }
            }
            cursor = Mth.clamp(idx, 0, value.length());
        } else {
            cursor = Mth.clamp(cursor + dir, 0, value.length());
        }
        if (cursor != oldCursor) {
            cursorVisibilityDirty = true;
        }
    }

    private void moveLine(int dir, boolean shift) {
        int oldCursor = cursor;
        if (shift && selStart < 0) {
            selStart = cursor;
        }
        if (!shift) {
            selStart = -1;
        }
        int line = lineOf(cursor);
        int col = colOf(cursor);
        int target = line + dir;
        if (target < 0 || target >= lineCount()) {
            return;
        }
        int s = lineStart(target);
        int len = lineEnd(target) - s;
        cursor = s + Math.min(col, len);
        if (cursor != oldCursor) {
            cursorVisibilityDirty = true;
        }
    }

    private void scrollToCursor() {
        int visibleLines = Math.max(1, getHeight() / font.lineHeight);
        int line = lineOf(cursor);
        if (line < scrollLines) {
            scrollLines = line;
        } else if (line >= scrollLines + visibleLines) {
            scrollLines = line - visibleLines + 1;
        }
    }

    // ---------------- 输入路由 ----------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selStart = 0;
            cursor = value.length();
            cursorVisibilityDirty = true;
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copySelection();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            copySelection();
            insertText("");
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            insertText(clip == null ? "" : clip.replace("\r\n", "\n").replace('\r', '\n'));
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                deleteBack();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                deleteForward();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertText("\n");
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(-1, shift, ctrl);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(1, shift, ctrl);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveLine(-1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveLine(1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                int oldCursor = cursor;
                cursor = ctrl ? 0 : lineStart(lineOf(cursor));
                if (!shift) {
                    selStart = -1;
                }
                if (cursor != oldCursor) {
                    cursorVisibilityDirty = true;
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                int oldCursor = cursor;
                cursor = ctrl ? value.length() : lineEnd(lineOf(cursor));
                if (!shift) {
                    selStart = -1;
                }
                if (cursor != oldCursor) {
                    cursorVisibilityDirty = true;
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                insertText("    ");
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        if (codePoint >= 32) {
            insertText(String.valueOf(codePoint));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            setFocused(true);
            int line = Mth.clamp(scrollLines + (int) ((mouseY - getY()) / font.lineHeight), 0, lineCount() - 1);
            int s = lineStart(line);
            int e = lineEnd(line);
            String text = value.substring(s, e);
            int mx = (int) (mouseX - getX() - 2);
            int col = 0;
            for (int i = 0; i < text.length(); i++) {
                if (font.width(text.substring(0, i + 1)) <= mx) {
                    col = i + 1;
                } else {
                    break;
                }
            }
            cursor = s + col;
            selStart = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int visibleLines = Math.max(1, getHeight() / font.lineHeight);
        scrollLines = Mth.clamp(scrollLines - (int) delta, 0, Math.max(0, lineCount() - visibleLines));
        return true;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int lineH = font.lineHeight;
        int visibleLines = Math.max(1, h / lineH);
        if (cursorVisibilityDirty) {
            scrollToCursor();
            cursorVisibilityDirty = false;
        }
        scrollLines = Mth.clamp(scrollLines, 0, Math.max(0, lineCount() - visibleLines));
        int selA = selStart >= 0 ? Math.min(selStart, cursor) : -1;
        int selB = selStart >= 0 ? Math.max(selStart, cursor) : -1;
        ShopTextures.enableScissor(g, x, y, w, h);
        for (int i = 0; i < visibleLines; i++) {
            int line = scrollLines + i;
            if (line >= lineCount()) {
                break;
            }
            int s = lineStart(line);
            int e = lineEnd(line);
            int ty = y + 1 + i * lineH;
            // 选区高亮
            if (selA >= 0 && selA < e && selB > s) {
                int a = Math.max(s, selA);
                int b = Math.min(e, selB);
                if (a < b) {
                    int sx = x + 2 + font.width(value.substring(s, a));
                    int sw = font.width(value.substring(a, b));
                    g.fill(sx, ty - 1, sx + sw, ty + lineH - 1, 0x804040A0);
                }
            }
            g.drawString(font, value.substring(s, e), x + 2, ty, 0xFFFFFFFF);
            // 光标(闪烁)
            if (isFocused() && line == lineOf(cursor) && (System.currentTimeMillis() / 500) % 2 == 0) {
                int col = colOf(cursor);
                int cx = x + 2 + font.width(value.substring(s, s + col));
                g.fill(cx, ty - 1, cx + 1, ty + lineH - 1, 0xFFFFFFFF);
            }
        }
        ShopTextures.disableScissor(g);
    }
}
