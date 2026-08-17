package com.qshop.trade;

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
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            units = Math.min(units, (e.globalLimit - usedGlobal) / itemsPerUnit);
        }
        if (e.playerLimit > 0) {
            units = Math.min(units, (e.playerLimit - usedPlayer) / itemsPerUnit);
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
                    wallet.take(e.currencyId, cost);
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
                    wallet.add(e.currencyId, cost);
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
                        wallet.take(e.currencyId, cost);
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
            int itemsTraded = itemsPerUnit * finalUnits;
            if (e.globalLimit > 0) {
                data.globalCounts.addCount(key, itemsTraded, period);
                data.setDirty();
            }
            if (e.playerLimit > 0) {
                wallet.addLimitCount(key, itemsTraded, period);
            }
        }

        // ---- 购买指令 ----
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
                    .replace("%units%", String.valueOf(finalUnits))
                    .replace("%items%", String.valueOf(itemsPerUnit * finalUnits))
                    .replace("%price%", CurrencyRegistry.format(e.price * finalUnits))
                    .replace("%currency%", e.currencyId)
                    .replace("%multiplier%", String.valueOf(finalUnits));
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

    private static int totalCount(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack s : stacks) {
            total += s.getCount();
        }
        return total;
    }
}
