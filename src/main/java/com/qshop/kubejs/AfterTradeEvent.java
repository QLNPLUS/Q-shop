package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/**
 * 购买后事件(只读):交易成功扣费/发物/执行指令之后触发。
 *
 * <pre>
 * QShopEvents.afterTrade(event => {
 *     console.log(event.playerName + ' 购买了 ' + event.entryName + ' x' + event.tradedUnits
 *             + ',实付 ' + event.paidPrice + ' ' + event.currency);
 * });
 * </pre>
 */
public class AfterTradeEvent extends EventJS {

    private final ServerPlayer player;
    private final Shop shop;
    private final int tabIndex;
    private final int entryIndex;
    private final ShopEntry entry;
    private final int tradedUnits;
    private final int totalItems;
    private final boolean partial;

    public AfterTradeEvent() {
        this(null, null, 0, 0, null, 0, 0, false);
    }

    public AfterTradeEvent(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                           ShopEntry entry, int tradedUnits, int totalItems, boolean partial) {
        this.player = player;
        this.shop = shop;
        this.tabIndex = tabIndex;
        this.entryIndex = entryIndex;
        this.entry = entry;
        this.tradedUnits = tradedUnits;
        this.totalItems = totalItems;
        this.partial = partial;
    }

    public ServerPlayer getPlayer() {
        return player;
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

    public String getEntryType() {
        return entry == null ? "" : entry.type.name();
    }

    public String getEntryName() {
        return entry == null ? "" : entry.displayNameOrItem();
    }

    public double getPrice() {
        return entry == null ? 0 : entry.price;
    }

    public String getCurrency() {
        return entry == null ? "" : (entry.currencyId == null ? "" : entry.currencyId);
    }

    /** 实际成交单位数(可能因余额/限购小于请求数) */
    public int getTradedUnits() {
        return tradedUnits;
    }

    /** 实际成交涉及的物品总件数 */
    public int getTotalItems() {
        return totalItems;
    }

    /** 实际支付金额(单价 × 成交单位数) */
    public double getPaidPrice() {
        return entry == null ? 0 : entry.price * tradedUnits;
    }

    /** 是否因余额/物品/限购不足而部分成交 */
    public boolean isPartial() {
        return partial;
    }
}
