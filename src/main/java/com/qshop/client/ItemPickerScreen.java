package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 物品选择界面:全物品模式(全部已注册物品 + 常见 NBT 变体 + TACZ 枪械/配件)与背包模式。
 * 搜索支持:普通文字按名称、#标签(如 #minecraft:planks)、@命名空间(如 @tacz)。
 * 网格带平滑滚动动画;选择后通过回调返回 ItemStack;返回/关闭均回到上一个界面。
 */
public class ItemPickerScreen extends Screen {

    public interface Picker {
        void onPick(ItemStack stack);
    }

    private static final int GUI_W = 250;
    private static final int GUI_H = 214;
    private static final int COLS = 8;
    private static final int ROWS = 5;
    private static final int CELL = 28;

    private final Screen previous;
    private final Picker picker;
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> visible = new ArrayList<>();

    private boolean allMode = true;
    private String search = "";
    private int scroll = 0;
    private float rowAnim = 0;
    private int left;
    private int top;
    private EditBox searchBox;

    public ItemPickerScreen(Screen previous, Picker picker) {
        super(Component.translatable("qshop.gui.pick_item"));
        this.previous = previous;
        this.picker = picker;
    }

    @Override
    protected void init() {
        ShopLayoutDebug.beginScreen(ShopLayoutDebug.DebugScreen.ITEM_PICKER);
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        if (allItems.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                allItems.add(new ItemStack(item));
                addNbtVariants(allItems, item);
            }
            addTaczItems(allItems);
        }
        rebuild();
    }

    /** 为"同名不同 NBT"的物品补充常用变体(药水/喷溅/滞留/药箭/附魔书) */
    private static void addNbtVariants(List<ItemStack> list, Item item) {
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
            for (var potion : BuiltInRegistries.POTION) {
                ItemStack stack = new ItemStack(item);
                net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack, potion);
                list.add(stack);
            }
        } else if (item == Items.ENCHANTED_BOOK) {
            for (var enchantment : BuiltInRegistries.ENCHANTMENT) {
                int max = enchantment.getMaxLevel();
                for (int level = 1; level <= max; level++) {
                    list.add(net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                            new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment, level)));
                }
            }
        }
    }

    /**
     * TACZ(永恒枪械工坊)兼容。优先读取 TACZ 的 TimelessAPI，因为服务器数据包内容
     * 会在登录后同步到 TACZ 的客户端缓存，而不是 Minecraft 客户端资源管理器的 custom 路径。
     */
    private static void addTaczItems(List<ItemStack> list) {
        if (!ModList.get().isLoaded("tacz")) {
            return;
        }
        if (addTaczApiItems(list)) {
            return;
        }
        addTaczResourceItems(list);
    }

    /**
     * 通过反射调用 TACZ 的公开 API，避免把 TACZ 变成 QShop 的强制依赖。
     * Builder 会根据 index 的 item_type 创建正确的枪械物品，并写入完整 NBT。
     */
    private static boolean addTaczApiItems(List<ItemStack> list) {
        int before = list.size();
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> gunBuilder = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            addTaczGunIndexes(list, invokeStatic(api, "getAllCommonGunIndex"), gunBuilder);

            addTaczSimpleIndexes(list, invokeStatic(api, "getAllCommonAttachmentIndex"),
                    Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder"));
            addTaczSimpleIndexes(list, invokeStatic(api, "getAllCommonAmmoIndex"),
                    Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
        return list.size() > before;
    }

    private static void addTaczGunIndexes(List<ItemStack> list, Object indexes, Class<?> builderClass)
            throws ReflectiveOperationException {
        if (!(indexes instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object value : iterable) {
            if (!(value instanceof Map.Entry<?, ?> entry) || !(entry.getKey() instanceof ResourceLocation id)) {
                continue;
            }
            try {
                Object index = entry.getValue();
                Object data = invoke(index, "getGunData");
                Object modes = invoke(data, "getFireModeSet");
                if (!(modes instanceof List<?> fireModes) || fireModes.isEmpty()) {
                    continue;
                }
                Object builder = invokeStatic(builderClass, "create");
                builder = invoke(builder, "setId", id);
                builder = invoke(builder, "setFireMode", fireModes.get(0));
                builder = invoke(builder, "setAmmoCount", ((Number) invoke(data, "getAmmoAmount")).intValue());
                builder = invoke(builder, "setHeatData", invoke(data, "hasHeatData"));
                builder = invoke(builder, "setAmmoInBarrel", true);
                Object result = invoke(builder, "build");
                if (result instanceof ItemStack stack && !stack.isEmpty()) {
                    list.add(stack);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 单个损坏的 index 不应阻止其他数据包物品显示。
            }
        }
    }

    private static void addTaczSimpleIndexes(List<ItemStack> list, Object indexes, Class<?> builderClass)
            throws ReflectiveOperationException {
        if (!(indexes instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object value : iterable) {
            if (!(value instanceof Map.Entry<?, ?> entry) || !(entry.getKey() instanceof ResourceLocation id)) {
                continue;
            }
            try {
                Object builder = invokeStatic(builderClass, "create");
                builder = invoke(builder, "setId", id);
                Object result = invoke(builder, "build");
                if (result instanceof ItemStack stack && !stack.isEmpty()) {
                    list.add(stack);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 单个损坏的 index 不应阻止其他数据包物品显示。
            }
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Object... args) throws ReflectiveOperationException {
        return findMethod(type, name, args).invoke(null, args);
    }

    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        return findMethod(target.getClass(), name, args).invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String name, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!method.getName().equals(name) || parameterTypes.length != args.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null ? parameterTypes[i].isPrimitive()
                        : !wrapPrimitive(parameterTypes[i]).isInstance(args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    /** 旧版 TACZ 的资源路径回退，兼容本地 custom 资源包。 */
    private static void addTaczResourceItems(List<ItemStack> list) {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Item gunItem = firstItem("tacz:modern_kinetic_gun", "tacz:gun");
            Item attachmentItem = firstItem("tacz:attachment", "tacz:modern_kinetic_attachment");
            Item ammoItem = firstItem("tacz:ammo", "tacz:modern_kinetic_ammo");
            if (gunItem == null || gunItem == Items.AIR) {
                return; // 未安装 TACZ
            }
            Map<ResourceLocation, Resource> found = rm.listResources("custom",
                    rl -> rl.getPath().endsWith(".json") && rl.getPath().contains("/data/tacz/index/"));
            for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
                String path = entry.getKey().getPath();
                String[] seg = path.split("/");
                // custom/<pack>/data/<ns>/index/<kind>/<file>.json
                if (seg.length < 7 || !seg[2].equals("data") || !seg[4].equals("index")) {
                    continue;
                }
                String ns = seg[3];
                String kind = seg[5];
                String file = seg[seg.length - 1];
                String id = file.substring(0, file.length() - 5);
                Item item = switch (kind) {
                    case "guns" -> gunItem;
                    case "attachments" -> attachmentItem;
                    case "ammo" -> ammoItem;
                    default -> null;
                };
                if (item == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                CompoundTag tag = new CompoundTag();
                String tagKey = switch (kind) {
                    case "guns" -> "GunId";
                    case "attachments" -> "AttachmentId";
                    default -> "AmmoId";
                };
                tag.putString(tagKey, ns + ":" + id);
                stack.setTag(tag);
                // 显示名(索引 JSON 的 name 字段,可能是语言键)
                try {
                    Resource res = entry.getValue();
                    try (InputStream in = res.open()) {
                        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        var obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                        if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
                            String nameKey = obj.get("name").getAsString();
                            String display = I18n.get(nameKey);
                            if (display == null || display.isEmpty() || display.equals(nameKey)) {
                                display = nameKey;
                            }
                            stack.setHoverName(Component.literal(display));
                        }
                    }
                } catch (Exception ignored) {
                }
                list.add(stack);
            }
        } catch (Exception ignored) {
        }
    }

    private static Item firstItem(String... ids) {
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item != null && item != Items.AIR) {
                return item;
            }
        }
        return null;
    }

    /** 返回上一个界面(取消/关闭/Esc) */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previous);
    }

    private int px(ShopLayoutDebug.PickerWidget widget, int normalX) {
        return ShopLayoutDebug.x(widget, normalX);
    }

    private int py(ShopLayoutDebug.PickerWidget widget, int normalY) {
        return ShopLayoutDebug.y(widget, normalY);
    }

    private void rebuild() {
        this.clearWidgets();

        // 顶行:模式切换(居中)+ 关闭(右);返回按钮在底部中间
        addRenderableWidget(new QButton(px(ShopLayoutDebug.PickerWidget.MODE_BUTTON, left + 81),
                py(ShopLayoutDebug.PickerWidget.MODE_BUTTON, top + 6), 88, 14,
                Component.translatable(allMode ? "qshop.gui.all_items" : "qshop.gui.inventory_items"), b -> {
                    allMode = !allMode;
                    // 切换后同步按钮文字:显示背包物品时按钮显示"背包物品"
                    b.setMessage(Component.translatable(allMode ? "qshop.gui.all_items" : "qshop.gui.inventory_items"));
                    scroll = 0;
                    rowAnim = 0;
                    refreshVisible();
                }));
        addRenderableWidget(new QIconButton(px(ShopLayoutDebug.PickerWidget.CLOSE_BUTTON, left + GUI_W - 20),
                py(ShopLayoutDebug.PickerWidget.CLOSE_BUTTON, top + 6), ShopTextures.Icon.CLOSE, this::onClose));
        addRenderableWidget(new QButton(px(ShopLayoutDebug.PickerWidget.BACK_BUTTON, left + 70),
                py(ShopLayoutDebug.PickerWidget.BACK_BUTTON, top + 190), 110, 16,
                Component.translatable("qshop.gui.back"), b -> onClose()));

        searchBox = new EditBox(this.font,
                px(ShopLayoutDebug.PickerWidget.SEARCH_BOX, left + 14),
                py(ShopLayoutDebug.PickerWidget.SEARCH_BOX, top + 28),
                222, 14, Component.literal(""));
        searchBox.setMaxLength(40);
        searchBox.setBordered(false);
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s == null ? "" : s;
            scroll = 0;
            rowAnim = 0;
            refreshVisible();
        });
        addRenderableWidget(searchBox);

        refreshVisible();
    }

    private void refreshVisible() {
        visible.clear();
        List<ItemStack> source;
        if (allMode) {
            source = allItems;
        } else {
            source = new ArrayList<>();
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty()) {
                        source.add(s.copy());
                    }
                }
            }
        }
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (query.startsWith("#")) {
            // 标签搜索:如 #minecraft:planks 或 #planks
            String tagId = query.substring(1);
            if (!tagId.contains(":")) {
                tagId = "minecraft:" + tagId;
            }
            ResourceLocation tagRl = ResourceLocation.tryParse(tagId);
            if (tagRl != null) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagRl);
                for (ItemStack s : source) {
                    if (s.is(tagKey)) {
                        visible.add(s);
                    }
                }
            }
        } else if (query.startsWith("@")) {
            // 命名空间搜索:如 @tacz
            String ns = query.substring(1);
            for (ItemStack s : source) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (id.getNamespace().startsWith(ns)) {
                    visible.add(s);
                }
            }
        } else {
            for (ItemStack s : source) {
                if (query.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    visible.add(s);
                }
            }
        }
    }

    private int maxScroll() {
        // 允许滚动到最后一行完整对齐:最大滚动 = ceil(size/COLS)*COLS - 可见数
        return Math.max(0, (int) Math.ceil(visible.size() / (float) COLS) * COLS - COLS * ROWS);
    }

    /** 由鼠标坐标计算悬浮/点击的条目序号,-1 表示不在网格内(向下取整,避免网格上方误判) */
    private int indexAt(double mouseX, double mouseY) {
        int gx = px(ShopLayoutDebug.PickerWidget.GRID, left + 13);
        int gy = py(ShopLayoutDebug.PickerWidget.GRID, top + 46);
        int baseRow = (int) Math.floor(rowAnim);
        float frac = rowAnim - baseRow;
        int c = (int) Math.floor((mouseX - gx) / CELL);
        int r = (int) Math.floor((mouseY - gy + frac * CELL) / CELL);
        int index = (baseRow + r) * COLS + c;
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS || index < 0 || index >= visible.size()) {
            return -1;
        }
        return index;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先全部取消聚焦,再聚焦被点击的输入框
        if (searchBox != null) {
            searchBox.setFocused(false);
            if (searchBox.mouseClicked(mouseX, mouseY, button)) {
                searchBox.setFocused(true);
                return true;
            }
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            if (picker != null) {
                picker.onPick(visible.get(index).copy());
            }
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F8 && ShopLayoutDebug.isConfiguredEnabled()) {
            ShopLayoutDebug.toggle();
            rebuild();
            return true;
        }
        if (ShopLayoutDebug.isEnabled() && (searchBox == null || !searchBox.isFocused())) {
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
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int direction = delta > 0 ? 1 : delta < 0 ? -1 : 0;
        int ns = Mth.clamp(scroll - direction * COLS, 0, maxScroll());
        if (ns != scroll) {
            scroll = ns;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ShopTextures.background(g, this.width, this.height);
        ShopTextures.panelPicker(g,
                px(ShopLayoutDebug.PickerWidget.PANEL, left),
                py(ShopLayoutDebug.PickerWidget.PANEL, top));

        // 滚动动画(以"行"为单位插值,时间基准,帧率无关)
        float target = scroll / (float) COLS;
        float delta = Minecraft.getInstance().getDeltaFrameTime();
        rowAnim += (target - rowAnim) * Math.min(1.0f, delta * 15f);
        if (Math.abs(target - rowAnim) < 0.005f) {
            rowAnim = target;
        }

        ShopTextures.input(g,
                px(ShopLayoutDebug.PickerWidget.SEARCH_BOX, left + 12),
                py(ShopLayoutDebug.PickerWidget.SEARCH_BOX, top + 27),
                226, 12, searchBox.isFocused());

        // 网格(手动渲染,平滑滚动;裁剪到网格视口)
        int gx = px(ShopLayoutDebug.PickerWidget.GRID, left + 13);
        int gy = py(ShopLayoutDebug.PickerWidget.GRID, top + 46);
        int baseRow = (int) Math.floor(rowAnim);
        float frac = rowAnim - baseRow;
        int hovered = indexAt(mouseX, mouseY);
        ShopTextures.enableScissor(g, gx, gy, COLS * CELL, ROWS * CELL);
        for (int r = 0; r <= ROWS; r++) {
            int y = gy + (int) (r * CELL - frac * CELL);
            for (int c = 0; c < COLS; c++) {
                int index = (baseRow + r) * COLS + c;
                if (index < 0 || index >= visible.size()) {
                    continue;
                }
                ItemStack s = visible.get(index);
                boolean hover = index == hovered;
                ShopTextures.slot(g, gx + c * CELL, y, CELL - 2, CELL - 2, hover, false);
                g.renderItem(s, gx + c * CELL + 5, y + 5);
                g.renderItemDecorations(this.font, s, gx + c * CELL + 5, y + 5);
            }
        }
        ShopTextures.disableScissor(g);

        ShopTextures.renderWidgets(this, g, mouseX, mouseY, partialTick);

        // 悬浮物品 tooltip
        if (hovered >= 0) {
            ItemStack s = visible.get(hovered);
            if (!s.isEmpty()) {
                g.renderTooltip(this.font, s, mouseX, mouseY);
            }
        }
        renderDebugOverlay(g);
    }

    private void renderDebugOverlay(GuiGraphics g) {
        if (!ShopLayoutDebug.isEnabled()) {
            return;
        }
        ShopLayoutDebug.PickerWidget widget = ShopLayoutDebug.selectedPicker();
        int x;
        int y;
        int w;
        int h;
        switch (widget) {
            case PANEL -> {
                x = px(ShopLayoutDebug.PickerWidget.PANEL, left);
                y = py(ShopLayoutDebug.PickerWidget.PANEL, top);
                w = GUI_W;
                h = GUI_H;
            }
            case MODE_BUTTON -> {
                x = px(ShopLayoutDebug.PickerWidget.MODE_BUTTON, left + 81);
                y = py(ShopLayoutDebug.PickerWidget.MODE_BUTTON, top + 6);
                w = 88;
                h = 14;
            }
            case CLOSE_BUTTON -> {
                x = px(ShopLayoutDebug.PickerWidget.CLOSE_BUTTON, left + GUI_W - 20);
                y = py(ShopLayoutDebug.PickerWidget.CLOSE_BUTTON, top + 6);
                w = 12;
                h = 12;
            }
            case BACK_BUTTON -> {
                x = px(ShopLayoutDebug.PickerWidget.BACK_BUTTON, left + 70);
                y = py(ShopLayoutDebug.PickerWidget.BACK_BUTTON, top + 190);
                w = 110;
                h = 16;
            }
            case SEARCH_BOX -> {
                x = px(ShopLayoutDebug.PickerWidget.SEARCH_BOX, left + 12);
                y = py(ShopLayoutDebug.PickerWidget.SEARCH_BOX, top + 27);
                w = 226;
                h = 14;
            }
            case GRID -> {
                x = px(ShopLayoutDebug.PickerWidget.GRID, left + 13);
                y = py(ShopLayoutDebug.PickerWidget.GRID, top + 46);
                w = COLS * CELL;
                h = ROWS * CELL;
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
