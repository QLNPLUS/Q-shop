package com.qshop.trade;

import com.qshop.api.CurrencyService;
import com.qshop.api.TradeResult;
import com.qshop.config.QShopServerConfig;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.data.QShopSavedData;
import com.qshop.kubejs.QShopTradeEvents;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopCommand;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 交易服务:购买 / 出售 / 以物换物,含全服与个人限购、购买指令。
 * units 表示交易单位数(1 单位 = 条目的 item.count 个物品)。
 */
public final class TradeService {

    private static final Logger LOGGER = LogManager.getLogger("QShop");

    private TradeService() {
    }

    /**
     * 发送交易反馈消息:受服务端配置控制。
     * <ul>
     *   <li>showTradeMessages=false:不显示任何交易提示;</li>
     *   <li>tradeMessagesInActionBar=true:显示在物品栏上方(statsMessage/actionbar)区域,避免聊天栏刷屏;</li>
     *   <li>否则发送到聊天栏。</li>
     * </ul>
     */
    private static void tell(ServerPlayer player, Component msg) {
        if (!QShopServerConfig.showTradeMessages()) {
            return;
        }
        if (QShopServerConfig.tradeMessagesInActionBar()) {
            player.displayClientMessage(msg, true);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    public static void trade(ServerPlayer player, String shopId, int tabIndex, int entryIndex, int requestedUnits) {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            tell(player, Component.translatable("qshop.msg.shop_missing", shopId));
            return;
        }
        var entries = shop.entriesOf(tabIndex);
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }
        ShopEntry e = entries.get(entryIndex);
        if (requestedUnits < 1) {
            requestedUnits = 1;
        }
        if (e.type == ShopEntryType.BARTER
                && (!validBarterStacks(e.give) || !validBarterStacks(e.receive))) {
            tell(player, Component.translatable("qshop.msg.invalid_entry"));
            return;
        }

        // ---- 前提要求检查(FTB 任务 / 阶段) ----
        if (!RequirementCheck.satisfied(player, e)) {
            tell(player, RequirementCheck.formatMissing(player, e));
            return;
        }

        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }

        // ---- 限购键:按条目 uuid(稳定;删除/移动条目不串号)。旧条目无 uuid 时退回位置键 ----
        String key = e.uuid != null && !e.uuid.isEmpty()
                ? shop.id + "|" + e.uuid
                : shop.id + "|" + tabIndex + "|" + entryIndex;
        String period = e.reset.periodKey();

        // ---- 限购检查 ----
        int usedGlobal = 0;
        int usedPlayer = 0;
        QShopSavedData data = null;
        boolean track = e.globalLimit > 0 || e.playerLimit > 0;
        if (e.globalLimit > 0) {
            data = QShopSavedData.get(player.getServer());
            usedGlobal = data.globalCounts.getCount(key, period);
        }
        if (e.playerLimit > 0) {
            usedPlayer = wallet.getLimitCount(key, period);
        }

        // 每个交易单位涉及的物品件数(COMMAND 按物品代价件数计,无物品则按 1 计)
        int itemsPerUnit = switch (e.type) {
            case BARTER -> totalCount(e.receive);
            case COMMAND -> !e.item.isEmpty() ? e.item.getCount() : 1;
            default -> e.item.getCount();
        };
        if (itemsPerUnit <= 0) {
            return;
        }

        int units = requestedUnits;
        if (e.globalLimit > 0) {
            units = Math.min(units, Math.max(0, e.globalLimit - usedGlobal));
        }
        if (e.playerLimit > 0) {
            units = Math.min(units, Math.max(0, e.playerLimit - usedPlayer));
        }
        if (units <= 0) {
            tell(player, Component.translatable("qshop.msg.limit_reached"));
            return;
        }

        // ---- 购买前事件(可取消;只在 KubeJS 安装时触发) ----
        if (!QShopTradeEvents.postBefore(player, shop, tabIndex, entryIndex, e, requestedUnits)) {
            return; // 脚本调用 event.cancel() 取消了交易
        }

        // ---- 按余额/库存折算最大单位数 ----
        switch (e.type) {
            case BUY -> {
                if (e.price > 0) {
                    // 用 long 计算并夹到 int 上限,避免大余额时 (int) 强转溢出(变负数导致无法交易)
                    long byBalance = (long) (wallet.getBalance(e.currencyId) / e.price);
                    units = (int) Math.min(units, Math.min(byBalance, Integer.MAX_VALUE));
                    if (units <= 0) {
                        tell(player, Component.translatable("qshop.msg.not_enough_currency"));
                        return;
                    }
                }
                ItemStack result = e.item.copy();
                result.setCount(e.item.getCount() * units);
                if (!ItemHelper.canFit(player, result)) {
                    tell(player, Component.translatable("qshop.msg.no_space"));
                    return;
                }
                double cost = e.price * units;
                if (cost > 0) {
                    CurrencyService.INSTANCE.withdraw(player, e.currencyId, cost,
                            CurrencyService.SOURCE_TRADE, null);
                }
                ItemHelper.give(player, result);
            }
            case SELL -> {
                int byStock = ItemHelper.countItems(player, e.item) / e.item.getCount();
                units = Math.min(units, byStock);
                if (units <= 0) {
                    tell(player, Component.translatable("qshop.msg.not_enough_items"));
                    return;
                }
                int need = e.item.getCount() * units;
                if (!ItemHelper.removeItems(player, e.item, need)) {
                    return;
                }
                double cost = e.price * units;
                if (cost > 0) {
                    CurrencyService.INSTANCE.deposit(player, e.currencyId, cost,
                            CurrencyService.SOURCE_TRADE, null);
                }
            }
            case COMMAND -> {
                // 物品代价(玩家提供物品+商店提供指令:只收物品,不收货币;见"简化交易项目")
                if (!e.item.isEmpty()) {
                    int byStock = ItemHelper.countItems(player, e.item) / e.item.getCount();
                    units = Math.min(units, byStock);
                    if (units <= 0) {
                        tell(player, Component.translatable("qshop.msg.not_enough_items"));
                        return;
                    }
                    int need = e.item.getCount() * units;
                    if (!ItemHelper.removeItems(player, e.item, need)) {
                        return;
                    }
                } else if (e.price > 0) {
                    long byBalance = (long) (wallet.getBalance(e.currencyId) / e.price);
                    units = (int) Math.min(units, Math.min(byBalance, Integer.MAX_VALUE));
                    if (units <= 0) {
                        tell(player, Component.translatable("qshop.msg.not_enough_currency"));
                        return;
                    }
                    double cost = e.price * units;
                    if (cost > 0) {
                        CurrencyService.INSTANCE.withdraw(player, e.currencyId, cost,
                                CurrencyService.SOURCE_TRADE, null);
                    }
                }
            }
            case BARTER -> {
                // 以物换物:纯物品交换,不读取/收取售价
                for (ItemStack g : e.give) {
                    int byGive = ItemHelper.countItems(player, g) / g.getCount();
                    units = Math.min(units, byGive);
                    if (units <= 0) {
                        tell(player, Component.translatable("qshop.msg.not_enough_items"));
                        return;
                    }
                }
                List<ItemStack> receiveStacks = ItemHelper.scaled(e.receive, units);
                if (!ItemHelper.canFitAll(player, receiveStacks)) {
                    tell(player, Component.translatable("qshop.msg.no_space"));
                    return;
                }
                for (ItemStack g : e.give) {
                    ItemHelper.removeItems(player, g, g.getCount() * units);
                }
                ItemHelper.giveAll(player, receiveStacks);
            }
        }

        boolean partial = units < requestedUnits;
        int finalUnits = units;

        // ---- 记录限购 ----
        if (track) {
            if (e.globalLimit > 0) {
                data.globalCounts.addCount(key, finalUnits, period);
                data.setDirty();
            }
            if (e.playerLimit > 0) {
                wallet.addLimitCount(key, finalUnits, period);
            }
        }

        // ---- 购买指令 ----
        // COMMAND entries represent one command reward per purchased unit. Other
        // entry types keep their existing post-trade behavior and run once with
        // aggregate placeholders.
        int commandRuns = e.type == ShopEntryType.COMMAND ? finalUnits : 1;
        int commandUnits = e.type == ShopEntryType.COMMAND ? 1 : finalUnits;
        int commandItems = e.type == ShopEntryType.COMMAND ? itemsPerUnit : itemsPerUnit * finalUnits;
        String commandPrice = CurrencyRegistry.format(e.type == ShopEntryType.COMMAND
                ? e.price : e.price * finalUnits);
        for (int run = 0; run < commandRuns; run++) {
            for (ShopCommand sc : e.commands) {
                if (sc.command == null || sc.command.isEmpty()) {
                    continue;
                }
                String cmd = sc.command
                        .replace("%player%", player.getGameProfile().getName())
                        .replace("%player_uuid%", player.getUUID().toString())
                        .replace("%shop%", shop.id)
                        .replace("%shop_uuid%", shop.uuid.toString())
                        .replace("%entry%", String.valueOf(entryIndex))
                        .replace("%units%", String.valueOf(commandUnits))
                        .replace("%items%", String.valueOf(commandItems))
                        .replace("%price%", commandPrice)
                        .replace("%currency%", e.currencyId)
                        .replace("%multiplier%", String.valueOf(commandUnits));
                try {
                    CommandSourceStack source = new CommandSourceStack(
                            player, player.position(), player.getRotationVector(), null,
                            sc.op ? 4 : 0,
                            player.getGameProfile().getName(), player.getDisplayName(),
                            player.getServer(), player);
                    if (sc.silent) {
                        source = source.withSuppressedOutput();
                    }
                    player.getServer().getCommands().performPrefixedCommand(source, cmd);
                } catch (Exception ex) {
                    LOGGER.warn("QShop: 购买指令执行失败: {}", cmd, ex);
                }
            }
        }

        // ---- 购买后事件(只在 KubeJS 安装时触发) ----
        QShopTradeEvents.postAfter(player, shop, tabIndex, entryIndex, e, finalUnits,
                itemsPerUnit * finalUnits, partial);

        // ---- 反馈消息 ----
        int tradedItems = itemsPerUnit * finalUnits;
        String itemName = e.displayNameOrItem();
        String priceStr = CurrencyRegistry.format(e.price * finalUnits);
        String curName = CurrencyRegistry.displayName(e.currencyId);
        switch (e.type) {
            case BUY -> tell(player, Component.translatable("qshop.msg.bought", itemName, tradedItems, priceStr, curName));
            case COMMAND -> {
                if (!e.item.isEmpty()) {
                    // 物品代价指令:反馈消耗的物品
                    tell(player, Component.translatable("qshop.msg.consumed",
                            itemName, e.item.getCount() * finalUnits));
                } else {
                    tell(player, Component.translatable("qshop.msg.bought", itemName, tradedItems, priceStr, curName));
                }
            }
            case SELL -> tell(player, Component.translatable("qshop.msg.sold", itemName, tradedItems, priceStr, curName));
            case BARTER -> tell(player, Component.translatable("qshop.msg.bartered", itemName, tradedItems));
        }
        if (partial) {
            tell(player, Component.translatable("qshop.msg.partial", finalUnits));
        }

        // ---- 刷新界面与钱包 ----
        QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
        ShopManager.openShop(player, shop);
    }

    /**
     * Executes a server-side BUY or SELL transaction against an addon-provided
     * Forge item handler. This path deliberately does not send chat messages,
     * reopen the shop screen, or execute command entries.
     */
    public static TradeResult tradeHandler(ServerPlayer player, IItemHandler inventory,
                                            String shopRef, Object tabRef, Object entryRef,
                                            int requestedUnits, ShopEntryType expectedType,
                                            ResourceLocation source, @Nullable BlockPos sourcePos) {
        if (player == null || inventory == null || shopRef == null || expectedType == null
                || requestedUnits < 1) {
            return TradeResult.failure(TradeResult.Status.INVALID_ARGUMENT, requestedUnits,
                    "Invalid addon trade arguments");
        }

        Shop shop = ShopManager.get(shopRef);
        if (shop == null) {
            return TradeResult.failure(TradeResult.Status.SHOP_NOT_FOUND, requestedUnits,
                    "Shop not found");
        }
        int tabIndex = resolveTabIndex(shop, tabRef);
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return TradeResult.failure(TradeResult.Status.TAB_NOT_FOUND, requestedUnits,
                    "Tab not found");
        }
        ShopTab tab = shop.tabs.get(tabIndex);
        int entryIndex = resolveEntryIndex(tab, entryRef);
        if (entryIndex < 0 || entryIndex >= tab.entries.size()) {
            return TradeResult.failure(TradeResult.Status.ENTRY_NOT_FOUND, requestedUnits,
                    "Entry not found");
        }
        ShopEntry e = tab.entries.get(entryIndex);
        if (e.type != expectedType || e.item.isEmpty() || !e.commands.isEmpty()
                || !Double.isFinite(e.price) || e.price < 0
                || (e.price > 0 && (e.currencyId == null || e.currencyId.isBlank()))) {
            return TradeResult.failure(TradeResult.Status.UNSUPPORTED_ENTRY, requestedUnits,
                    "Addon handler trades require a command-free BUY or SELL entry");
        }
        if (!RequirementCheck.satisfied(player, e)) {
            return TradeResult.failure(TradeResult.Status.REQUIREMENTS_NOT_MET, requestedUnits,
                    "Entry requirements are not satisfied");
        }

        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                    "Player wallet is unavailable");
        }

        String key = e.uuid != null && !e.uuid.isEmpty()
                ? shop.id + "|" + e.uuid
                : shop.id + "|" + tabIndex + "|" + entryIndex;
        String period = e.reset.periodKey();
        QShopSavedData data = null;
        int usedGlobal = 0;
        int usedPlayer = 0;
        boolean track = e.globalLimit > 0 || e.playerLimit > 0;
        if (e.globalLimit > 0) {
            data = QShopSavedData.get(player.getServer());
            usedGlobal = data.globalCounts.getCount(key, period);
        }
        if (e.playerLimit > 0) {
            usedPlayer = wallet.getLimitCount(key, period);
        }

        int itemsPerUnit = e.item.getCount();
        if (itemsPerUnit <= 0) {
            return TradeResult.failure(TradeResult.Status.UNSUPPORTED_ENTRY, requestedUnits,
                    "Entry item count is invalid");
        }
        int units = requestedUnits;
        if (e.globalLimit > 0) {
            units = Math.min(units, Math.max(0, e.globalLimit - usedGlobal));
        }
        if (e.playerLimit > 0) {
            units = Math.min(units, Math.max(0, e.playerLimit - usedPlayer));
        }
        if (units <= 0) {
            return TradeResult.failure(TradeResult.Status.LIMIT_REACHED, requestedUnits,
                    "Trade limit reached");
        }

        if (!QShopTradeEvents.postBefore(player, shop, tabIndex, entryIndex, e, requestedUnits)) {
            return TradeResult.failure(TradeResult.Status.CANCELLED, requestedUnits,
                    "Trade cancelled by an event handler");
        }

        int totalItems;
        double totalPrice;
        if (expectedType == ShopEntryType.SELL) {
            int byStock = countHandlerItems(inventory, e.item) / itemsPerUnit;
            units = Math.min(units, byStock);
            if (units <= 0) {
                return TradeResult.failure(TradeResult.Status.NOT_ENOUGH_ITEMS, requestedUnits,
                        "Addon inventory does not contain enough items");
            }
            long totalItemsLong = (long) itemsPerUnit * units;
            if (totalItemsLong > Integer.MAX_VALUE) {
                return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                        "Requested item count is too large");
            }
            totalItems = (int) totalItemsLong;
            if (!extractHandlerItems(inventory, e.item, totalItems)) {
                return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                        "Addon inventory refused item extraction");
            }
            totalPrice = e.price * units;
            if (!Double.isFinite(totalPrice)) {
                return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                        "Trade price is too large");
            }
            if (totalPrice > 0) {
                CurrencyService.INSTANCE.deposit(player, e.currencyId, totalPrice,
                        source, sourcePos);
            }
        } else {
            long byBalance = e.price > 0
                    ? (long) (wallet.getBalance(e.currencyId) / e.price)
                    : Integer.MAX_VALUE;
            units = (int) Math.min(units, Math.min(byBalance, Integer.MAX_VALUE));
            if (units <= 0) {
                return TradeResult.failure(TradeResult.Status.NOT_ENOUGH_CURRENCY, requestedUnits,
                        "Player wallet does not contain enough currency");
            }
            long requestedItems = (long) itemsPerUnit * units;
            if (requestedItems > Integer.MAX_VALUE) {
                return TradeResult.failure(TradeResult.Status.NO_SPACE, requestedUnits,
                        "Requested item count is too large");
            }
            long capacity = handlerCapacity(inventory, e.item);
            units = Math.min(units, (int) Math.min(Integer.MAX_VALUE, capacity / itemsPerUnit));
            if (units <= 0) {
                return TradeResult.failure(TradeResult.Status.NO_SPACE, requestedUnits,
                        "Addon inventory has no space");
            }
            totalItems = itemsPerUnit * units;
            totalPrice = e.price * units;
            if (!Double.isFinite(totalPrice)) {
                return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                        "Trade price is too large");
            }
            double oldBalance = wallet.getBalance(e.currencyId);
            if (totalPrice > 0 && !CurrencyService.INSTANCE.withdraw(player, e.currencyId,
                    totalPrice, source, sourcePos)) {
                return TradeResult.failure(TradeResult.Status.NOT_ENOUGH_CURRENCY, requestedUnits,
                        "Player wallet does not contain enough currency");
            }
            ItemStack output = e.item.copy();
            output.setCount(totalItems);
            int inserted = insertHandlerItems(inventory, output);
            if (inserted != totalItems) {
                if (inserted > 0) {
                    extractHandlerItems(inventory, e.item, inserted);
                }
                if (totalPrice > 0) {
                    CurrencyService.INSTANCE.set(player, e.currencyId, oldBalance,
                            source, sourcePos, false);
                }
                return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                        "Addon inventory refused item insertion");
            }
        }

        int tradedItems = itemsPerUnit * units;
        if (track) {
            if (e.globalLimit > 0) {
                data.globalCounts.addCount(key, units, period);
                data.setDirty();
            }
            if (e.playerLimit > 0) {
                wallet.addLimitCount(key, units, period);
            }
        }
        QShopTradeEvents.postAfter(player, shop, tabIndex, entryIndex, e, units,
                tradedItems, units < requestedUnits);
        return TradeResult.success(requestedUnits, units, totalItems, totalPrice);
    }

    /**
     * Executes a server-side BARTER transaction using separate Forge handlers
     * for the items supplied by the addon and the items received from QShop.
     */
    public static TradeResult barterHandler(ServerPlayer player, IItemHandler input,
                                             IItemHandler output, String shopRef,
                                             Object tabRef, Object entryRef, int requestedUnits,
                                             ResourceLocation source, @Nullable BlockPos sourcePos) {
        if (player == null || input == null || output == null || shopRef == null
                || requestedUnits < 1) {
            return TradeResult.failure(TradeResult.Status.INVALID_ARGUMENT, requestedUnits,
                    "Invalid addon barter arguments");
        }

        Shop shop = ShopManager.get(shopRef);
        if (shop == null) {
            return TradeResult.failure(TradeResult.Status.SHOP_NOT_FOUND, requestedUnits,
                    "Shop not found");
        }
        int tabIndex = resolveTabIndex(shop, tabRef);
        if (tabIndex < 0 || tabIndex >= shop.tabs.size()) {
            return TradeResult.failure(TradeResult.Status.TAB_NOT_FOUND, requestedUnits,
                    "Tab not found");
        }
        ShopTab tab = shop.tabs.get(tabIndex);
        int entryIndex = resolveEntryIndex(tab, entryRef);
        if (entryIndex < 0 || entryIndex >= tab.entries.size()) {
            return TradeResult.failure(TradeResult.Status.ENTRY_NOT_FOUND, requestedUnits,
                    "Entry not found");
        }
        ShopEntry e = tab.entries.get(entryIndex);
        if (e.type != ShopEntryType.BARTER || !validBarterStacks(e.give)
                || !validBarterStacks(e.receive)
                || !e.commands.isEmpty() || !Double.isFinite(e.price) || e.price < 0
                || (e.price > 0 && (e.currencyId == null || e.currencyId.isBlank()))) {
            return TradeResult.failure(TradeResult.Status.UNSUPPORTED_ENTRY, requestedUnits,
                    "Addon barter trades require a command-free barter entry");
        }
        if (!RequirementCheck.satisfied(player, e)) {
            return TradeResult.failure(TradeResult.Status.REQUIREMENTS_NOT_MET, requestedUnits,
                    "Entry requirements are not satisfied");
        }

        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                    "Player wallet is unavailable");
        }

        String key = e.uuid != null && !e.uuid.isEmpty()
                ? shop.id + "|" + e.uuid
                : shop.id + "|" + tabIndex + "|" + entryIndex;
        String period = e.reset.periodKey();
        QShopSavedData data = null;
        int usedGlobal = 0;
        int usedPlayer = 0;
        boolean track = e.globalLimit > 0 || e.playerLimit > 0;
        if (e.globalLimit > 0) {
            data = QShopSavedData.get(player.getServer());
            usedGlobal = data.globalCounts.getCount(key, period);
        }
        if (e.playerLimit > 0) {
            usedPlayer = wallet.getLimitCount(key, period);
        }

        int itemsPerUnit = totalCount(e.receive);
        if (itemsPerUnit <= 0) {
            return TradeResult.failure(TradeResult.Status.UNSUPPORTED_ENTRY, requestedUnits,
                    "Barter receive list is empty");
        }
        int units = requestedUnits;
        if (e.globalLimit > 0) {
            units = Math.min(units, Math.max(0, e.globalLimit - usedGlobal));
        }
        if (e.playerLimit > 0) {
            units = Math.min(units, Math.max(0, e.playerLimit - usedPlayer));
        }
        if (units <= 0) {
            return TradeResult.failure(TradeResult.Status.LIMIT_REACHED, requestedUnits,
                    "Trade limit reached");
        }

        if (!QShopTradeEvents.postBefore(player, shop, tabIndex, entryIndex, e, requestedUnits)) {
            return TradeResult.failure(TradeResult.Status.CANCELLED, requestedUnits,
                    "Trade cancelled by an event handler");
        }

        for (ItemStack give : e.give) {
            units = Math.min(units, countHandlerItems(input, give) / give.getCount());
        }
        for (ItemStack receive : e.receive) {
            units = Math.min(units, (int) Math.min(Integer.MAX_VALUE,
                    handlerCapacity(output, receive) / receive.getCount()));
        }
        if (units <= 0) {
            return TradeResult.failure(TradeResult.Status.NOT_ENOUGH_ITEMS, requestedUnits,
                    "Addon barter inventory lacks supplied items or output space");
        }

        double totalPrice = e.price * units;
        if (!Double.isFinite(totalPrice)) {
            return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                    "Trade price is too large");
        }
        double oldBalance = wallet.getBalance(e.currencyId);
        if (totalPrice > 0 && !CurrencyService.INSTANCE.withdraw(player, e.currencyId,
                totalPrice, source, sourcePos)) {
            return TradeResult.failure(TradeResult.Status.NOT_ENOUGH_CURRENCY, requestedUnits,
                    "Player wallet does not contain enough currency");
        }

        List<ItemStack> removed = new java.util.ArrayList<>();
        boolean removedAll = true;
        for (ItemStack give : e.give) {
            int amount = give.getCount() * units;
            if (!extractHandlerItems(input, give, amount)) {
                removedAll = false;
                break;
            }
            ItemStack rollback = give.copy();
            rollback.setCount(amount);
            removed.add(rollback);
        }
        if (!removedAll) {
            for (ItemStack stack : removed) insertHandlerItems(input, stack);
            if (totalPrice > 0) {
                CurrencyService.INSTANCE.set(player, e.currencyId, oldBalance,
                        source, sourcePos, false);
            }
            return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                    "Addon input inventory refused extraction");
        }

        boolean insertedAll = true;
        List<ItemStack> inserted = new java.util.ArrayList<>();
        for (ItemStack receive : e.receive) {
            int amount = receive.getCount() * units;
            ItemStack result = receive.copy();
            result.setCount(amount);
            int insertedCount = insertHandlerItems(output, result);
            if (insertedCount != amount) {
                insertedAll = false;
                if (insertedCount > 0) {
                    ItemStack partial = receive.copy();
                    partial.setCount(insertedCount);
                    inserted.add(partial);
                }
                break;
            }
            inserted.add(result);
        }
        if (!insertedAll) {
            for (ItemStack stack : inserted) {
                extractHandlerItems(output, stack, stack.getCount());
            }
            for (ItemStack stack : removed) insertHandlerItems(input, stack);
            if (totalPrice > 0) {
                CurrencyService.INSTANCE.set(player, e.currencyId, oldBalance,
                        source, sourcePos, false);
            }
            return TradeResult.failure(TradeResult.Status.FAILED, requestedUnits,
                    "Addon output inventory refused insertion");
        }

        int tradedItems = itemsPerUnit * units;
        if (track) {
            if (e.globalLimit > 0) {
                data.globalCounts.addCount(key, units, period);
                data.setDirty();
            }
            if (e.playerLimit > 0) {
                wallet.addLimitCount(key, units, period);
            }
        }
        QShopTradeEvents.postAfter(player, shop, tabIndex, entryIndex, e, units,
                tradedItems, units < requestedUnits);
        return TradeResult.success(requestedUnits, units, tradedItems, totalPrice);
    }

    private static int countHandlerItems(IItemHandler inventory, ItemStack target) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                count = Math.min(Integer.MAX_VALUE, count + stack.getCount());
            }
        }
        return count;
    }

    private static int resolveTabIndex(Shop shop, Object ref) {
        if (ref instanceof Number n) {
            return n.intValue();
        }
        if (ref == null) {
            return -1;
        }
        String value = String.valueOf(ref);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // UUID reference below.
        }
        for (int i = 0; i < shop.tabs.size(); i++) {
            if (value.equals(shop.tabs.get(i).uuid)) {
                return i;
            }
        }
        return -1;
    }

    private static int resolveEntryIndex(ShopTab tab, Object ref) {
        if (ref instanceof Number n) {
            return n.intValue();
        }
        if (ref == null) {
            return -1;
        }
        String value = String.valueOf(ref);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // UUID reference below.
        }
        for (int i = 0; i < tab.entries.size(); i++) {
            if (value.equals(tab.entries.get(i).uuid)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean extractHandlerItems(IItemHandler inventory, ItemStack target, int amount) {
        if (amount < 0 || countHandlerItems(inventory, target) < amount) {
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameComponents(stack, target)) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(slot, Math.min(remaining, stack.getCount()), false);
            if (ItemStack.isSameItemSameComponents(extracted, target)) {
                remaining -= extracted.getCount();
            }
        }
        if (remaining > 0) {
            ItemStack rollback = target.copy();
            rollback.setCount(amount - remaining);
            if (!rollback.isEmpty()) {
                insertHandlerItems(inventory, rollback);
            }
            return false;
        }
        return true;
    }

    private static long handlerCapacity(IItemHandler inventory, ItemStack target) {
        long capacity = 0;
        int stackLimit = target.getMaxStackSize();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, target)) {
                continue;
            }
            if (!inventory.isItemValid(slot, target)) {
                continue;
            }
            int limit = Math.min(stackLimit, Math.max(0, inventory.getSlotLimit(slot)));
            capacity += existing.isEmpty() ? limit : Math.max(0, limit - existing.getCount());
        }
        return capacity;
    }

    private static int insertHandlerItems(IItemHandler inventory, ItemStack stack) {
        int inserted = 0;
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            int before = remaining.getCount();
            remaining = inventory.insertItem(slot, remaining, false);
            inserted += before - remaining.getCount();
        }
        return inserted;
    }

    private static int totalCount(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack s : stacks) {
            total += s.getCount();
        }
        return total;
    }

    private static boolean validBarterStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return false;
        }
        long total = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                return false;
            }
            total += stack.getCount();
            if (total > Integer.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }
}
