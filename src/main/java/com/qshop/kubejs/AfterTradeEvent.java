package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopTab;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/** Event fired after a successful trade. */
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

    public ShopEntry getEntry() {
        return entry;
    }

    public ShopTab getTab() {
        if (shop == null || tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return null;
        }
        return shop.tabs.get(tabIndex);
    }

    public Shop getShop() {
        return shop;
    }

    public String getPlayerName() {
        return player == null ? "" : player.getGameProfile().getName();
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public int getTradedUnits() {
        return tradedUnits;
    }

    public int getTotalItems() {
        return totalItems;
    }

    /** Actual currency paid: entry.price multiplied by traded units. */
    public double getPaidPrice() {
        return entry == null ? 0 : entry.price * tradedUnits;
    }

    /** True when fewer units completed than the player requested. */
    public boolean isPartial() {
        return partial;
    }
}
