package com.qshop.currency;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qshop.util.QLang;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 货币注册表。活动定义文件位于世界的 serverconfig/qshop/currencies.json;
 * 服务器根目录的 config/qshop/currencies.json 作为默认模板。
 */
public final class CurrencyRegistry {

    private static final Logger LOGGER = LogManager.getLogger("QShop");

    private static final Map<String, Currency> CURRENCIES = new LinkedHashMap<>();

    /** 配置文件的绝对路径(load 时记录,create 时写回) */
    private static Path FILE;

    private CurrencyRegistry() {
    }

    /** 从文件加载;文件不存在时生成默认配置 */
    public static void load(Path file) {
        FILE = file;
        CURRENCIES.clear();
        try {
            ensureDefaultFile(file);
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.has("currencies")) {
                for (var el : root.getAsJsonArray("currencies")) {
                    JsonObject o = el.getAsJsonObject();
                    String id = o.has("id") ? o.get("id").getAsString() : "";
                    if (id.isEmpty()) {
                        continue;
                    }
                    String name = migrateLegacyName(id, o.has("name") ? o.get("name").getAsString() : "");
                    String color = o.has("color") ? o.get("color").getAsString() : "#FFFFFF";
                    CURRENCIES.put(id, new Currency(id, name, Currency.parseColor(color)));
                }
            }
            LOGGER.info("QShop: 已加载 {} 种货币", CURRENCIES.size());
        } catch (Exception e) {
            LOGGER.error("QShop: 货币配置加载失败", e);
        }
        if (CURRENCIES.isEmpty()) {
            CURRENCIES.put("coins", new Currency("coins", defaultName("coins"), 0xFFD700));
        }
    }

    /** 旧版默认中文名迁移到语言键：中文客户端仍显示原中文，其他语言按各自翻译显示。 */
    private static String migrateLegacyName(String id, String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String trimmed = name.trim();
        if ("coins".equals(id) && "金币".equals(trimmed)) {
            return QLang.PREFIX + "qshop.currency.coins";
        }
        if ("points".equals(id) && "点数".equals(trimmed)) {
            return QLang.PREFIX + "qshop.currency.points";
        }
        return name;
    }

    /** 空名/缺省名的默认语言键 */
    public static String defaultName(String id) {
        return QLang.PREFIX + "qshop.currency." + id;
    }

    /** Creates the default file without replacing an existing configuration. */
    public static void ensureDefaultFile(Path file) {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultJson());
                LOGGER.info("QShop: 已生成默认货币配置 {}", file);
            }
        } catch (Exception e) {
            LOGGER.error("QShop: 默认货币配置生成失败 {}", file, e);
        }
    }

    public static Currency get(String id) {
        return id == null ? null : CURRENCIES.get(id);
    }

    public static List<Currency> all() {
        return new ArrayList<>(CURRENCIES.values());
    }

    public static String firstId() {
        return CURRENCIES.isEmpty() ? null : CURRENCIES.keySet().iterator().next();
    }

    /** 创建货币类型(重复 id 或非法颜色返回 false);成功后写回配置文件 */
    public static boolean create(String id, String name, String colorHex) {
        if (id == null || id.isEmpty() || CURRENCIES.containsKey(id)) {
            return false;
        }
        String color = colorHex == null || colorHex.isBlank() ? "#FFFFFF" : colorHex.trim();
        int parsed;
        try {
            parsed = Integer.parseInt(color.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return false; // 非法颜色
        }
        if (parsed < 0 || parsed > 0xFFFFFF) {
            return false;
        }
        CURRENCIES.put(id, new Currency(id, name == null || name.isBlank() ? id : name, parsed));
        if (FILE != null) {
            save(FILE);
        }
        return true;
    }

    /** 把当前货币列表写回配置文件 */
    public static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Currency c : CURRENCIES.values()) {
                arr.add(currencyJson(c.id, c.displayName, "#" + Integer.toHexString(c.color).toUpperCase()));
            }
            root.add("currencies", arr);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(root));
        } catch (Exception e) {
            LOGGER.error("QShop: 货币配置保存失败", e);
        }
    }

    /** 显示名（按当前语言解析；空名回退到 qshop.currency.&lt;id&gt;） */
    public static String displayName(String id) {
        Currency c = get(id);
        if (c == null) {
            return id == null ? "" : id;
        }
        return QLang.text(c.displayName, defaultName(c.id), c.id);
    }

    /** 显示名组件：交给客户端按各自语言解析（用于聊天/指令反馈） */
    public static Component displayNameComponent(String id) {
        Currency c = get(id);
        if (c == null) {
            return Component.literal(id == null ? "" : id);
        }
        return QLang.name(c.displayName, defaultName(c.id), c.id);
    }

    /** 数字展示:整数不带小数点;超出 double 精确整数范围(约 9.2e18)时避免 (long) 强转饱和 */
    public static String format(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && !Double.isNaN(v)
                && Math.abs(v) < 9.223372036854776E18) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private static String defaultJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        arr.add(currencyJson("coins", defaultName("coins"), "#FFD700"));
        arr.add(currencyJson("points", defaultName("points"), "#55FFFF"));
        root.add("currencies", arr);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    private static JsonObject currencyJson(String id, String name, String color) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("color", color);
        return o;
    }
}
