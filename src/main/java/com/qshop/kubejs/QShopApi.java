package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Public Builder-first KubeJS API. JSON CRUD methods intentionally are not
 * exposed here; use EntryBuilder and TabBuilder for all shop mutations.
 */
public final class QShopApi {

    public static final QShopApi INSTANCE = new QShopApi();

    private QShopApi() {
    }

    public void open(String shopRef, Player player) {
        QShopBindings.INSTANCE.open(shopRef, player);
    }

    public void openByUuid(String shopUuid, Player player) {
        QShopBindings.INSTANCE.openByUuid(shopUuid, player);
    }

    public Shop getShop(String shopRef) {
        return QShopBindings.INSTANCE.getShop(shopRef);
    }

    public ShopTab getTab(String shopRef) {
        return QShopBindings.INSTANCE.getTab(shopRef);
    }

    public ShopTab getTab(String shopRef, Object tabRef) {
        return QShopBindings.INSTANCE.getTab(shopRef, tabRef);
    }

    public ShopEntry getEntry(String shopRef, Object entryRef) {
        return QShopBindings.INSTANCE.getEntry(shopRef, entryRef);
    }

    public ShopEntry getEntry(String shopRef, Object tabRef, Object entryRef) {
        return QShopBindings.INSTANCE.getEntry(shopRef, tabRef, entryRef);
    }

    public boolean exists(String shopRef) {
        return QShopBindings.INSTANCE.exists(shopRef);
    }

    public List<String> getShopIds() {
        return QShopBindings.INSTANCE.getShopIds();
    }

    public String getShopUuid(String shopRef) {
        return QShopBindings.INSTANCE.getShopUuid(shopRef);
    }

    public int getTabCount(String shopRef) {
        return QShopBindings.INSTANCE.getTabCount(shopRef);
    }

    public int getEntryCount(String shopRef) {
        return QShopBindings.INSTANCE.getEntryCount(shopRef);
    }

    public int getEntryCount(String shopRef, Object tabRef) {
        return QShopBindings.INSTANCE.getEntryCount(shopRef, tabRef);
    }

    public List<String> getCurrencies() {
        return QShopBindings.INSTANCE.getCurrencies();
    }

    public boolean createCurrency(String id, String name, String color) {
        return QShopBindings.INSTANCE.createCurrency(id, name, color);
    }

    public double getBalance(Player player, String currencyId) {
        return QShopBindings.INSTANCE.getBalance(player, currencyId);
    }

    public void giveCurrency(Player player, String currencyId, double amount) {
        QShopBindings.INSTANCE.giveCurrency(player, currencyId, amount);
    }

    public void takeCurrency(Player player, String currencyId, double amount) {
        QShopBindings.INSTANCE.takeCurrency(player, currencyId, amount);
    }

    public void setCurrency(Player player, String currencyId, double amount) {
        QShopBindings.INSTANCE.setCurrency(player, currencyId, amount);
    }

    public boolean createShop(String id, String displayName, String currency) {
        return QShopBindings.INSTANCE.createShop(id, displayName, currency);
    }

    public boolean createShop(String id, String displayName) {
        return QShopBindings.INSTANCE.createShop(id, displayName);
    }

    public boolean createShop(String id) {
        return QShopBindings.INSTANCE.createShop(id);
    }

    public boolean removeShop(String shopRef) {
        return QShopBindings.INSTANCE.removeShop(shopRef);
    }

    public EntryBuilder entry(String shopRef) {
        return QShopBindings.INSTANCE.entry(shopRef);
    }

    public EntryBuilder entry(String shopRef, Object tabRef) {
        return QShopBindings.INSTANCE.entry(shopRef, tabRef);
    }

    public TabBuilder tab(String shopRef) {
        return QShopBindings.INSTANCE.tab(shopRef);
    }

    /** Remove a tab by index or UUID; the last tab cannot be removed. */
    public boolean removeTab(String shopRef, Object tabRef) {
        Shop shop = getShop(shopRef);
        ShopTab tab = getTab(shopRef, tabRef);
        if (shop == null || tab == null || shop.tabs.size() <= 1) {
            return false;
        }
        shop.tabs.remove(tab);
        shop.ensureTabs();
        ShopManager.save(shop);
        return true;
    }

    /** Remove an entry by index or UUID within the selected tab. */
    public boolean removeEntry(String shopRef, Object tabRef, Object entryRef) {
        Shop shop = getShop(shopRef);
        ShopTab tab = getTab(shopRef, tabRef);
        ShopEntry entry = getEntry(shopRef, tabRef, entryRef);
        if (shop == null || tab == null || entry == null) {
            return false;
        }
        tab.entries.remove(entry);
        ShopManager.save(shop);
        return true;
    }

    public boolean clearEntryLimits(String shopId, String tabUuid, String entryUuid) {
        return QShopBindings.INSTANCE.clearEntryLimits(shopId, tabUuid, entryUuid);
    }

    public boolean clearTabLimits(String shopId, String tabUuid) {
        return QShopBindings.INSTANCE.clearTabLimits(shopId, tabUuid);
    }

    public boolean clearShopLimits(String shopId) {
        return QShopBindings.INSTANCE.clearShopLimits(shopId);
    }

    public boolean refreshTab(String shopRef, Object tabRef, int count, Object pool) {
        return QShopBindings.INSTANCE.refreshTab(shopRef, tabRef, count, pool);
    }

    public boolean refreshTab(String shopRef, Object tabRef, Object options) {
        return QShopBindings.INSTANCE.refreshTab(shopRef, tabRef,
                dev.latvian.mods.kubejs.util.JsonIO.of(options).getAsJsonObject());
    }

    public void reload() {
        QShopBindings.INSTANCE.reload();
    }
}
