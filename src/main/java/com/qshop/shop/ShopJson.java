package com.qshop.shop;

import com.qshop.util.ItemStackData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 商店 JSON 序列化 / 反序列化。
 *
 * <p>物品的三种写法(读取时都支持,保存时统一写为 base64):
 * <pre>
 * "minecraft:diamond"                       - 物品 id,数量 1
 * {"item": "minecraft:diamond", "count": 4} - 物品 id + 数量
 * {"item": "minecraft:diamond", "count": 1,
 *  "nbt": "{...snbt...}"}                   - 物品 id + 数量 + NBT(SNBT 格式)
 * "H4sIAAAAAA...(base64)"                   - 模组保存的压缩 NBT base64 格式
 * </pre>
 */
public final class ShopJson {

    private static final Logger LOGGER = LogManager.getLogger("QShop");
    private static final RegistryAccess REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private ShopJson() {
    }

    // ---------------- 物品 ----------------

    public static String stackToBase64(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = (CompoundTag) stack.save(REGISTRY_ACCESS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            throw new RuntimeException("物品序列化失败", e);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static ItemStack stackFromBase64(String s) {
        if (s == null || s.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(s);
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return ItemStack.parseOptional(REGISTRY_ACCESS, tag);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** 解析物品的三种写法,失败时抛出异常 */
    public static ItemStack parseItem(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        try {
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (s.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (s.startsWith("H4sI")) {
                    return stackFromBase64(s);
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s));
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    throw new IllegalArgumentException("未知物品: " + s);
                }
                return new ItemStack(item, 1);
            }
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                String itemId = o.has("item") ? o.get("item").getAsString() : "";
                if (itemId.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (itemId.startsWith("H4sI")) {
                    return stackFromBase64(itemId);
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                // ITEM 注册表是 DefaultedRegistry:未注册 id 返回默认值 AIR 而非 null,
                // 需显式判空,避免未知物品被静默解析成空条目
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    throw new IllegalArgumentException("未知物品: " + itemId);
                }
                int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
                ItemStack stack = new ItemStack(item, count);
                if (o.has("nbt") && o.get("nbt").isJsonPrimitive()) {
                    ItemStackData.setCustomTag(stack, TagParser.parseTag(o.get("nbt").getAsString()));
                }
                return stack;
            }
        } catch (Exception e) {
            throw new RuntimeException("解析物品失败: " + el, e);
        }
        return ItemStack.EMPTY;
    }

    // ---------------- 交易条目 ----------------

    public static JsonObject entryToJson(ShopEntry e) {
        JsonObject o = new JsonObject();
        e.ensureUuid();
        o.addProperty("uuid", e.uuid);
        o.addProperty("type", e.type.name());
        if (e.displayName != null && !e.displayName.isEmpty()) {
            o.addProperty("displayName", e.displayName);
        }
        if (e.description != null && !e.description.isEmpty()) {
            o.addProperty("description", e.description);
        }
        if (e.displayItem != null && !e.displayItem.isEmpty()) {
            o.addProperty("displayItem", stackToBase64(e.displayItem));
        }
        if (e.type == ShopEntryType.BARTER) {
            JsonArray give = new JsonArray();
            for (ItemStack s : e.give) {
                give.add(new com.google.gson.JsonPrimitive(stackToBase64(s)));
            }
            o.add("give", give);
            JsonArray receive = new JsonArray();
            for (ItemStack s : e.receive) {
                receive.add(new com.google.gson.JsonPrimitive(stackToBase64(s)));
            }
            o.add("receive", receive);
        } else {
            o.addProperty("item", stackToBase64(e.item));
        }
        o.addProperty("currency", e.currencyId == null ? "" : e.currencyId);
        o.addProperty("price", e.price);
        if (e.globalLimit > 0) {
            o.addProperty("globalLimit", e.globalLimit);
        }
        if (e.playerLimit > 0) {
            o.addProperty("playerLimit", e.playerLimit);
        }
        if (e.reset != LimitReset.NEVER) {
            o.addProperty("limitReset", e.reset.name());
        }
        if (!e.commands.isEmpty()) {
            JsonArray cmds = new JsonArray();
            for (ShopCommand c : e.commands) {
                JsonObject co = new JsonObject();
                co.addProperty("command", c.command);
                co.addProperty("op", c.op);
                co.addProperty("silent", c.silent);
                cmds.add(co);
            }
            o.add("commands", cmds);
        }
        if (!e.requiredQuests.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String q : e.requiredQuests) {
                arr.add(q);
            }
            o.add("requiredQuests", arr);
        }
        if (!e.requiredStages.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String s : e.requiredStages) {
                arr.add(s);
            }
            o.add("requiredStages", arr);
        }
        if (!e.requiredStageDescriptions.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String description : e.requiredStageDescriptions) {
                arr.add(description == null ? "" : description);
            }
            o.add("requiredStageDescriptions", arr);
        }
        if (e.showWhenRequirementsNotMet) {
            o.addProperty("showWhenRequirementsNotMet", true);
        }
        return o;
    }

    public static ShopEntry entryFromJson(JsonObject o) {
        ShopEntry e = new ShopEntry();
        if (o.has("uuid") && !o.get("uuid").getAsString().isBlank()) {
            e.uuid = o.get("uuid").getAsString().trim();
        }
        e.ensureUuid();
        e.type = o.has("type") ? ShopEntryType.fromName(o.get("type").getAsString()) : ShopEntryType.BUY;
        if (o.has("displayName")) {
            e.displayName = o.get("displayName").getAsString();
        }
        if (o.has("description")) {
            e.description = o.get("description").getAsString();
        }
        if (o.has("displayItem")) {
            try {
                e.displayItem = parseItem(o.get("displayItem"));
            } catch (Exception ex) {
                e.displayItem = ItemStack.EMPTY;
            }
        }
        if (e.type == ShopEntryType.BARTER) {
            if (o.has("give")) {
                for (JsonElement el : o.getAsJsonArray("give")) {
                    ItemStack s = parseItem(el);
                    if (!s.isEmpty()) {
                        e.give.add(s);
                    }
                }
            }
            if (o.has("receive")) {
                for (JsonElement el : o.getAsJsonArray("receive")) {
                    ItemStack s = parseItem(el);
                    if (!s.isEmpty()) {
                        e.receive.add(s);
                    }
                }
            }
        } else {
            e.item = parseItem(o.get("item"));
        }
        e.currencyId = o.has("currency") ? o.get("currency").getAsString() : "";
        e.price = o.has("price") ? Math.max(0, o.get("price").getAsDouble()) : 0;
        e.globalLimit = o.has("globalLimit") ? o.get("globalLimit").getAsInt() : -1;
        e.playerLimit = o.has("playerLimit") ? o.get("playerLimit").getAsInt() : -1;
        e.reset = o.has("limitReset") ? LimitReset.fromName(o.get("limitReset").getAsString()) : LimitReset.NEVER;
        if (o.has("commands")) {
            for (JsonElement el : o.getAsJsonArray("commands")) {
                JsonObject co = el.getAsJsonObject();
                ShopCommand c = new ShopCommand();
                c.command = co.has("command") ? co.get("command").getAsString() : "";
                c.op = co.has("op") && co.get("op").getAsBoolean();
                c.silent = !co.has("silent") || co.get("silent").getAsBoolean();
                if (!c.command.isEmpty()) {
                    e.commands.add(c);
                }
            }
        }
        if (o.has("requiredQuests")) {
            for (JsonElement el : o.getAsJsonArray("requiredQuests")) {
                String q = el.getAsString();
                if (q != null && !q.isBlank()) {
                    e.requiredQuests.add(q.trim());
                }
            }
        }
        if (o.has("requiredStages")) {
            for (JsonElement el : o.getAsJsonArray("requiredStages")) {
                String s = el.getAsString();
                if (s != null && !s.isBlank()) {
                    e.requiredStages.add(s.trim());
                }
            }
        }
        if (o.has("requiredStageDescriptions")) {
            for (JsonElement el : o.getAsJsonArray("requiredStageDescriptions")) {
                e.requiredStageDescriptions.add(el.getAsString().trim());
            }
        }
        e.showWhenRequirementsNotMet = o.has("showWhenRequirementsNotMet")
                && o.get("showWhenRequirementsNotMet").getAsBoolean();
        return e;
    }

    // ---------------- 商店 ----------------

    public static JsonObject shopToJson(Shop shop) {
        JsonObject o = new JsonObject();
        o.addProperty("id", shop.id);
        o.addProperty("uuid", shop.uuid == null ? UUID.randomUUID().toString() : shop.uuid.toString());
        if (shop.displayName != null && !shop.displayName.isEmpty()) {
            o.addProperty("displayName", shop.displayName);
        }
        if (shop.currency != null && !shop.currency.isEmpty()) {
            o.addProperty("currency", shop.currency);
        }
        o.addProperty("icon", stackToBase64(shop.icon));
        shop.ensureTabs();
        // 子商店列表
        JsonArray tabs = new JsonArray();
        for (ShopTab t : shop.tabs) {
            JsonObject to = new JsonObject();
            t.ensureUuid();
            to.addProperty("uuid", t.uuid);
            if (t.name != null && !t.name.isEmpty()) {
                to.addProperty("name", t.name);
            }
            if (t.description != null && !t.description.isEmpty()) {
                to.addProperty("description", t.description);
            }
            if (t.icon != null && !t.icon.isEmpty()) {
                to.addProperty("icon", stackToBase64(t.icon));
            }
            if (!t.requiredQuests.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String q : t.requiredQuests) {
                    arr.add(q);
                }
                to.add("requiredQuests", arr);
            }
            if (!t.requiredStages.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String s : t.requiredStages) {
                    arr.add(s);
                }
                to.add("requiredStages", arr);
            }
            JsonArray es = new JsonArray();
            for (ShopEntry e : t.entries) {
                es.add(entryToJson(e));
            }
            to.add("entries", es);
            tabs.add(to);
        }
        o.add("tabs", tabs);
        // 兼容旧版本:entries = 第一个子商店
        JsonArray entries = new JsonArray();
        if (!shop.tabs.isEmpty()) {
            for (ShopEntry e : shop.tabs.get(0).entries) {
                entries.add(entryToJson(e));
            }
        }
        o.add("entries", entries);
        return o;
    }

    /** 解析一个交易条目 JSON,跳过无效条目(COMMAND 允许无物品) */
    private static void parseEntryInto(Shop shop, JsonElement el, List<ShopEntry> target) {
        try {
            ShopEntry entry = entryFromJson(el.getAsJsonObject());
            boolean empty = entry.item.isEmpty() && entry.give.isEmpty() && entry.receive.isEmpty();
            if (entry.type != ShopEntryType.COMMAND && empty) {
                LOGGER.warn("QShop: 商店 {} 跳过空交易条目", shop.id);
                return;
            }
            target.add(entry);
        } catch (Exception e) {
            LOGGER.warn("QShop: 商店 {} 跳过无效交易条目: {}", shop.id, e.getMessage());
        }
    }

    /** 解析失败返回 null(文件级错误);单个条目失败会被跳过并警告 */
    public static Shop shopFromJson(JsonObject o) {
        Shop shop = new Shop();
        shop.id = o.has("id") ? o.get("id").getAsString() : "";
        if (shop.id.isEmpty()) {
            return null;
        }
        if (o.has("uuid")) {
            try {
                shop.uuid = UUID.fromString(o.get("uuid").getAsString());
            } catch (Exception e) {
                shop.uuid = null;
            }
        } else {
            shop.uuid = null;
        }
        if (o.has("displayName")) {
            shop.displayName = o.get("displayName").getAsString();
        }
        if (o.has("currency")) {
            shop.currency = o.get("currency").getAsString();
        }
        if (o.has("icon")) {
            try {
                shop.icon = parseItem(o.get("icon"));
            } catch (Exception e) {
                LOGGER.warn("商店 {} 图标解析失败,忽略", shop.id);
            }
        }
        // 旧格式:直接读取 entries(生成一个默认子商店)
        if (o.has("entries")) {
            for (JsonElement el : o.getAsJsonArray("entries")) {
                parseEntryInto(shop, el, shop.entries);
            }
        }
        // 新格式:子商店列表
        if (o.has("tabs")) {
            shop.tabs.clear();
            for (JsonElement el : o.getAsJsonArray("tabs")) {
                JsonObject to = el.getAsJsonObject();
                ShopTab t = new ShopTab();
                if (to.has("uuid") && !to.get("uuid").getAsString().isBlank()) {
                    t.uuid = to.get("uuid").getAsString().trim();
                }
                t.ensureUuid();
                t.name = to.has("name") ? to.get("name").getAsString() : "";
                t.description = to.has("description") ? to.get("description").getAsString() : "";
                if (to.has("icon")) {
                    try {
                        t.icon = parseItem(to.get("icon"));
                    } catch (Exception e) {
                        LOGGER.warn("商店 {} 子商店图标解析失败,忽略", shop.id);
                    }
                }
                if (to.has("requiredQuests")) {
                    for (JsonElement qe : to.getAsJsonArray("requiredQuests")) {
                        String q = qe.getAsString();
                        if (q != null && !q.isBlank()) {
                            t.requiredQuests.add(q.trim());
                        }
                    }
                }
                if (to.has("requiredStages")) {
                    for (JsonElement se : to.getAsJsonArray("requiredStages")) {
                        String s = se.getAsString();
                        if (s != null && !s.isBlank()) {
                            t.requiredStages.add(s.trim());
                        }
                    }
                }
                if (to.has("entries")) {
                    for (JsonElement ee : to.getAsJsonArray("entries")) {
                        parseEntryInto(shop, ee, t.entries);
                    }
                }
                shop.tabs.add(t);
            }
        }
        shop.ensureTabs();
        return shop;
    }
}
