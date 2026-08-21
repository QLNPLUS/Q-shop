package com.qshop.api;

import com.qshop.shop.ShopEntryType;
import com.qshop.trade.TradeService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Stable Java entry point for QShop addon mods.
 *
 * <p>Addon code should depend on this facade instead of accessing ShopManager,
 * WalletCapability, or the mutable JSON model directly.</p>
 */
public final class QShopAddonApi {

    public static final String API_VERSION = CurrencyService.API_VERSION;

    private QShopAddonApi() {
    }

    public static CurrencyService currency() {
        return CurrencyService.INSTANCE;
    }

    /** Executes a SELL entry against an addon-provided Forge item handler. */
    public static TradeResult sell(ServerPlayer player, IItemHandler inventory,
                                   String shopRef, int tabIndex, int entryIndex,
                                   int requestedUnits) {
        return sell(player, inventory, shopRef, tabIndex, entryIndex, requestedUnits,
                CurrencyService.SOURCE_API, null);
    }

    /** Executes a SELL entry with an addon source and block position. */
    public static TradeResult sell(ServerPlayer player, IItemHandler inventory,
                                   String shopRef, int tabIndex, int entryIndex,
                                   int requestedUnits, ResourceLocation source,
                                   @Nullable BlockPos sourcePos) {
        return TradeService.tradeHandler(player, inventory, shopRef, tabIndex, entryIndex,
                requestedUnits, ShopEntryType.SELL, source, sourcePos);
    }

    /** Executes a SELL entry using a tab UUID/index and entry UUID/index reference. */
    public static TradeResult sell(ServerPlayer player, IItemHandler inventory,
                                   String shopRef, Object tabRef, Object entryRef,
                                   int requestedUnits) {
        return sell(player, inventory, shopRef, tabRef, entryRef, requestedUnits,
                CurrencyService.SOURCE_API, null);
    }

    /** Executes a SELL entry using a tab UUID/index and entry UUID/index reference. */
    public static TradeResult sell(ServerPlayer player, IItemHandler inventory,
                                   String shopRef, Object tabRef, Object entryRef,
                                   int requestedUnits, ResourceLocation source,
                                   @Nullable BlockPos sourcePos) {
        return TradeService.tradeHandler(player, inventory, shopRef, tabRef, entryRef,
                requestedUnits, ShopEntryType.SELL, source, sourcePos);
    }

    /** Executes a BUY entry against an addon-provided Forge item handler. */
    public static TradeResult buy(ServerPlayer player, IItemHandler inventory,
                                  String shopRef, int tabIndex, int entryIndex,
                                  int requestedUnits) {
        return buy(player, inventory, shopRef, tabIndex, entryIndex, requestedUnits,
                CurrencyService.SOURCE_API, null);
    }

    /** Executes a BUY entry with an addon source and block position. */
    public static TradeResult buy(ServerPlayer player, IItemHandler inventory,
                                  String shopRef, int tabIndex, int entryIndex,
                                  int requestedUnits, ResourceLocation source,
                                  @Nullable BlockPos sourcePos) {
        return TradeService.tradeHandler(player, inventory, shopRef, tabIndex, entryIndex,
                requestedUnits, ShopEntryType.BUY, source, sourcePos);
    }

    /** Executes a BUY entry using a tab UUID/index and entry UUID/index reference. */
    public static TradeResult buy(ServerPlayer player, IItemHandler inventory,
                                  String shopRef, Object tabRef, Object entryRef,
                                  int requestedUnits) {
        return buy(player, inventory, shopRef, tabRef, entryRef, requestedUnits,
                CurrencyService.SOURCE_API, null);
    }

    /** Executes a BUY entry using a tab UUID/index and entry UUID/index reference. */
    public static TradeResult buy(ServerPlayer player, IItemHandler inventory,
                                  String shopRef, Object tabRef, Object entryRef,
                                  int requestedUnits, ResourceLocation source,
                                  @Nullable BlockPos sourcePos) {
        return TradeService.tradeHandler(player, inventory, shopRef, tabRef, entryRef,
                requestedUnits, ShopEntryType.BUY, source, sourcePos);
    }
}
