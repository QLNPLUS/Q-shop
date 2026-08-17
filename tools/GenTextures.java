import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 生成 QShop 拆分 GUI 材质(每个元素一个 PNG,便于单独修改)。
 * 输出目录:src/main/resources/assets/qshop/textures/gui/
 */
public class GenTextures {

    private static final String OUT = "src/main/resources/assets/qshop/textures/gui/";

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();

        // ---- 面板 ----
        panel(250, 200, "panel.png");
        panel(250, 280, "panel_edit.png");

        // ---- 格子 20x20 ----
        slot(0xE0333333, 0xFF666666, "slot.png");
        slot(0xE0338833, 0xFF77AA77, "slot_hover.png");
        slot(0xE0664411, 0xFFAA7733, "slot_edit.png");
        slot(0xE0885511, 0xFFCC9944, "slot_edit_hover.png");

        // ---- 按钮 60x16 ----
        btn(0xFF4A4A4A, 0xFF222222, 0xFF777777, "button.png");
        btn(0xFF5A5A5A, 0xFF2E2E2E, 0xFF999999, "button_hover.png");
        btn(0xFF2E2E2E, 0xFF1A1A1A, 0xFF444444, "button_disabled.png");

        // ---- 输入框 96x12 ----
        input(0xFF555555, "input.png");
        input(0xFF77AAFF, "input_focus.png");

        // ---- 滑块 ----
        track();
        knob(0xFF8A8A8A, 0xFF3A3A3A, 0xFFAAAAAA, "knob.png");
        knob(0xFF9A9A9A, 0xFF4A4A4A, 0xFFBBBBBB, "knob_hover.png");

        // ---- 勾选框 12x12 ----
        check(false, 0xFF888888, "checkbox_off.png");
        check(true, 0xFF888888, "checkbox_on.png");
        check(false, 0xFFAAAAAA, "checkbox_off_hover.png");
        check(true, 0xFFAAAAAA, "checkbox_on_hover.png");

        // ---- 小图标 12x12 ----
        cross(0xFFFFFFFF, "close.png");
        cross(0xFFFF5555, "close_hover.png");
        plus(0xFF55FF55, "plus.png");
        plus(0xFF88FF88, "plus_hover.png");
        minus(0xFFFF5555, "minus.png");
        minus(0xFFFF8888, "minus_hover.png");
        trash(0xFFFFAA66, "trash.png");
        trash(0xFFFFCC99, "trash_hover.png");

        // ---- 购买/出售角标 12x12 ----
        arrowDown(0xFF55FF55, "buy.png");
        arrowUp(0xFFFFAA00, "sell.png");

        // ---- 价格背景条 32x12 ----
        priceBar();

        System.out.println("all textures written to " + OUT);
    }

    private static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static void write(BufferedImage b, String name) throws Exception {
        ImageIO.write(b, "png", new File(OUT + name));
    }

    private static void panel(int w, int h, String name) throws Exception {
        BufferedImage b = img(w, h);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE0101010, true));
        g.fillRoundRect(1, 1, w - 2, h - 2, 4, 4);
        g.setColor(new Color(0xFF8A8A8A));
        g.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
        g.setColor(new Color(0x2AFFFFFF, true));
        g.drawLine(4, 1, w - 5, 1);
        g.dispose();
        write(b, name);
    }

    /** 20x20: 3px 边框区(外框+内环)+ 内部填充,九宫格 border=3 时拉伸边框仍清晰 */
    private static void slot(int fill, int border, String name) throws Exception {
        BufferedImage b = img(20, 20);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0xFF3A3A3A));
        g.fillRect(1, 1, 18, 18);
        g.setColor(new Color(fill, true));
        g.fillRect(3, 3, 14, 14);
        g.setColor(new Color(border));
        g.drawRect(2, 2, 15, 15);
        g.setColor(new Color(border));
        g.drawRect(0, 0, 19, 19);
        g.setColor(new Color(0x26000000, true));
        g.fillRect(4, 15, 12, 4);
        g.dispose();
        write(b, name);
    }

    private static void btn(int top, int bottom, int border, String name) throws Exception {
        BufferedImage b = img(60, 16);
        Graphics2D g = b.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(top), 0, 16, new Color(bottom)));
        g.fillRect(1, 1, 58, 14);
        g.setColor(new Color(border));
        g.drawRect(0, 0, 59, 15);
        g.setColor(new Color(0x2AFFFFFF, true));
        g.drawLine(2, 1, 57, 1);
        g.dispose();
        write(b, name);
    }

    private static void input(int border, String name) throws Exception {
        BufferedImage b = img(96, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0xFF1E1E1E));
        g.fillRect(1, 1, 94, 10);
        g.setColor(new Color(border));
        g.drawRect(0, 0, 95, 11);
        g.dispose();
        write(b, name);
    }

    private static void track() throws Exception {
        BufferedImage b = img(96, 8);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0xFF1A1A1A));
        g.fillRect(1, 1, 94, 6);
        g.setColor(new Color(0xFF555555));
        g.drawRect(0, 0, 95, 7);
        g.dispose();
        write(b, "track.png");
    }

    private static void knob(int top, int bottom, int border, String name) throws Exception {
        BufferedImage b = img(8, 14);
        Graphics2D g = b.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(top), 0, 14, new Color(bottom)));
        g.fillRect(1, 1, 6, 12);
        g.setColor(new Color(border));
        g.drawRect(0, 0, 7, 13);
        g.setColor(new Color(0x40FFFFFF, true));
        g.drawLine(2, 1, 5, 1);
        g.dispose();
        write(b, name);
    }

    private static void check(boolean selected, int border, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0xFF222222));
        g.fillRect(1, 1, 10, 10);
        g.setColor(new Color(border));
        g.drawRect(0, 0, 11, 11);
        if (selected) {
            g.setColor(new Color(0xFF55FF55));
            g.setStroke(new BasicStroke(2));
            g.drawLine(2, 6, 5, 9);
            g.drawLine(5, 9, 9, 3);
        }
        g.dispose();
        write(b, name);
    }

    private static void cross(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(2, 2, 9, 9);
        g.drawLine(9, 2, 2, 9);
        g.dispose();
        write(b, name);
    }

    private static void plus(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(2, 6, 9, 6);
        g.drawLine(6, 2, 6, 9);
        g.dispose();
        write(b, name);
    }

    private static void minus(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(2, 6, 9, 6);
        g.dispose();
        write(b, name);
    }

    private static void trash(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(3, 3, 8, 3);
        g.drawLine(5, 1, 6, 1);
        g.drawRect(2, 4, 7, 6);
        g.dispose();
        write(b, name);
    }

    /** 下箭头(购买角标) */
    private static void arrowDown(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(6, 1, 6, 7);
        g.drawLine(2, 5, 6, 9);
        g.drawLine(10, 5, 6, 9);
        g.dispose();
        write(b, name);
    }

    /** 上箭头(出售角标) */
    private static void arrowUp(int color, String name) throws Exception {
        BufferedImage b = img(12, 12);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(2));
        g.drawLine(6, 11, 6, 5);
        g.drawLine(2, 7, 6, 3);
        g.drawLine(10, 7, 6, 3);
        g.dispose();
        write(b, name);
    }

    /** 价格背景条(圆角深灰,九宫格拉伸) */
    private static void priceBar() throws Exception {
        BufferedImage b = img(32, 12);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xFF3A3A3A));
        g.fillRoundRect(1, 1, 30, 10, 4, 4);
        g.setColor(new Color(0xFF555555));
        g.drawRoundRect(0, 0, 31, 11, 4, 4);
        g.dispose();
        write(b, "price_bar.png");
    }
}
