package com.qshop.kubejs;

import com.qshop.shop.LimitReset;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopCommand;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;

/**
 * KubeJS 交易条目 builder(链式,与现有 addEntry(JSON) 双轨并存)。
 *
 * <pre>
 * // 出售条目
 * QShop.entry('vip').sell('minecraft:diamond').price(100, 'coins')
 *     .playerLimit(10, 'DAILY').description('§a稀有材料').uuid('my-entry').add();
 *
 * // 指令条目(自动切换 COMMAND 类型)
 * QShop.entry('vip').cmd('give %player% minecraft:elytra 1', true, true).price(50, 'coins').add();
 *
 * // 以物换物
 * QShop.entry('vip', 0).barter({item: 'minecraft:stone', count: 2}, 'minecraft:cobblestone').add();
 * </pre>
 *
 * <p>物品参数支持:ItemStack / 物品 id 字符串 / 物品 JSON / JS 对象(经 KubeJS JsonIO 转换)。
 * tabRef 为 Number=子商店序号(0 起)、String=子商店 uuid、null=默认子商店。</p>
 */
public class EntryBuilder {

    private final String shopId;
    private final Object tabRef;
    private final ShopEntry entry = new ShopEntry();

    EntryBuilder(String shopId, Object tabRef) {
        this.shopId = shopId;
        this.tabRef = tabRef;
    }

    // ---------------- 类型便捷方法 ----------------

    /** 购买条目:玩家付货币,获得 item */
    public EntryBuilder buy(Object item) {
        entry.type = ShopEntryType.BUY;
        return item(item);
    }

    /** 出售条目:玩家交 item,获得货币 */
    public EntryBuilder sell(Object item) {
        entry.type = ShopEntryType.SELL;
        return item(item);
    }

    /** 指令条目:支付货币或物品后执行指令(配合 cmd()) */
    public EntryBuilder command() {
        entry.type = ShopEntryType.COMMAND;
        return this;
    }

    /** 以物换物:give = 玩家付出,receive = 玩家获得 */
    public EntryBuilder barter(Object give, Object receive) {
        entry.type = ShopEntryType.BARTER;
        entry.give.add(QShopBindings.parseItem(give));
        entry.receive.add(QShopBindings.parseItem(receive));
        return this;
    }

    /** 设置 BUY/SELL/COMMAND 的交易物品(可重复调用覆盖) */
    public EntryBuilder item(Object item) {
        entry.item = QShopBindings.parseItem(item);
        return this;
    }

    /** 以物换物:追加一件付出物品 */
    public EntryBuilder give(Object item) {
        entry.give.add(QShopBindings.parseItem(item));
        return this;
    }

    /** 以物换物:追加一件获得物品 */
    public EntryBuilder receive(Object item) {
        entry.receive.add(QShopBindings.parseItem(item));
        return this;
    }

    // ---------------- 价格 / 货币 ----------------

    public EntryBuilder price(double price) {
        entry.price = Math.max(0, price);
        return this;
    }

    /** 单价 + 货币 */
    public EntryBuilder price(double price, String currency) {
        entry.price = Math.max(0, price);
        entry.currencyId = currency == null ? "" : currency;
        return this;
    }

    public EntryBuilder currency(String currencyId) {
        entry.currencyId = currencyId == null ? "" : currencyId;
        return this;
    }

    // ---------------- 限购 ----------------

    public EntryBuilder globalLimit(int limit) {
        entry.globalLimit = Math.max(-1, limit);
        return this;
    }

    public EntryBuilder playerLimit(int limit) {
        entry.playerLimit = Math.max(-1, limit);
        return this;
    }

    /** 个人限购 + 重置周期(NEVER/DAILY/WEEKLY/MONTHLY) */
    public EntryBuilder playerLimit(int limit, String reset) {
        entry.playerLimit = Math.max(-1, limit);
        return limitReset(reset);
    }

    public EntryBuilder limitReset(String reset) {
        entry.reset = (reset == null || reset.isBlank())
                ? LimitReset.NEVER : LimitReset.fromName(reset);
        return this;
    }

    // ---------------- 显示 ----------------

    public EntryBuilder displayName(String name) {
        entry.displayName = name == null ? "" : name;
        return this;
    }

    public EntryBuilder description(String desc) {
        entry.description = desc == null ? "" : desc;
        return this;
    }

    public EntryBuilder displayItem(Object item) {
        entry.displayItem = QShopBindings.parseItem(item);
        return this;
    }

    // ---------------- uuid / 要求 ----------------

    /** 指定条目 uuid(空/省略则随机生成),便于后续 updateEntryByUuid 稳定引用 */
    public EntryBuilder uuid(String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            entry.uuid = uuid.trim();
        }
        return this;
    }

    public EntryBuilder quest(String questId) {
        if (questId != null && !questId.isBlank()) {
            entry.requiredQuests.add(questId.trim());
        }
        return this;
    }

    public EntryBuilder stage(String stage) {
        if (stage != null && !stage.isBlank()) {
            entry.requiredStages.add(stage.trim());
        }
        return this;
    }

    // ---------------- 指令(自动切换 COMMAND 类型) ----------------

    /** 添加购买指令(op=false 玩家权限,silent=true) */
    public EntryBuilder cmd(String command) {
        return cmd(command, false, true);
    }

    /** 添加购买指令(silent=true) */
    public EntryBuilder cmd(String command, boolean op) {
        return cmd(command, op, true);
    }

    /** 添加购买指令(可多次调用添加多条) */
    public EntryBuilder cmd(String command, boolean op, boolean silent) {
        if (command != null && !command.isBlank()) {
            if (entry.type == ShopEntryType.BUY || entry.type == ShopEntryType.SELL) {
                entry.type = ShopEntryType.COMMAND;
            }
            entry.commands.add(new ShopCommand(command, op, silent));
        }
        return this;
    }

    // ---------------- 提交 ----------------

    /** 添加到商店并保存,返回是否成功 */
    public boolean add() {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        boolean empty = entry.item.isEmpty() && entry.give.isEmpty() && entry.receive.isEmpty();
        if (entry.type != ShopEntryType.COMMAND && empty) {
            return false;
        }
        ShopTab target = QShopBindings.resolveTab(shop, tabRef);
        if (target == null) {
            return false;
        }
        entry.ensureUuid();
        for (int i = 0; i < target.entries.size(); i++) {
            if (entry.uuid.equals(target.entries.get(i).uuid)) {
                target.entries.set(i, entry);
                ShopManager.save(shop);
                return true;
            }
        }
        target.entries.add(entry);
        ShopManager.save(shop);
        return true;
    }
}
