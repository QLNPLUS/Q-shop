package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopTab;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/** Event fired before a trade is processed. */
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

    /** Number of trade units requested before limits and inventory checks. */
    public int getUnits() {
        return requestedUnits;
    }
}
