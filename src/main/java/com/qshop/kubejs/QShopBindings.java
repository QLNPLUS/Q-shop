package com.qshop.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.qshop.QShopMod;
import com.qshop.api.CurrencyService;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.data.QShopSavedData;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopJson;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * KubeJS 脚本绑定 QShop。
 *
 * <p>用法示例(server_scripts):
 * <pre>
 * // 打开商店
 * QShop.open('starter', event.player);
 * QShop.openByUuid('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx', event.player);
 *
 * // 货币
 * QShop.giveCurrency(event.player, 'coins', 100);
 * QShop.takeCurrency(event.player, 'coins', 10);
 * QShop.setCurrency(event.player, 'points', 50);
 * let b = QShop.getBalance(event.player, 'coins');
 *
 * // 交易项目增删改(JsonObject 可用 JsonIO.of({...}) 构造;注意是 JsonIO 不是 JsonUtils)
 * QShop.createShop('vip', 'VIP 商店', 'coins');   // 默认货币可选,空则 coins
 * QShop.addEntry('vip', JsonIO.of({type: 'SELL', item: 'minecraft:diamond',
 *        price: 100, currency: 'coins', globalLimit: 100, playerLimit: 10}));
 * QShop.addEntry('vip', 1, JsonIO.of({type: 'BUY', item: {item: 'minecraft:oak_log', count: 8},
 *        price: 2, currency: 'coins'}));   // 第二个参数:Number=子商店序号(0 起),String=子商店 uuid
 * QShop.updateEntry('vip', 0, JsonIO.of({type: 'SELL', item: 'minecraft:netherite_ingot',
 *        price: 500, currency: 'coins'}));
 * QShop.removeEntry('vip', 1, 0);          // 删除子商店 1 的第 0 条
 * QShop.removeShop('vip');
 *
 * // 子商店(tab)增删改(序号或 uuid 两种方式;options 形式支持全部子商店字段)
 * QShop.addTab('vip', '武器', 'minecraft:iron_sword');       // 图标可选
 * QShop.addTab('vip', '防具', 'minecraft:diamond_chestplate', 'my-fixed-tab-uuid'); // 指定 uuid,空则随机
 * QShop.addTab('vip', { name: '每日卡池', icon: 'minecraft:paper', uuid: 'daily',
 *     description: '每日刷新', requiredQuests: ['q1'], requiredStages: ['vip'],
 *     requiredStageDescriptions: ['VIP 阶段'] });   // 推荐:全字段
 * QShop.updateTab('vip', 0, '装备', 'minecraft:diamond_chestplate');   // 按序号(旧式)
 * QShop.updateTab('vip', 'daily', { name: '新名', description: '新描述', requiredQuests: [] }); // 推荐
 * let tabUuid = QShop.getShopTabUuid('vip', 0);
 * QShop.updateTabByUuid('vip', tabUuid, '装备', null);         // 按 uuid(旧式)
 * QShop.removeTabByUuid('vip', tabUuid);                       // 至少保留一个子商店
 *
 * // 交易项目按 uuid 匹配(条目 JSON 里带 "uuid" 字段可指定,缺省随机生成)
 * QShop.addEntry('vip', tabUuid, JsonIO.of({type: 'SELL', item: 'minecraft:diamond', price: 100,
 *        uuid: 'my-fixed-entry-uuid'}));
 * QShop.updateEntryByUuid('vip', tabUuid, entryUuid, JsonIO.of({type: 'SELL', item: 'minecraft:netherite_ingot'}));
 * QShop.removeEntryByUuid('vip', tabUuid, entryUuid);
 *
 * // builder 方式(与 JSON 双轨并存,适合链式书写)
 * QShop.entry('vip').sell('minecraft:diamond').price(100, 'coins')
 *     .playerLimit(10, 'DAILY').description('§a稀有').uuid('my-entry').add();
 * QShop.entry('vip').cmd('give %player% minecraft:elytra 1', true, true).price(50, 'coins').add();
 * QShop.tab('vip').name('武器').icon('minecraft:iron_sword').uuid('my-tab').add();
 *
 * // 刷新子商店:清空并从权重池随机生成 count 条交易(池条目 = 标准 ShopEntry JSON + weight)
 * QShop.refreshTab('card', 'daily-tab-uuid', 10, [
 *     { type: 'BUY', item: { item: 'minecraft:diamond', count: 1, nbt: '{...}' },
 *       price: 1000, currency: 'coins', playerLimit: 5, weight: 20 },
 * ]);
 * </pre>
 *
 * <p>注意:新建商店自带一个默认子商店,getTabCount 初始为 1;addTab 每次新增一个。</p>
 */
final class QShopBindings {

    public static final QShopBindings INSTANCE = new QShopBindings();

    private QShopBindings() {
    }

    // ---------------- 打开商店 ----------------

    /** 按商店 id 或 uuid 打开商店 */
    public void open(String shopIdOrUuid, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        ShopManager.openShop(sp, shopIdOrUuid);
    }

    /** 按 uuid 打开商店 */
    public void openByUuid(String uuid, Player player) {
        open(uuid, player);
    }

    // ---------------- 统一对象查询 ----------------

    /** 按 shop ID 或 shop UUID 获取商店对象。 */
    public Shop getShop(String shopRef) {
        return ShopManager.get(shopRef);
    }

    /** 获取指定 tab；tabRef 为索引(Number)，UUID(String)，或 null(第一个 tab)。 */
    public ShopTab getTab(String shopRef, Object tabRef) {
        Shop shop = getShop(shopRef);
        if (shop == null) {
            return null;
        }
        shop.ensureTabs();
        return resolveTab(shop, tabRef);
    }

    /** 获取默认 tab。 */
    public ShopTab getTab(String shopRef) {
        return getTab(shopRef, null);
    }

    /** 获取指定 entry；entryRef 为索引(Number)或 UUID(String)。 */
    public ShopEntry getEntry(String shopRef, Object tabRef, Object entryRef) {
        ShopTab tab = getTab(shopRef, tabRef);
        if (tab == null || entryRef == null) {
            return null;
        }
        if (entryRef instanceof Number n) {
            int index = n.intValue();
            return index >= 0 && index < tab.entries.size() ? tab.entries.get(index) : null;
        }
        if (entryRef instanceof String uuid) {
            return entryByUuid(tab, uuid);
        }
        return null;
    }

    /** 获取默认 tab 中的 entry。 */
    public ShopEntry getEntry(String shopRef, Object entryRef) {
        return getEntry(shopRef, null, entryRef);
    }

    // ---------------- 商店信息 ----------------

    public boolean exists(String shopId) {
        return ShopManager.get(shopId) != null;
    }

    public List<String> getShopIds() {
        return ShopManager.all().stream().map(s -> s.id).toList();
    }

    public String getShopUuid(String shopId) {
        Shop s = ShopManager.get(shopId);
        return s == null ? null : s.uuid.toString();
    }

    // ---------------- 货币 ----------------

    public double getBalance(Player player, String currencyId) {
        if (!(player instanceof ServerPlayer)) {
            return 0;
        }
        return CurrencyService.INSTANCE.getBalance((ServerPlayer) player, currencyId);
    }

    public void giveCurrency(Player player, String currencyId, double amount) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        CurrencyService.INSTANCE.deposit(sp, currencyId, amount,
                CurrencyService.SOURCE_KUBEJS, null);
    }

    public void takeCurrency(Player player, String currencyId, double amount) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        CurrencyService.INSTANCE.withdraw(sp, currencyId, amount,
                CurrencyService.SOURCE_KUBEJS, null);
    }

    public void setCurrency(Player player, String currencyId, double amount) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        CurrencyService.INSTANCE.set(sp, currencyId, amount,
                CurrencyService.SOURCE_KUBEJS, null);
    }

    public List<String> getCurrencies() {
        return CurrencyRegistry.all().stream().map(c -> c.id).toList();
    }

    /** 创建货币类型(写入 currencies.json);重复 id 或非法颜色返回 false */
    public boolean createCurrency(String id, String name, String color) {
        return CurrencyRegistry.create(id, name, color);
    }

    // ---------------- 商店/交易项目 增删改 ----------------

    /** 创建商店(重复 id 返回 false) */
    public boolean createShop(String id) {
        return createShop(id, id, null);
    }

    /** 创建商店并指定显示名 */
    public boolean createShop(String id, String displayName) {
        return createShop(id, displayName, null);
    }

    /**
     * 创建商店并指定显示名与默认货币(currency 为 null/空时默认 "coins" 金币)。
     * 默认货币决定商店 GUI 底部显示的余额货币。
     */
    public boolean createShop(String id, String displayName, String currency) {
        if (id == null || id.isEmpty() || ShopManager.get(id) != null) {
            return false;
        }
        Shop shop = new Shop();
        shop.id = id;
        shop.displayName = displayName == null ? "" : displayName;
        shop.currency = (currency == null || currency.isBlank()) ? "coins" : currency;
        shop.uuid = UUID.randomUUID();
        ShopManager.save(shop);
        return true;
    }

    /** 删除商店(同时删除配置文件),返回是否成功 */
    public boolean removeShop(String id) {
        Shop shop = ShopManager.get(id);
        return shop != null && ShopManager.deleteShop(shop.id);
    }

    // ---------------- 子商店(tab) 增删改 ----------------

    /**
     * 添加子商店(统一入口):name 传字符串 = 仅名称;传 JsonObject = 全部字段选项。
     * <pre>
     * QShop.addTab('vip', '武器');                    // 仅名称
     * QShop.addTab('vip', '武器', 'minecraft:iron_sword');          // + 图标(旧式)
     * QShop.addTab('vip', '武器', 'minecraft:iron_sword', 'tab-uuid'); // + uuid(旧式)
     * QShop.addTab('vip', {                            // 推荐:选项对象(全部子商店字段)
     *     name: '每日卡池',
     *     icon: 'minecraft:paper',          // 可选(物品 id / {item,count,nbt} / base64)
     *     uuid: 'daily-tab-uuid',           // 可选,留空随机
     *     description: '每日刷新',           // 可选,悬停 tooltip
     *     requiredQuests: ['quest-id-1'],   // 可选,FTB 任务
     *     requiredStages: ['vip'],          // 可选,阶段
     *     requiredStageDescriptions: ['VIP 阶段'], // 可选,阶段显示描述
     *     showWhenRequirementsNotMet: true  // 可选,未满足时显示锁定 tab
     * });
     * </pre>
     */
    public boolean addTab(String shopId, Object nameOrOptions) {
        JsonObject options = asJsonObject(nameOrOptions);
        if (options != null) {
            // 选项对象形式
            if (!options.has("name") || !options.get("name").isJsonPrimitive()) {
                return false;
            }
            Shop shop = ShopManager.get(shopId);
            if (shop == null) {
                return false;
            }
            shop.ensureTabs();
            ShopTab t = new ShopTab();
            t.name = options.get("name").getAsString();
            if (options.has("uuid") && options.get("uuid").isJsonPrimitive()
                    && !options.get("uuid").getAsString().isBlank()) {
                t.uuid = options.get("uuid").getAsString().trim();
            }
            t.ensureUuid();
            if (options.has("icon")) {
                try {
                    t.icon = ShopJson.parseItem(options.get("icon"));
                } catch (Exception ignored) {
                    t.icon = ItemStack.EMPTY;
                }
            }
            if (options.has("description") && options.get("description").isJsonPrimitive()) {
                t.description = options.get("description").getAsString();
            }
            readStringList(options, "requiredQuests", t.requiredQuests);
            readStringList(options, "requiredStages", t.requiredStages);
            readStringListPreserveEmpty(options, "requiredStageDescriptions", t.requiredStageDescriptions);
            if (options.has("showWhenRequirementsNotMet")
                    && options.get("showWhenRequirementsNotMet").isJsonPrimitive()) {
                t.showWhenRequirementsNotMet = options.get("showWhenRequirementsNotMet").getAsBoolean();
            }
            shop.tabs.add(t);
            ShopManager.save(shop);
            return true;
        }
        return addTab(shopId, nameOrOptions == null ? "" : nameOrOptions.toString(), null, null);
    }

    /**
     * 把 JS 对象/JsonObject 统一转成 JsonObject;字符串/数字等返回 null。
     * Rhino 只有在参数类型显式为 JsonObject 时才会自动转换 JS 对象,
     * 因此 Object 形参需要在这里手动经 JsonIO.of 转换。
     */
    private static JsonObject asJsonObject(Object o) {
        if (o instanceof JsonObject jo) {
            return jo;
        }
        if (o == null) {
            return null;
        }
        try {
            JsonElement el = new com.google.gson.Gson().toJsonTree(o);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 添加子商店并指定图标(图标 JSON 与物品同写法:id / {item,count,nbt} / base64) */
    public boolean addTab(String shopId, String name, JsonElement iconJson) {
        return addTab(shopId, name, iconJson, null);
    }

    /**
     * 添加子商店并指定图标与 uuid(uuid 传 null/空字符串则随机生成)。
     * 指定 uuid 便于脚本里后续用 uuid 稳定引用(子商店按 uuid 匹配)。
     */
    public boolean addTab(String shopId, String name, JsonElement iconJson, String uuid) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        ShopTab t = new ShopTab();
        if (uuid != null && !uuid.isBlank()) {
            t.uuid = uuid.trim();
        }
        t.ensureUuid();
        t.name = name == null ? "" : name;
        if (iconJson != null) {
            try {
                t.icon = ShopJson.parseItem(iconJson);
            } catch (Exception ignored) {
                t.icon = ItemStack.EMPTY;
            }
        }
        shop.tabs.add(t);
        ShopManager.save(shop);
        return true;
    }

    /**
     * 修改子商店(统一入口):tabRef 为 Number=序号 / String=uuid;
     * nameOrOptions 传字符串 = 仅改名称;传 JsonObject = 只更新其中出现的字段
     * (icon 传 null 表示清除;requiredQuests/requiredStages/requiredStageDescriptions 传空数组表示清空)。
     * <pre>
     * QShop.updateTab('vip', 0, '新名');               // 仅改名称(旧式)
     * QShop.updateTab('vip', 0, '新名', 'minecraft:diamond'); // + 图标(旧式)
     * QShop.updateTab('vip', 'daily-tab-uuid', {        // 推荐:选项对象
     *     name: '新名字',
     *     description: '新描述',
     *     requiredQuests: [],               // 清空任务要求
     *     requiredStageDescriptions: [],    // 清空阶段描述
     * });
     * QShop.updateTab('vip', 0, { icon: null });        // 清除图标
     * </pre>
     */
    public boolean updateTab(String shopId, Object tabRef, Object nameOrOptions) {
        JsonObject options = asJsonObject(nameOrOptions);
        if (options != null) {
            // 选项对象形式:只更新出现的字段
            Shop shop = ShopManager.get(shopId);
            if (shop == null) {
                return false;
            }
            shop.ensureTabs();
            ShopTab t = resolveTab(shop, tabRef);
            if (t == null) {
                return false;
            }
            if (options.has("name") && options.get("name").isJsonPrimitive()) {
                t.name = options.get("name").getAsString();
            }
            if (options.has("icon")) {
                JsonElement icon = options.get("icon");
                if (icon.isJsonNull()) {
                    t.icon = ItemStack.EMPTY;
                } else {
                    try {
                        t.icon = ShopJson.parseItem(icon);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (options.has("description") && options.get("description").isJsonPrimitive()) {
                t.description = options.get("description").getAsString();
            }
            if (options.has("requiredQuests")) {
                t.requiredQuests.clear();
                readStringList(options, "requiredQuests", t.requiredQuests);
            }
            if (options.has("requiredStages")) {
                t.requiredStages.clear();
                readStringList(options, "requiredStages", t.requiredStages);
            }
            if (options.has("requiredStageDescriptions")) {
                t.requiredStageDescriptions.clear();
                readStringListPreserveEmpty(options, "requiredStageDescriptions", t.requiredStageDescriptions);
            }
            if (options.has("showWhenRequirementsNotMet")
                    && options.get("showWhenRequirementsNotMet").isJsonPrimitive()) {
                t.showWhenRequirementsNotMet = options.get("showWhenRequirementsNotMet").getAsBoolean();
            }
            ShopManager.save(shop);
            return true;
        }
        // 旧式:仅改名称(图标不变),tabRef 必须为序号
        String name = nameOrOptions == null ? null : nameOrOptions.toString();
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        int tabIndex = tabRef instanceof Number n ? n.intValue() : -1;
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return false;
        }
        ShopTab t = shop.tabs.get(tabIndex);
        if (name != null) {
            t.name = name;
        }
        ShopManager.save(shop);
        return true;
    }

    /** 修改子商店:名称/图标(传 null 表示不改该项;图标传空 JsonObject 表示清除) */
    public boolean updateTab(String shopId, int tabIndex, String name, JsonElement iconJson) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return false;
        }
        ShopTab t = shop.tabs.get(tabIndex);
        if (name != null) {
            t.name = name;
        }
        if (iconJson != null) {
            try {
                ItemStack icon = ShopJson.parseItem(iconJson);
                t.icon = icon == null ? ItemStack.EMPTY : icon;
            } catch (Exception ignored) {
            }
        }
        ShopManager.save(shop);
        return true;
    }

    /** 把 options 里的字符串数组字段读入目标列表 */
    private static void readStringList(JsonObject options, String key, List<String> target) {
        if (!options.has(key) || !options.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement el : options.getAsJsonArray(key)) {
            if (el != null && el.isJsonPrimitive() && !el.getAsString().isBlank()) {
                target.add(el.getAsString().trim());
            }
        }
    }

    /** 读取阶段描述并保留中间空字符串,以维持与 requiredStages 的索引对应。 */
    private static void readStringListPreserveEmpty(JsonObject options, String key, List<String> target) {
        if (!options.has(key) || !options.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement el : options.getAsJsonArray(key)) {
            target.add(el != null && el.isJsonPrimitive() ? el.getAsString().trim() : "");
        }
    }

    /** 删除子商店(至少保留一个),返回是否成功 */
    public boolean removeTab(String shopId, int tabIndex) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        if (shop.tabs.size() <= 1 || tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return false;
        }
        shop.tabs.remove(tabIndex);
        shop.ensureTabs();
        ShopManager.save(shop);
        return true;
    }

    public int getTabCount(String shopId) {
        Shop shop = ShopManager.get(shopId);
        return shop == null ? 0 : shop.tabs.size();
    }

    /** 按序号取子商店 uuid(便于后续按 uuid 操作) */
    public String getShopTabUuid(String shopId, int tabIndex) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return null;
        }
        shop.ensureTabs();
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return null;
        }
        return shop.tabs.get(tabIndex).uuid;
    }

    /** 按序号取交易条目 uuid(便于后续按 uuid 操作) */
    public String getShopEntryUuid(String shopId, int tabIndex, int entryIndex) {
        return getShopEntryUuid(shopId, Integer.valueOf(tabIndex), Integer.valueOf(entryIndex));
    }

    /** 按统一引用规则获取 entry UUID。 */
    public String getShopEntryUuid(String shopRef, Object tabRef, Object entryRef) {
        ShopEntry entry = getEntry(shopRef, tabRef, entryRef);
        return entry == null ? null : entry.uuid;
    }

    /** 按统一引用规则获取指定 tab 的 entry 数量。 */
    public int getEntryCount(String shopRef, Object tabRef) {
        ShopTab tab = getTab(shopRef, tabRef);
        return tab == null ? 0 : tab.entries.size();
    }

    /** 默认 tab 的 entry 数量。 */
    public int getEntryCount(String shopRef) {
        return getEntryCount(shopRef, null);
    }
    /* 保留旧版实现的源码兼容入口；统一逻辑由上面的 Object 版本处理。 */
    // ---------------- 交易项目 增删改(带子商店) ----------------

    /** 向商店默认子商店(第一个)追加一个交易条目 */
    public boolean addEntry(String shopId, JsonObject entryJson) {
        return addEntry(shopId, 0, entryJson);
    }

    /**
     * 向指定子商店追加一个交易条目。
     *
     * <p>tabRef 传 Number 表示子商店序号(0 起),传 String 表示子商店 uuid。
     * 合并了原来的 int/String 两个重载:Rhino 解析数字参数时对 int/String 重载有歧义
     * (数字 1 会被当成 uuid "1" 静默找不到子商店),统一为 Object 后按类型分派。</p>
     *
     * @param entryJson 与配置文件条目同格式的 JSON,物品支持 id / {item,count,nbt} / base64 三种写法
     */
    public boolean addEntry(String shopId, Object tabRef, JsonObject entryJson) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null || entryJson == null) {
            return false;
        }
        shop.ensureTabs();
        try {
            ShopEntry e = ShopJson.entryFromJson(entryJson);
            boolean empty = e.item.isEmpty() && e.give.isEmpty() && e.receive.isEmpty();
            if (e.type != ShopEntryType.COMMAND && empty) {
                return false;
            }
            ShopTab target = resolveTab(shop, tabRef);
            if (target == null) {
                return false;
            }
            target.entries.add(e);
            ShopManager.save(shop);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 解析子商店引用:null→默认(第一个)子商店;Number→序号(越界返回 null);String→uuid;其他返回 null */
    static ShopTab resolveTab(Shop shop, Object tabRef) {
        if (tabRef == null) {
            return shop.tabs.isEmpty() ? null : shop.tabs.get(0);
        }
        if (tabRef instanceof Number n) {
            int idx = n.intValue();
            if (idx < 0 || idx >= shop.tabs.size()) {
                return null;
            }
            return shop.tabs.get(idx);
        }
        if (tabRef instanceof String s) {
            return tabByUuid(shop, s);
        }
        return null;
    }

    /** 用新条目整体替换默认子商店指定位置的条目 */
    public boolean updateEntry(String shopId, int index, JsonObject entryJson) {
        return updateEntry(shopId, 0, index, entryJson);
    }

    /** 用新条目整体替换指定子商店指定位置的条目 */
    public boolean updateEntry(String shopId, int tabIndex, int index, JsonObject entryJson) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null || entryJson == null) {
            return false;
        }
        shop.ensureTabs();
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return false;
        }
        List<ShopEntry> list = shop.tabs.get(tabIndex).entries;
        if (index < 0 || index >= list.size()) {
            return false;
        }
        try {
            ShopEntry e = ShopJson.entryFromJson(entryJson);
            boolean empty = e.item.isEmpty() && e.give.isEmpty() && e.receive.isEmpty();
            if (e.type != ShopEntryType.COMMAND && empty) {
                return false;
            }
            list.set(index, e);
            ShopManager.save(shop);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 删除默认子商店指定位置的交易条目 */
    public boolean removeEntry(String shopId, int index) {
        return removeEntry(shopId, 0, index);
    }

    /** 删除指定子商店指定位置的交易条目 */
    public boolean removeEntry(String shopId, int tabIndex, int index) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return false;
        }
        List<ShopEntry> list = shop.tabs.get(tabIndex).entries;
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        ShopManager.save(shop);
        return true;
    }

    /** 默认子商店的条目数 */
    /** 指定子商店的条目数 */
    public int getEntryCount(String shopId, int tabIndex) {
        return getEntryCount(shopId, Integer.valueOf(tabIndex));
    }

    // ---------------- 子商店 / 交易项目:按 uuid 匹配 ----------------

    private static ShopTab tabByUuid(Shop shop, String tabUuid) {
        if (shop == null || tabUuid == null) {
            return null;
        }
        for (ShopTab t : shop.tabs) {
            if (tabUuid.equals(t.uuid)) {
                return t;
            }
        }
        return null;
    }

    private static ShopEntry entryByUuid(ShopTab tab, String entryUuid) {
        if (tab == null || entryUuid == null) {
            return null;
        }
        for (ShopEntry e : tab.entries) {
            if (entryUuid.equals(e.uuid)) {
                return e;
            }
        }
        return null;
    }

    /** 解析交易项目引用:零基数字索引或 UUID,不支持名称。 */
    private static ShopEntry resolveEntry(ShopTab tab, Object entryRef) {
        if (tab == null || entryRef == null) {
            return null;
        }
        if (entryRef instanceof Number n) {
            int index = n.intValue();
            return index >= 0 && index < tab.entries.size() ? tab.entries.get(index) : null;
        }
        if (entryRef instanceof String uuid) {
            return entryByUuid(tab, uuid);
        }
        return null;
    }

    /** 按 uuid 修改子商店名称(图标不变) */
    public boolean updateTabByUuid(String shopId, String tabUuid, String name) {
        return updateTabByUuid(shopId, tabUuid, name, null);
    }

    /** 按 uuid 修改子商店名称/图标(传 null 不改;图标传空 JsonObject 清除) */
    public boolean updateTabByUuid(String shopId, String tabUuid, String name, JsonElement iconJson) {
        Shop shop = ShopManager.get(shopId);
        ShopTab t = tabByUuid(shop, tabUuid);
        if (t == null) {
            return false;
        }
        if (name != null) {
            t.name = name;
        }
        if (iconJson != null) {
            try {
                ItemStack icon = ShopJson.parseItem(iconJson);
                t.icon = icon == null ? ItemStack.EMPTY : icon;
            } catch (Exception ignored) {
            }
        }
        ShopManager.save(shop);
        return true;
    }

    /** 按 uuid 删除子商店(至少保留一个) */
    public boolean removeTabByUuid(String shopId, String tabUuid) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null || shop.tabs.size() <= 1) {
            return false;
        }
        ShopTab t = tabByUuid(shop, tabUuid);
        if (t == null) {
            return false;
        }
        shop.tabs.remove(t);
        shop.ensureTabs();
        ShopManager.save(shop);
        return true;
    }

    /** 按条目 uuid 整体替换交易项目 */
    public boolean updateEntryByUuid(String shopId, String tabUuid, String entryUuid, JsonObject entryJson) {
        Shop shop = ShopManager.get(shopId);
        ShopTab t = tabByUuid(shop, tabUuid);
        if (t == null || entryJson == null) {
            return false;
        }
        ShopEntry target = entryByUuid(t, entryUuid);
        if (target == null) {
            return false;
        }
        try {
            ShopEntry e = ShopJson.entryFromJson(entryJson);
            boolean empty = e.item.isEmpty() && e.give.isEmpty() && e.receive.isEmpty();
            if (e.type != ShopEntryType.COMMAND && empty) {
                return false;
            }
            e.uuid = target.uuid; // 保留原 uuid,避免引用失效
            t.entries.set(t.entries.indexOf(target), e);
            ShopManager.save(shop);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 按条目 uuid 删除交易项目 */
    public boolean removeEntryByUuid(String shopId, String tabUuid, String entryUuid) {
        Shop shop = ShopManager.get(shopId);
        ShopTab t = tabByUuid(shop, tabUuid);
        if (t == null) {
            return false;
        }
        ShopEntry target = entryByUuid(t, entryUuid);
        if (target == null) {
            return false;
        }
        t.entries.remove(target);
        ShopManager.save(shop);
        return true;
    }

    // ---------------- Builder 入口(与 JSON 方式双轨并存) ----------------

    /**
     * 创建交易条目 builder(目标商店,写入默认子商店)。
     *
     * <pre>
     * QShop.entry('vip').sell('minecraft:diamond').price(100, 'coins')
     *     .playerLimit(10, 'DAILY').description('§a稀有').uuid('my-entry').add();
     * </pre>
     */
    public EntryBuilder entry(String shopId) {
        return new EntryBuilder(shopId, null);
    }

    /**
     * 创建交易条目 builder(tabRef:Number=子商店序号 0 起,String=子商店 uuid,null=默认子商店)。
     */
    public EntryBuilder entry(String shopId, Object tabRef) {
        return new EntryBuilder(shopId, tabRef);
    }

    /**
     * 创建子商店 builder。
     *
     * <pre>
     * QShop.tab('vip').name('武器').icon('minecraft:iron_sword').uuid('my-tab').add();
     * </pre>
     */
    public TabBuilder tab(String shopId) {
        return new TabBuilder(shopId);
    }

    /**
     * 物品参数统一解析:ItemStack / 物品 id 字符串 / 物品 JSON / JS 对象(经 KubeJS JsonIO 转换)。
     * 解析失败返回空物品。
     */
    static ItemStack parseItem(Object o) {
        if (o == null) {
            return ItemStack.EMPTY;
        }
        if (o instanceof ItemStack s) {
            return s.copy();
        }
        JsonElement el;
        if (o instanceof String s) {
            el = new JsonPrimitive(s);
        } else {
            try {
                el = new com.google.gson.Gson().toJsonTree(o);
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }
        try {
            return ShopJson.parseItem(el);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ---------------- 限购记录清理 ----------------

    /**
     * 删除某个交易项目的全部限购记录(全服 + 所有在线/离线玩家个人),返回是否成功。
     * shopId 可为商店 ID 或 UUID; tabRef/entryRef 可为零基索引或 UUID,不支持名称。
     */
    public boolean clearEntryLimits(String shopId, Object tabRef, Object entryRef) {
        Shop shop = ShopManager.get(shopId);
        ShopTab t = resolveTab(shop, tabRef);
        ShopEntry e = resolveEntry(t, entryRef);
        if (e == null) {
            return false;
        }
        clearLimitKey(shop.id + "|" + e.uuid);
        return true;
    }

    /**
     * 删除某个子商店内全部条目的限购记录。
     * shopId 可为商店 ID 或 UUID; tabRef 可为零基索引或子商店 UUID,不支持名称。
     */
    public boolean clearTabLimits(String shopId, Object tabRef) {
        Shop shop = ShopManager.get(shopId);
        ShopTab t = resolveTab(shop, tabRef);
        if (t == null) {
            return false;
        }
        for (ShopEntry e : t.entries) {
            if (e.uuid != null && !e.uuid.isEmpty()) {
                clearLimitKey(shop.id + "|" + e.uuid);
            }
        }
        return true;
    }

    /** 删除某个商店全部条目的限购记录 */
    public boolean clearShopLimits(String shopId) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        for (ShopTab t : shop.tabs) {
            for (ShopEntry e : t.entries) {
                if (e.uuid != null && !e.uuid.isEmpty()) {
                    clearLimitKey(shop.id + "|" + e.uuid);
                }
            }
        }
        return true;
    }

    /** 清空全服计数与所有在线/离线玩家的个人计数 */
    private static void clearLimitKey(String key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        QShopSavedData data = QShopSavedData.get(server);
        data.globalCounts.remove(key);
        data.setDirty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            IWallet w = WalletCapability.get(p);
            if (w != null) {
                w.clearLimitCount(key);
            }
        }
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(playerDataDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(playerDataDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dat"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        String uuidText = name.substring(0, name.length() - 4);
                        try {
                            UUID playerUuid = UUID.fromString(uuidText);
                            if (server.getPlayerList().getPlayer(playerUuid) == null) {
                                CurrencyService.INSTANCE.clearLimitCount(server, playerUuid, key);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // Ignore unrelated .dat files in the playerdata directory.
                        }
                    });
        } catch (IOException e) {
            QShopMod.LOGGER.warn("QShop: failed to scan offline player limit data", e);
        }
    }

    /**
     * 刷新指定子商店的交易内容:清空该子商店现有条目,按权重从物品池中抽取
     * {@code count} 条重新生成(每条独立随机,允许重复出现)。
     * tabRef 支持零基数字索引或子商店 UUID,不支持子商店名称。
     *
     * <p>两种写法(server_scripts):
     * <pre>
     * // 写法一:count + 池
     * QShop.refreshTab('card', 'daily-tab-uuid', 10, [
     *     { type: 'BUY', item: { item: 'minecraft:diamond', count: 1, nbt: '{...}' },
     *       price: 1000, currency: 'coins', playerLimit: 5, weight: 20 },
     *     ...
     * ]);
     *
     * // 写法二:选项对象(currency 为未显式指定货币的条目的默认值)
     * QShop.refreshTab('card', 0, { count: 10, currency: 'coins', pool: [ ...同上... ] });
     * </pre>
     *
     * <p><b>池条目 = 标准交易条目 JSON(与 addEntry / 商店配置文件完全相同的格式),
     * 支持 ShopEntry 的全部 18 个字段</b>:
     * <pre>
     * uuid / type / displayName / description / displayItem / item / give / receive /
     * currency / price / globalLimit / playerLimit / limitReset / commands /
     * requiredQuests / requiredStages / requiredStageDescriptions / showWhenRequirementsNotMet
     * </pre>
     * 外加一个仅用于抽卡的选择字段 <b>weight</b>(权重,默认 1;不是条目字段)。
     * 每次生成都会赋予全新 uuid(限购计数从零开始)。</p>
     */
    public boolean refreshTab(String shopId, Object tabRef, int count, Object pool) {
        JsonObject options = new JsonObject();
        options.addProperty("count", count);
        try {
            options.add("pool", new com.google.gson.Gson().toJsonTree(pool));
        } catch (Throwable t) {
            return false;
        }
        return refreshTab(shopId, tabRef, options);
    }

    /** 刷新子商店(选项对象形式,见 {@link #refreshTab(String, Object, int, Object)}) */
    public boolean refreshTab(String shopId, Object tabRef, JsonObject options) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null || options == null) {
            return false;
        }
        shop.ensureTabs();
        ShopTab tab = resolveTab(shop, tabRef);
        if (tab == null) {
            return false;
        }
        int count = options.has("count") ? Math.max(0, options.get("count").getAsInt()) : 1;
        String defaultType = options.has("type") ? options.get("type").getAsString() : "";
        String defaultCurrency = options.has("currency") ? options.get("currency").getAsString()
                : (shop.currency == null || shop.currency.isEmpty() ? "coins" : shop.currency);
        List<JsonObject> poolEntries = parsePool(options.has("pool") ? options.get("pool") : null);
        if (poolEntries.isEmpty()) {
            return false;
        }

        Random random = new Random();
        tab.entries.clear();
        for (int i = 0; i < count; i++) {
            ShopEntry e = buildPoolEntry(weightedPick(poolEntries, random), defaultType, defaultCurrency, random);
            if (e != null) {
                tab.entries.add(e);
            }
        }
        ShopManager.save(shop);
        return true;
    }

    /** 把池参数解析为条目 JsonObject 列表(接受 JsonArray / JsonObject / JS 数组 / JS 对象) */
    private static List<JsonObject> parsePool(Object pool) {
        List<JsonObject> out = new ArrayList<>();
        if (pool == null) {
            return out;
        }
        try {
            JsonElement el = new com.google.gson.Gson().toJsonTree(pool);
            if (el == null) {
                return out;
            }
            if (el.isJsonArray()) {
                for (JsonElement sub : el.getAsJsonArray()) {
                    if (sub != null && sub.isJsonObject()) {
                        out.add(sub.getAsJsonObject());
                    }
                }
            } else if (el.isJsonObject()) {
                out.add(el.getAsJsonObject());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 按 weight 权重随机抽取一条池配置 */
    private static JsonObject weightedPick(List<JsonObject> entries, Random random) {
        double total = 0;
        for (JsonObject o : entries) {
            total += Math.max(0, o.has("weight") ? o.get("weight").getAsDouble() : 1);
        }
        if (total <= 0) {
            return entries.get(random.nextInt(entries.size()));
        }
        double r = random.nextDouble() * total;
        for (JsonObject o : entries) {
            r -= Math.max(0, o.has("weight") ? o.get("weight").getAsDouble() : 1);
            if (r <= 0) {
                return o;
            }
        }
        return entries.get(entries.size() - 1);
    }

    /** 由标准条目 JSON(支持全部 ShopEntry 字段)+ weight 生成一条交易;无效条目返回 null */
    private static ShopEntry buildPoolEntry(JsonObject o, String defaultType, String defaultCurrency, Random random) {
        if (o == null) {
            return null;
        }
        try {
            ShopEntry e = ShopJson.entryFromJson(o); // 完整 18 字段解析(未知物品抛异常)
            boolean empty = e.item.isEmpty() && e.give.isEmpty() && e.receive.isEmpty();
            if (e.type != ShopEntryType.COMMAND && empty) {
                QShopMod.LOGGER.warn("QShop: refreshTab 跳过空条目 {}", o);
                return null;
            }
            // 池默认值:仅当条目 JSON 未显式指定时应用
            if (!o.has("type") && defaultType != null && !defaultType.isEmpty()) {
                e.type = "SELL".equalsIgnoreCase(defaultType) ? ShopEntryType.SELL : ShopEntryType.BUY;
            }
            if (e.currencyId == null || e.currencyId.isEmpty()) {
                e.currencyId = defaultCurrency == null ? "" : defaultCurrency;
            }
            // 刷新语义:每次生成全新 uuid,限购计数从零开始
            e.uuid = UUID.randomUUID().toString();
            return e;
        } catch (Exception ex) {
            QShopMod.LOGGER.warn("QShop: refreshTab 跳过无效条目: {}", ex.getMessage());
            return null;
        }
    }

    /** 从配置文件重新加载所有商店 */
    public void reload() {
        ShopManager.reload();
    }
}
