package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/**
 * 购买前事件(可取消):在扣费/扣物品之前触发。
 *
 * <pre>
 * QShopEvents.beforeTrade(event => {
 *     if (event.entryUuid === 'no-sell-item') {
 *         event.cancel();               // 取消这笔交易
 *         event.player.tell('该条目已下架');
 *     }
 * });
 * </pre>
 */
public class BeforeTradeEvent extends EventJS {

    private final ServerPlayer player;
    private final Shop shop;
    private final int tabIndex;
    private final int entryIndex;
    private final ShopEntry entry;
    private final int requestedUnits;

    public BeforeTradeEvent() {
        this(null, null, 0, 0, null, 0);
    }

    public BeforeTradeEvent(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                            ShopEntry entry, int requestedUnits) {
        this.player = player;
        this.shop = shop;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
        this.entry = entry;
        this.requestedUnits = requestedUnits;
    }

    /** 触发交易的玩家 */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** 触发交易的完整对象，便于脚本直接读取 entry.count 等字段。 */
    public ShopEntry getEntry() {
        return entry;
    }

    /** 触发交易所在的完整 tab 对象。 */
    public com.qshop.shop.ShopTab getTab() {
        if (shop == null || tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return null;
        }
        return shop.tabs.get(tabIndex);
    }

    /** 触发交易所在的完整 shop 对象。 */
    public Shop getShop() {
        return shop;
    }

    public String getPlayerName() {
        return player == null ? "" : player.getGameProfile().getName();
    }

    public String getShopId() {
        return shop == null ? "" : shop.id;
    }

    public String getShopUuid() {
        return shop == null || shop.uuid == null ? "" : shop.uuid.toString();
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public String getEntryUuid() {
        return entry == null || entry.uuid == null ? "" : entry.uuid;
    }

    /** 条目类型:BUY / SELL / BARTER / COMMAND */
    public String getEntryType() {
        return entry == null ? "" : entry.type.name();
    }

    /** 条目显示名(自定义名或物品名) */
    public String getEntryName() {
        return entry == null ? "" : entry.displayNameOrItem();
    }

    /** 每单位单价 */
    public double getPrice() {
        return entry == null ? 0 : entry.price;
    }

    /** 货币 id */
    public String getCurrency() {
        return entry == null ? "" : (entry.currencyId == null ? "" : entry.currencyId);
    }

    /** 玩家请求的交易单位数 */
    public int getUnits() {
        return requestedUnits;
    }
}
