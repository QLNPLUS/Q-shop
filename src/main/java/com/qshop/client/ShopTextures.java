package com.qshop.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qshop.config.QShopCommonConfig;
import com.qshop.shop.ShopEntryType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * QShop GUI 材质(assets/qshop/textures/gui/ 下的拆分 PNG,每个元素一个文件,便于单独修改):
 *   panel.png / panel_wide.png / panel_edit.png    面板(250x200 / 280x200 / 250x250)
 *   slot*.png                     格子 20x20(普通/悬停/编辑/编辑悬停)
 *   button*.png                   按钮 60x16(普通/悬停/禁用)
 *   input*.png                    输入框 96x12(普通/聚焦)
 *   track.png / knob*.png         滑块轨道 96x8 / 把手 8x14
 *   checkbox_*.png                勾选框 12x12
 *   close/plus/minus/trash*.png   小图标 12x12
 *
 * <p>带边框的元素使用九宫格绘制,边框保持 1px 不拉伸。
 */
public final class ShopTextures {

    private static final ResourceLocation PANEL = rl("panel");
    private static final ResourceLocation PANEL_WIDE = rl("panel_wide");
    private static final ResourceLocation PANEL_EDIT = rl("panel_edit");
    private static final ResourceLocation PANEL_ADD = rl("panel_add");
    private static final ResourceLocation PANEL_TAB = rl("panel_tab");
    private static final ResourceLocation PANEL_PICKER = rl("panel_picker");
    private static final ResourceLocation SLOT = rl("slot");
    private static final ResourceLocation SLOT_HOVER = rl("slot_hover");
    private static final ResourceLocation SLOT_EDIT = rl("slot_edit");
    private static final ResourceLocation SLOT_EDIT_HOVER = rl("slot_edit_hover");
    private static final ResourceLocation BTN = rl("button");
    private static final ResourceLocation BTN_HOVER = rl("button_hover");
    private static final ResourceLocation BTN_DISABLED = rl("button_disabled");
    private static final ResourceLocation INPUT = rl("input");
    private static final ResourceLocation INPUT_FOCUS = rl("input_focus");
    private static final ResourceLocation TRACK = rl("track");
    private static final ResourceLocation KNOB = rl("knob");
    private static final ResourceLocation KNOB_HOVER = rl("knob_hover");
    private static final ResourceLocation CHECK_OFF = rl("checkbox_off");
    private static final ResourceLocation CHECK_ON = rl("checkbox_on");
    private static final ResourceLocation CHECK_OFF_HOVER = rl("checkbox_off_hover");
    private static final ResourceLocation CHECK_ON_HOVER = rl("checkbox_on_hover");
    private static final ResourceLocation CLOSE = rl("close");
    private static final ResourceLocation CLOSE_HOVER = rl("close_hover");
    private static final ResourceLocation LAYOUT = rl("layout");
    private static final ResourceLocation LAYOUT_HOVER = rl("layout_hover");
    private static final ResourceLocation PLUS = rl("plus");
    private static final ResourceLocation PLUS_HOVER = rl("plus_hover");
    private static final ResourceLocation MINUS = rl("minus");
    private static final ResourceLocation MINUS_HOVER = rl("minus_hover");
    private static final ResourceLocation TRASH = rl("trash");
    private static final ResourceLocation TRASH_HOVER = rl("trash_hover");
    private static final ResourceLocation BUY = rl("buy");
    private static final ResourceLocation SELL = rl("sell");
    private static final ResourceLocation BARTER = rl("barter");
    private static final ResourceLocation COMMAND = rl("command");
    private static final ResourceLocation PRICE_BAR = rl("price_bar");
    private static final ResourceLocation TAB_BAR = rl("tab_bar");
    private static final ResourceLocation TAB_BTN = rl("tab_btn");
    private static final ResourceLocation TAB_BTN_HOVER = rl("tab_btn_hover");
    private static final ResourceLocation TAB_BTN_SEL = rl("tab_btn_sel");
    private static final ResourceLocation MENU_PANEL = rl("menu_panel");
    private static final ResourceLocation TRADE_PANEL = rl("trade_panel");
    private static final ResourceLocation SCROLL_TRACK = rl("scroll_track");
    private static final ResourceLocation SCROLL_KNOB = rl("scroll_knob");

    private ShopTextures() {
    }

    private static ResourceLocation rl(String name) {
        return new ResourceLocation("qshop", "textures/gui/" + name + ".png");
    }

    // ---------------- 面板 ----------------

    public static void panel(GuiGraphics g, int x, int y) {
        g.blit(PANEL, x, y, 0, 0, 250, 200, 250, 200);
    }

    /** 8x4 布局主面板(280x200),独立材质以便单独换皮肤。 */
    public static void panelWide(GuiGraphics g, int x, int y) {
        g.blit(PANEL_WIDE, x, y, 0, 0, 280, 200, 280, 200);
    }

    /** 编辑界面高面板(250x280) */
    public static void panelEdit(GuiGraphics g, int x, int y) {
        g.blit(PANEL_EDIT, x, y, 0, 0, 250, 280, 250, 280);
    }

    /** 添加条目窗口面板(250x200,绿色调描边) */
    public static void panelAdd(GuiGraphics g, int x, int y) {
        g.blit(PANEL_ADD, x, y, 0, 0, 250, 200, 250, 200);
    }

    /** 编辑子商店窗口面板(250x200,蓝色调描边) */
    public static void panelTab(GuiGraphics g, int x, int y) {
        g.blit(PANEL_TAB, x, y, 0, 0, 250, 200, 250, 200);
    }

    /** 物品选择器面板(250x214,琥珀色调描边;比标准面板高 14px,容纳 5 行物品网格) */
    public static void panelPicker(GuiGraphics g, int x, int y) {
        g.blit(PANEL_PICKER, x, y, 0, 0, 250, 214, 250, 214);
    }

    // ---------------- 左侧 tab 栏 / 子商店按钮 ----------------

    /** 左侧 tab 栏背景(46x200 固定尺寸,不拉伸;修改 tab_bar.png 即可换皮肤) */
    public static void tabBar(GuiGraphics g, int x, int y) {
        g.blit(TAB_BAR, x, y, 0, 0, 46, 200, 46, 200);
    }

    /** tab 按钮九宫格边距(4px) */
    private static final int TAB_BTN_BORDER = 4;

    /** 子商店按钮 40x24(选中/悬停三态) */
    public static void tabButton(GuiGraphics g, int x, int y, boolean selected, boolean hover) {
        ResourceLocation tex = selected ? TAB_BTN_SEL : (hover ? TAB_BTN_HOVER : TAB_BTN);
        blit9(g, tex, x, y, 40, 24, 40, 24, TAB_BTN_BORDER);
    }

    /** 子商店按钮命中检测 */
    public static boolean tabButtonHit(int x, int y, double mx, double my) {
        return hitTest(TAB_BTN, x, y, 40, 24, mx, my, TAB_BTN_BORDER, 40, 24);
    }

    // ---------------- 悬浮面板(右键菜单 / 交易窗) ----------------

    /** 右键菜单面板(96x80 九宫格,border 4) */
    public static void menuPanel(GuiGraphics g, int x, int y, int w, int h) {
        blit9(g, MENU_PANEL, x, y, w, h, 96, 80, 4);
    }

    /** 交易窗面板(150x128 九宫格,border 6) */
    public static void tradePanel(GuiGraphics g, int x, int y, int w, int h) {
        blit9(g, TRADE_PANEL, x, y, w, h, 150, 128, 6);
    }

    // ---------------- tab 列表渐变遮罩 / 滚动条 ----------------

    /** 遮罩高度(px) */
    private static final int MASK_H = 10;

    /** 顶部渐隐遮罩(实心 → 透明)，使用 GuiGraphics 以继承调用方的 Z 层。 */
    public static void tabFadeTop(GuiGraphics g, int x, int y, int w) {
        if (!QShopCommonConfig.showFadeMasks()) {
            return;
        }
        int c = QShopCommonConfig.fadeColor();
        int r = (c >> 16) & 0xFF;
        int gn = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        for (int i = 0; i < MASK_H; i++) {
            int a = 255 - (int) (255 * (i / (float) MASK_H));
            g.fill(x, y + i, x + w, y + i + 1, (a << 24) | (r << 16) | (gn << 8) | b);
        }
    }

    /** 底部渐隐遮罩(透明 → 实心) */
    public static void tabFadeBottom(GuiGraphics g, int x, int y, int w) {
        if (!QShopCommonConfig.showFadeMasks()) {
            return;
        }
        int c = QShopCommonConfig.fadeColor();
        int r = (c >> 16) & 0xFF;
        int gn = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        for (int i = 0; i < MASK_H; i++) {
            int a = (int) (255 * (i / (float) MASK_H));
            g.fill(x, y + i, x + w, y + i + 1, (a << 24) | (r << 16) | (gn << 8) | b);
        }
    }

    /** 滚动条轨道(3px,9 宫格拉伸,border 1) */
    public static void scrollTrack(GuiGraphics g, int x, int y, int h) {
        blit9(g, SCROLL_TRACK, x, y, 3, h, 8, 16, 1);
    }

    /** 滚动条滑块(5px,9 宫格拉伸,border 2) */
    public static void scrollKnob(GuiGraphics g, int x, int y, int h) {
        blit9(g, SCROLL_KNOB, x, y, 5, h, 16, 16, 2);
    }

    // ---------------- 九宫格 ----------------

    /** 以 border 像素边框做九宫格拉伸绘制(整个贴图 tw x th),源尺寸与目标尺寸分离,不会平铺 */
    private static void blit9(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int tw, int th, int border) {
        int iw = tw - border * 2;
        int ih = th - border * 2;
        int mw = w - border * 2;
        int mh = h - border * 2;
        // 四角(1:1)
        g.blit(tex, x, y, 0, 0, border, border, tw, th);
        g.blit(tex, x + w - border, y, tw - border, 0, border, border, tw, th);
        g.blit(tex, x, y + h - border, 0, th - border, border, border, tw, th);
        g.blit(tex, x + w - border, y + h - border, tw - border, th - border, border, border, tw, th);
        // 四边(单轴拉伸)
        if (mw > 0) {
            g.blit(tex, x + border, y, mw, border, border, 0, iw, border, tw, th);
            g.blit(tex, x + border, y + h - border, mw, border, border, th - border, iw, border, tw, th);
        }
        if (mh > 0) {
            g.blit(tex, x, y + border, border, mh, 0, border, border, ih, tw, th);
            g.blit(tex, x + w - border, y + border, border, mh, tw - border, border, border, ih, tw, th);
        }
        // 中心(双轴拉伸)
        if (mw > 0 && mh > 0) {
            g.blit(tex, x + border, y + border, mw, mh, border, border, iw, ih, tw, th);
        }
    }

    // ---------------- 裁剪 ----------------

    /**
     * 启用裁剪(左上角坐标 + 宽高,自动换算 GUI 缩放与 Y 翻转)。
     * 先 flush 保证裁剪只影响后续绘制的元素。
     */
    public static void enableScissor(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                (int) (x * scale),
                window.getHeight() - (int) ((y + h) * scale),
                (int) (w * scale),
                (int) (h * scale));
    }

    public static void disableScissor(GuiGraphics g) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    // ---------------- 格子 ----------------

    /** 格子九宫格边距(3px 边框区,拉伸时边框保持清晰) */
    private static final int SLOT_BORDER = 3;

    public static void slot(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean edit) {
        ResourceLocation tex = hover ? (edit ? SLOT_EDIT_HOVER : SLOT_HOVER) : (edit ? SLOT_EDIT : SLOT);
        blit9(g, tex, x, y, w, h, 20, 20, SLOT_BORDER);
    }

    // ---------------- 按钮 ----------------

    /** 按钮九宫格边距(保留更多两侧渐变/描边) */
    private static final int BTN_BORDER = 4;

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean enabled) {
        ResourceLocation tex = enabled ? (hover ? BTN_HOVER : BTN) : BTN_DISABLED;
        blit9(g, tex, x, y, w, h, 60, 16, BTN_BORDER);
    }

    // ---------------- 透明度命中检测 ----------------

    private static final Map<ResourceLocation, NativeImage> IMG_CACHE = new HashMap<>();

    private static NativeImage pixels(ResourceLocation tex) {
        NativeImage img = IMG_CACHE.get(tex);
        if (img != null) {
            return img;
        }
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(tex);
            if (opt.isPresent()) {
                try (var in = opt.get().open()) {
                    img = NativeImage.read(in);
                }
            }
        } catch (Exception ignored) {
        }
        if (img == null) {
            return null;
        }
        IMG_CACHE.put(tex, img);
        return img;
    }

    /** 九宫格拉伸下,屏幕局部坐标 → 材质归一化坐标(0..1) */
    private static float uv(int p, int size, int border, int texSize) {
        if (p < border) {
            return p / (float) texSize;
        }
        if (p >= size - border) {
            return (texSize - (size - p)) / (float) texSize;
        }
        return (border + (p - border) / (float) Math.max(1, size - 2 * border) * (texSize - 2 * border)) / texSize;
    }

    /**
     * 命中检测:交互区域 = 材质非完全透明像素(修改材质大小即可改变按钮大小)。
     * 采样失败(如贴图未加载)时退回矩形检测。
     */
    public static boolean hitTest(ResourceLocation tex, int x, int y, int w, int h,
                                  double mx, double my, int border, int texW, int texH) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        NativeImage img = pixels(tex);
        if (img == null) {
            return true;
        }
        float u = uv((int) (mx - x), w, border, texW);
        float v = uv((int) (my - y), h, border, texH);
        int px = Math.min(img.getWidth() - 1, (int) (u * img.getWidth()));
        int py = Math.min(img.getHeight() - 1, (int) (v * img.getHeight()));
        return ((img.getPixelRGBA(px, py) >>> 24) & 0xFF) > 8;
    }

    /** 按钮命中检测(普通态材质) */
    public static boolean buttonHit(int x, int y, int w, int h, double mx, double my) {
        return hitTest(BTN, x, y, w, h, mx, my, BTN_BORDER, 60, 16);
    }

    /** 小图标命中检测(12x12,按当前图标材质) */
    public static boolean iconHit(Icon icon, int x, int y, double mx, double my) {
        ResourceLocation tex = switch (icon) {
            case CLOSE -> CLOSE;
            case LAYOUT -> LAYOUT;
            case PLUS -> PLUS;
            case MINUS -> MINUS;
            case TRASH -> TRASH;
        };
        return hitTest(tex, x, y, 12, 12, mx, my, 0, 12, 12);
    }

    /** 勾选框命中检测(12x12,按当前勾选状态材质) */
    public static boolean checkboxHit(boolean selected, int x, int y, double mx, double my) {
        return hitTest(selected ? CHECK_ON : CHECK_OFF, x, y, 12, 12, mx, my, 0, 12, 12);
    }

    // ---------------- 立即模式绘制(绕过一切延迟缓冲,保证最上层) ----------------

    /**
     * 用 Tesselator 立即模式画纯色矩形。
     * 不经任何 MultiBufferSource,绘制即刻落屏,物理上不可能被之后的内容覆盖。
     */
    public static void fillImmediate(int x, int y, int w, int h, int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = new Matrix4f();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, x, y + h, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x + w, y + h, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x + w, y, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x, y, 0).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
    }

    /** 用 Tesselator 立即模式画按钮(九宫格),绘制即刻落屏 */
    public static void buttonImmediate(int x, int y, int w, int h, boolean hover, boolean enabled) {
        ResourceLocation tex = enabled ? (hover ? BTN_HOVER : BTN) : BTN_DISABLED;
        blit9Immediate(tex, x, y, w, h, 60, 16, BTN_BORDER);
    }

    private static void blit9Immediate(ResourceLocation tex, int x, int y, int w, int h,
                                       int tw, int th, int border) {
        int iw = tw - border * 2;
        int ih = th - border * 2;
        int mw = w - border * 2;
        int mh = h - border * 2;
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = new Matrix4f();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quad(buf, m, x, y, border, border, 0, 0, border, border, tw, th);
        quad(buf, m, x + w - border, y, border, border, tw - border, 0, border, border, tw, th);
        quad(buf, m, x, y + h - border, border, border, 0, th - border, border, border, tw, th);
        quad(buf, m, x + w - border, y + h - border, border, border, tw - border, th - border, border, border, tw, th);
        if (mw > 0) {
            quad(buf, m, x + border, y, mw, border, border, 0, iw, border, tw, th);
            quad(buf, m, x + border, y + h - border, mw, border, border, th - border, iw, border, tw, th);
        }
        if (mh > 0) {
            quad(buf, m, x, y + border, border, mh, 0, border, border, ih, tw, th);
            quad(buf, m, x + w - border, y + border, border, mh, tw - border, border, border, ih, tw, th);
        }
        if (mw > 0 && mh > 0) {
            quad(buf, m, x + border, y + border, mw, mh, border, border, iw, ih, tw, th);
        }
        Tesselator.getInstance().end();
    }

    private static void quad(BufferBuilder buf, Matrix4f m, int x, int y, int w, int h,
                             int u, int v, int uw, int vh, int tw, int th) {
        float f = 1f / tw;
        float g = 1f / th;
        buf.vertex(m, x, y + h, 0).uv(u * f, (v + vh) * g).color(255, 255, 255, 255).endVertex();
        buf.vertex(m, x + w, y + h, 0).uv((u + uw) * f, (v + vh) * g).color(255, 255, 255, 255).endVertex();
        buf.vertex(m, x + w, y, 0).uv((u + uw) * f, v * g).color(255, 255, 255, 255).endVertex();
        buf.vertex(m, x, y, 0).uv(u * f, v * g).color(255, 255, 255, 255).endVertex();
    }

    // ---------------- 小图标 ----------------

    public enum Icon { CLOSE, LAYOUT, PLUS, MINUS, TRASH }

    public static void icon(GuiGraphics g, int x, int y, Icon icon, boolean hover) {
        ResourceLocation tex = switch (icon) {
            case CLOSE -> hover ? CLOSE_HOVER : CLOSE;
            case LAYOUT -> hover ? LAYOUT_HOVER : LAYOUT;
            case PLUS -> hover ? PLUS_HOVER : PLUS;
            case MINUS -> hover ? MINUS_HOVER : MINUS;
            case TRASH -> hover ? TRASH_HOVER : TRASH;
        };
        g.blit(tex, x, y, 0, 0, 12, 12, 12, 12);
    }

    /** 交易类型角标(格子左上角):购买/出售/交换/指令各一张材质 */
    public static void typeIcon(GuiGraphics g, int x, int y, ShopEntryType type) {
        ResourceLocation tex = switch (type) {
            case BUY -> BUY;
            case SELL -> SELL;
            case BARTER -> BARTER;
            case COMMAND -> COMMAND;
        };
        g.blit(tex, x, y, 0, 0, 12, 12, 12, 12);
    }

    /** 删除按钮(编辑模式,8x8,按用户自定义材质缩放绘制) */
    public static void trashIcon(GuiGraphics g, int x, int y, boolean hover) {
        g.blit(hover ? TRASH_HOVER : TRASH, x, y, 0, 0, 8, 8, 12, 12);
    }

    // ---------------- 价格背景条 ----------------

    /** 价格背景条九宫格边距 */
    private static final int PRICE_BAR_BORDER = 5;

    /** 价格文字后面的灰色背景条(宽度按文字自适应) */
    public static void priceBar(GuiGraphics g, int x, int y, int w) {
        blit9(g, PRICE_BAR, x, y, w, 10, 32, 12, PRICE_BAR_BORDER);
    }

    // ---------------- 滑块 ----------------

    public static void track(GuiGraphics g, int x, int y, int w) {
        blit9(g, TRACK, x, y, w, 8, 96, 8, 1);
    }

    public static void knob(GuiGraphics g, int x, int y, boolean hover) {
        g.blit(hover ? KNOB_HOVER : KNOB, x, y, 0, 0, 8, 14, 8, 14);
    }

    // ---------------- 输入框 ----------------

    /** 输入框九宫格边距 */
    private static final int INPUT_BORDER = 2;

    public static void input(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        blit9(g, focused ? INPUT_FOCUS : INPUT, x, y, w, h, 96, 12, INPUT_BORDER);
    }

    // ---------------- 勾选框 ----------------

    public static void checkbox(GuiGraphics g, int x, int y, boolean selected, boolean hover) {
        ResourceLocation tex = selected ? (hover ? CHECK_ON_HOVER : CHECK_ON) : (hover ? CHECK_OFF_HOVER : CHECK_OFF);
        g.blit(tex, x, y, 0, 0, 12, 12, 12, 12);
    }
}
