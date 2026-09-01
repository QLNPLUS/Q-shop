package com.qshop.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.data.QShopSavedData;
import com.qshop.net.ClientShopEntry;
import com.qshop.net.ClientTab;
import com.qshop.trade.RequirementCheck;
import com.qshop.net.OpenShopPacket;
import com.qshop.net.QShopNetwork;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 商店管理器。商店定义文件位于世界目录下的 serverconfig/qshop/shops/,
 * 一个商店一个 JSON 文件,文件名为商店 id。
 */
public final class ShopManager {

    private static final Logger LOGGER = LogManager.getLogger("QShop");

    private static final Map<String, Shop> SHOPS = new LinkedHashMap<>();
    private static final Map<UUID, Shop> BY_UUID = new HashMap<>();

    private static MinecraftServer server;

    private ShopManager() {
    }

    /** 服务器启动时调用 */
    public static void load(MinecraftServer srv) {
        server = srv;
        Path qshop = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("qshop");
        importDefaultConfig(qshop);
        CurrencyRegistry.load(qshop.resolve("currencies.json"));
        ensureDefaults(qshop);
        reload();
    }

    /**
     * Imports the bundled server shop JSON files for a new world.
     * Existing world configuration is never overwritten.
     */
    private static void importDefaultConfig(Path target) {
        if (Files.exists(target) || server == null || server.getServerDirectory() == null) {
            return;
        }
        Path source = server.getServerDirectory().resolve("defaultconfigs").resolve("qshop");
        if (!Files.isDirectory(source)) {
            return;
        }
        boolean skipStarter = hasNonStarterShop(source);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                if (skipStarter && isStarterShop(relative)) {
                    LOGGER.info("QShop: skipped default starter shop because another default shop is present");
                    continue;
                }
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
            LOGGER.info("QShop: imported default configuration from {} to {}", source, target);
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("QShop: failed to import default configuration from {}", source, e);
        }
    }

    private static boolean hasNonStarterShop(Path source) {
        Path shops = source.resolve("shops");
        if (!Files.isDirectory(shops)) {
            return false;
        }
        try (Stream<Path> files = Files.list(shops)) {
            return files.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")
                    && !path.getFileName().toString().equalsIgnoreCase("starter.json"));
        } catch (IOException e) {
            LOGGER.warn("QShop: failed to inspect default shop files in {}", shops, e);
            return false;
        }
    }

    private static boolean isStarterShop(Path relative) {
        return relative.getNameCount() == 2
                && relative.getName(0).toString().equalsIgnoreCase("shops")
                && relative.getName(1).toString().equalsIgnoreCase("starter.json");
    }

    private static void ensureDefaults(Path qshop) {
        try {
            Path shops = qshop.resolve("shops");
            if (Files.isDirectory(shops)) {
                try (Stream<Path> list = Files.list(shops)) {
                    if (list.findAny().isPresent()) {
                        return;
                    }
                }
            }
            Files.createDirectories(shops);
            Files.writeString(shops.resolve("starter.json"), defaultStarterShopJson());
            LOGGER.info("QShop: 已生成示例商店配置文件 {}", shops.resolve("starter.json"));
        } catch (IOException e) {
            LOGGER.error("QShop: 生成默认商店配置失败", e);
        }
    }

    /** 重新读取全部商店文件(保留内存中已有的 uuid) */
    public static void reload() {
        SHOPS.clear();
        BY_UUID.clear();
        if (server == null) {
            return;
        }
        Path dir = shopsDir();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> list = Files.list(dir)) {
                for (Path p : list.filter(x -> x.getFileName().toString().endsWith(".json")).sorted(Comparator.comparing(x -> x.getFileName().toString())).toList()) {
                    try {
                        JsonObject obj = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                        Shop shop = ShopJson.shopFromJson(obj);
                        if (shop == null || shop.id.isEmpty()) {
                            LOGGER.warn("QShop: 跳过无效商店文件 {}", p);
                            continue;
                        }
                        if (SHOPS.containsKey(shop.id)) {
                            LOGGER.warn("QShop: 商店 id 重复,跳过 {}", shop.id);
                            continue;
                        }
                        if (shop.uuid == null) {
                            shop.uuid = UUID.randomUUID();
                            writeShopFile(shop);
                        }
                        SHOPS.put(shop.id, shop);
                        BY_UUID.put(shop.uuid, shop);
                    } catch (Exception e) {
                        LOGGER.error("QShop: 加载商店文件失败 {}", p, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("QShop: 读取商店目录失败", e);
        }
        LOGGER.info("QShop: 已加载 {} 个商店", SHOPS.size());
        broadcastAllShops();
    }

    /** 保存商店到文件并更新内存 */
    public static void save(Shop shop) {
        // KubeJS ServerEvents.loaded 可能先于本模组的 ServerStartingEvent 触发,
        // 此时 server 尚未初始化;用当前服务器引用自愈,保证脚本内创建/修改立即可见。
        if (server == null) {
            server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        }
        if (server == null || shop == null || shop.id == null || shop.id.isEmpty()) {
            return;
        }
        if (shop.uuid == null) {
            shop.uuid = UUID.randomUUID();
        }
        shop.dataVersion++;
        writeShopFile(shop);
        SHOPS.put(shop.id, shop);
        BY_UUID.put(shop.uuid, shop);
        broadcastShopUpdate(shop);
    }

    /** 仅写文件(不更新内存) */
    private static void writeShopFile(Shop shop) {
        try {
            Path dir = shopsDir();
            Files.createDirectories(dir);
            JsonObject obj = ShopJson.shopToJson(shop);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(dir.resolve(shop.id + ".json"), gson.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("QShop: 保存商店 {} 失败", shop.id, e);
        }
    }

    public static boolean deleteShop(String id) {
        Shop s = SHOPS.remove(id);
        if (s != null) {
            BY_UUID.remove(s.uuid);
        }
        try {
            Files.deleteIfExists(shopsDir().resolve(id + ".json"));
            return s != null;
        } catch (IOException e) {
            LOGGER.error("QShop: 删除商店文件 {} 失败", id, e);
            return s != null;
        }
    }

    /** 按商店 ID 或商店 UUID 查找商店。 */
    public static Shop get(String idOrUuid) {
        if (idOrUuid == null) {
            return null;
        }
        Shop direct = SHOPS.get(idOrUuid);
        return direct != null ? direct : byUuid(idOrUuid);
    }

    public static Shop byUuid(String uuid) {
        try {
            return BY_UUID.get(UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    /** 先按 id 查找,再按 uuid 查找 */
    public static Shop byIdOrUuid(String idOrUuid) {
        return get(idOrUuid);
    }

    public static List<Shop> all() {
        return new ArrayList<>(SHOPS.values());
    }

    public static Path shopsDir() {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("qshop").resolve("shops");
    }

    // ---------------- 打开商店 ----------------

    public static void openShop(ServerPlayer player, String idOrUuid) {
        Shop shop = byIdOrUuid(idOrUuid);
        if (shop != null) {
            openShop(player, shop);
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("qshop.msg.shop_missing", idOrUuid));
        }
    }

    /** 向玩家发送打开商店的数据包(含余额、限额用量、编辑权限等) */
    public static void openShop(ServerPlayer player, Shop shop) {
        openShop(player, shop, false);
    }

    /** 向在线客户端推送商店变化；客户端只会应用当前打开该商店的刷新包。 */
    public static void openShop(ServerPlayer player, Shop shop, boolean refresh) {
        if (player == null || shop == null) {
            return;
        }
        IWallet wallet = WalletCapability.get(player);
        Map<String, Double> balances = wallet == null ? new HashMap<>() : wallet.snapshot();
        QShopSavedData data = null;
        shop.ensureTabs();
        boolean editing = player.hasPermissions(2) && player.isCreative();
        List<ClientTab> tabs = new ArrayList<>();
        for (int ti = 0; ti < shop.tabs.size(); ti++) {
            ShopTab tab = shop.tabs.get(ti);
            boolean tabMet = RequirementCheck.satisfied(player, tab);
            if (!tabMet && !editing && !tab.showWhenRequirementsNotMet) {
                // 子商店的任务/阶段要求未满足且未开启显示:非编辑玩家看不到该子商店
                continue;
            }
            ClientTab ct = new ClientTab();
            ct.serverIndex = ti;
            ct.requirementsMet = tabMet;
            ct.showWhenRequirementsNotMet = tab.showWhenRequirementsNotMet;
            ct.uuid = tab.uuid == null ? "" : tab.uuid;
            ct.name = tab.name;
            ct.description = tab.description == null ? "" : tab.description;
            ct.icon = tab.icon.copy();
            ct.requiredQuests.addAll(tab.requiredQuests);
            ct.requiredStages.addAll(tab.requiredStages);
            ct.requiredStageDescriptions.addAll(tab.requiredStageDescriptions);
            for (int i = 0; i < tab.entries.size(); i++) {
                ShopEntry e = tab.entries.get(i);
                boolean requirementsMet = RequirementCheck.satisfied(player, e);
                if (!requirementsMet && !editing && !e.showWhenRequirementsNotMet) {
                    continue;
                }
                int usedGlobal = 0;
                int usedPlayer = 0;
                String period = e.reset.periodKey();
                // 限购键与交易服务一致:按条目 uuid(无 uuid 时退回位置键)
                String key = e.uuid != null && !e.uuid.isEmpty()
                        ? shop.id + "|" + e.uuid
                        : shop.id + "|" + ti + "|" + i;
                if (e.globalLimit > 0) {
                    if (data == null) {
                        data = QShopSavedData.get(player.getServer());
                    }
                    usedGlobal = data.globalCounts.getCount(key, period);
                }
                if (e.playerLimit > 0 && wallet != null) {
                    usedPlayer = wallet.getLimitCount(key, period);
                }
                // 达到全局或个人限购上限后，普通模式隐藏该交易项目；编辑模式仍保留，便于管理员调整配置。
                if (!editing && ((e.globalLimit > 0 && usedGlobal >= e.globalLimit)
                        || (e.playerLimit > 0 && usedPlayer >= e.playerLimit))) {
                    continue;
                }
                ct.entries.add(ClientShopEntry.from(e, i, requirementsMet, usedGlobal, usedPlayer));
            }
            tabs.add(ct);
        }
        List<Currency> currencies = CurrencyRegistry.all();
        ItemStack icon = shop.icon;
        String displayCurrency = shop.currency == null || shop.currency.isEmpty()
                ? (CurrencyRegistry.firstId() == null ? "" : CurrencyRegistry.firstId())
                : shop.currency;
        QShopNetwork.sendToPlayer(player, new OpenShopPacket(
                shop.id, shop.displayNameOrId(), displayCurrency, icon, tabs, balances, currencies, editing,
                shop.dataVersion, refresh));
    }

    /** 将一个已保存的商店变化主动推送给所有在线玩家。 */
    private static void broadcastShopUpdate(Shop shop) {
        if (server == null || shop == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            openShop(player, shop, true);
        }
    }

    /** 手动 reload 后同步所有仍打开商店界面的客户端。 */
    private static void broadcastAllShops() {
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        for (Shop shop : SHOPS.values()) {
            broadcastShopUpdate(shop);
        }
    }

    /**
     * 向商店添加交易条目(GUI 物品选择器路径)。
     *
     * @param item        BUY/SELL 交易物品;BARTER 获得物;COMMAND 忽略
     * @param giveItem    BARTER 付出物(可空)
     * @param displayItem 展示物品(可空)
     * @param command     COMMAND 类型的指令文本(可空)
     */
    public static boolean addEntry(Shop shop, ShopEntryType type, ItemStack item, ItemStack giveItem,
                                   ItemStack displayItem, double price, String currency, String command) {
        return addEntryToTab(shop, 0, type, item, giveItem, displayItem, price, currency, command,
                List.of(), List.of(), false);
    }

    /** 向指定子商店添加交易条目 */
    public static boolean addEntryToTab(Shop shop, int tabIndex, ShopEntryType type, ItemStack item, ItemStack giveItem,
                                        ItemStack displayItem, double price, String currency, String command) {
        return addEntryToTab(shop, tabIndex, type, item, giveItem, displayItem, price, currency, command,
                List.of(), List.of(), false);
    }

    /** 向指定子商店添加交易条目(含任务/阶段要求) */
    public static boolean addEntryToTab(Shop shop, int tabIndex, ShopEntryType type, ItemStack item, ItemStack giveItem,
                                        ItemStack displayItem, double price, String currency, String command,
                                        List<String> requiredQuests, List<String> requiredStages) {
        return addEntryToTab(shop, tabIndex, type, item, giveItem, displayItem, price, currency, command,
                requiredQuests, requiredStages, false);
    }

    /** 向指定子商店添加交易条目(含任务/阶段要求与未满足时显示设置) */
    public static boolean addEntryToTab(Shop shop, int tabIndex, ShopEntryType type, ItemStack item, ItemStack giveItem,
                                        ItemStack displayItem, double price, String currency, String command,
                                        List<String> requiredQuests, List<String> requiredStages,
                                        boolean showWhenRequirementsNotMet) {
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        List<ShopEntry> list = shop.entriesOf(tabIndex);
        ShopEntry e = new ShopEntry();
        e.uuid = UUID.randomUUID().toString();
        e.currencyId = currency == null ? "" : currency;
        e.price = Math.max(0, price);
        e.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem.copy();
        e.type = type;
        if (requiredQuests != null) {
            for (String s : requiredQuests) {
                if (s != null && !s.isBlank()) {
                    e.requiredQuests.add(s.trim());
                }
            }
        }
        if (requiredStages != null) {
            for (String s : requiredStages) {
                if (s != null && !s.isBlank()) {
                    e.requiredStages.add(s.trim());
                }
            }
        }
        e.showWhenRequirementsNotMet = showWhenRequirementsNotMet;
        switch (type) {
            case BARTER -> {
                if (item == null || item.isEmpty()) {
                    return false;
                }
                e.receive.add(item.copy());
                if (giveItem != null && !giveItem.isEmpty()) {
                    e.give.add(giveItem.copy());
                }
            }
            case COMMAND -> {
                if (command != null && !command.isBlank()) {
                    e.commands.add(new ShopCommand(command.trim(), false, true));
                }
                // 指令条目可附带物品代价(简化模型:玩家提供物品+商店提供指令)
                if (item != null && !item.isEmpty()) {
                    e.item = item.copy();
                }
            }
            default -> {
                if (item == null || item.isEmpty()) {
                    return false;
                }
                e.item = item.copy();
            }
        }
        list.add(e);
        save(shop);
        return true;
    }

    /**
     * 用玩家手持物品向商店添加交易条目(BARTER:主手=获得物,副手=付出物;COMMAND:无需物品)。
     * 返回是否成功。
     */
    public static boolean addEntryFromHeld(ServerPlayer player, Shop shop, ShopEntryType type,
                                           double price, String currency) {
        if (player == null || shop == null) {
            return false;
        }
        ItemStack item = ItemStack.EMPTY;
        ItemStack give = ItemStack.EMPTY;
        if (type == ShopEntryType.BARTER) {
            item = player.getMainHandItem();
            give = player.getOffhandItem();
        } else if (type != ShopEntryType.COMMAND) {
            item = player.getMainHandItem();
        }
        return addEntry(shop, type, item, give, ItemStack.EMPTY, price, currency, "");
    }

    // ---------------- 默认示例商店 ----------------

    private static String defaultStarterShopJson() {
        return """
                {
                  "id": "starter",
                  "displayName": "新手商店",
                  "entries": [
                    {
                      "type": "SELL",
                      "item": { "item": "minecraft:diamond", "count": 1 },
                      "currency": "coins",
                      "price": 50.0
                    },
                    {
                      "type": "BUY",
                      "item": { "item": "minecraft:oak_log", "count": 8 },
                      "currency": "coins",
                      "price": 1.0
                    },
                    {
                      "type": "BARTER",
                      "give": [ { "item": "minecraft:emerald", "count": 3 } ],
                      "receive": [ { "item": "minecraft:diamond", "count": 1 } ]
                    }
                  ]
                }
                """;
    }
}
