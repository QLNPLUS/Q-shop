package com.qshop.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.shop.LimitReset;
import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopEntryType;
import com.qshop.shop.ShopJson;
import com.qshop.shop.ShopManager;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /qshop 指令。
 */
public final class QShopCommands {

    private QShopCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qshop")
                // ---------------- 打开商店 ----------------
                .then(Commands.literal("open")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "shop"), null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(s -> s.hasPermission(2))
                                        .executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "shop"),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("list").executes(QShopCommands::list))
                .then(Commands.literal("balance").executes(QShopCommands::balance))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            ShopManager.reload();
                            ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.reloaded", ShopManager.all().size()), true);
                            return 1;
                        }))
                // ---------------- 货币管理 ----------------
                .then(Commands.literal("currency")
                        .then(Commands.literal("list").executes(QShopCommands::currencyList))
                        .then(Commands.literal("create").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ctx -> currencyCreate(ctx, null))
                                                .then(Commands.argument("color", StringArgumentType.string())
                                                        .executes(ctx -> currencyCreate(ctx, StringArgumentType.getString(ctx, "color")))))))
                        .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> currencyChange(ctx, true))))))
                        .then(Commands.literal("take").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> currencyChange(ctx, false))))))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> currencySet(ctx)))))))
                // ---------------- 商店管理 ----------------
                .then(Commands.literal("shop")
                        .then(Commands.literal("list").executes(QShopCommands::list))
                        .then(Commands.literal("create").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> shopCreate(ctx, null, null))
                                        .then(Commands.argument("displayName", StringArgumentType.string())
                                                .executes(ctx -> shopCreate(ctx, StringArgumentType.getString(ctx, "displayName"), null))
                                                .then(Commands.argument("currency", StringArgumentType.word())
                                                        .executes(ctx -> shopCreate(ctx,
                                                                StringArgumentType.getString(ctx, "displayName"),
                                                                StringArgumentType.getString(ctx, "currency"))))))))
                // ---------------- 游戏内编辑 ----------------
                .then(Commands.literal("edit").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .then(Commands.literal("add")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(ctx -> addEntry(ctx, -1))
                                                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> addEntry(ctx, DoubleArgumentType.getDouble(ctx, "price")))
                                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                                .executes(ctx -> addEntry(ctx, DoubleArgumentType.getDouble(ctx, "price")))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> removeEntry(ctx))))
                                .then(Commands.literal("setitem")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setItem(ctx))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("field", StringArgumentType.word())
                                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                                .executes(ctx -> setField(ctx))))))))
                // ---------------- 工具 ----------------
                .then(Commands.literal("item")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.translatable("qshop.cmd.player_required"));
                                return 0;
                            }
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("qshop.cmd.need_item"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.item_printed", ShopJson.stackToBase64(held)), false);
                            return 1;
                        }))
        );
    }

    // ---------------- open / list / balance ----------------

    private static int open(CommandContext<CommandSourceStack> ctx, String shopId, ServerPlayer target) {
        Shop shop = ShopManager.byIdOrUuid(shopId);
        if (shop == null) {
            ctx.getSource().sendFailure(Component.translatable("qshop.msg.shop_missing", shopId));
            return 0;
        }
        ServerPlayer player = target != null ? target
                : (ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null);
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("qshop.cmd.player_required"));
            return 0;
        }
        ShopManager.openShop(player, shop);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.shop_list"), false);
        for (Shop shop : ShopManager.all()) {
            final Shop s = shop;
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + s.id + " [" + s.uuid + "] "
                    + s.displayNameOrId() + " (" + Component.translatable("qshop.cmd.entry_count").getString() + ": " + s.entries.size() + ")"), false);
        }
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("qshop.cmd.player_required"));
            return 0;
        }
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.balance_self"), false);
        for (Currency c : CurrencyRegistry.all()) {
            Component line = Component.literal(" - " + c.displayName + ": " + CurrencyRegistry.format(wallet.getBalance(c.id)))
                    .withStyle(style -> style.withColor(c.color));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    // ---------------- 货币 ----------------

    private static int currencyChange(CommandContext<CommandSourceStack> ctx, boolean give) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String currency = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return 0;
        }
        if (give) {
            wallet.add(currency, amount);
            ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.currency_give",
                    player.getGameProfile().getName(), CurrencyRegistry.format(amount), CurrencyRegistry.displayName(currency)), true);
        } else {
            wallet.take(currency, amount);
            ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.currency_take",
                    player.getGameProfile().getName(), CurrencyRegistry.format(amount), CurrencyRegistry.displayName(currency)), true);
        }
        QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
        return 1;
    }

    private static int currencySet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String currency = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return 0;
        }
        wallet.setBalance(currency, amount);
        ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.currency_set",
                player.getGameProfile().getName(), CurrencyRegistry.displayName(currency), CurrencyRegistry.format(amount)), true);
        QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
        return 1;
    }

    private static int currencyList(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.currency_list"), false);
        for (Currency c : CurrencyRegistry.all()) {
            final Currency cc = c;
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + cc.id + " = " + cc.displayName)
                    .withStyle(style -> style.withColor(cc.color)), false);
        }
        return 1;
    }

    private static int currencyCreate(CommandContext<CommandSourceStack> ctx, String color) {
        String id = StringArgumentType.getString(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");
        if (CurrencyRegistry.create(id, name, color)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.currency_created", id, name), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("qshop.cmd.currency_exists", id));
        return 0;
    }

    // ---------------- 商店管理 ----------------

    private static int shopCreate(CommandContext<CommandSourceStack> ctx, String displayName, String currency) {
        String id = StringArgumentType.getString(ctx, "id");
        if (ShopManager.get(id) != null) {
            ctx.getSource().sendFailure(Component.translatable("qshop.cmd.shop_exists", id));
            return 0;
        }
        Shop shop = new Shop();
        shop.id = id;
        shop.displayName = displayName == null ? "" : displayName;
        // 默认货币:参数为空时默认金币(coins)
        shop.currency = (currency == null || currency.isEmpty()) ? "coins" : currency;
        shop.uuid = java.util.UUID.randomUUID();
        ShopManager.save(shop);
        ctx.getSource().sendSuccess(() -> Component.translatable("qshop.cmd.shop_created", id), true);
        return 1;
    }

    // ---------------- 编辑 ----------------

    private static int addEntry(CommandContext<CommandSourceStack> ctx, double priceArg) {
        CommandSourceStack src = ctx.getSource();
        Shop shop = ShopManager.byIdOrUuid(StringArgumentType.getString(ctx, "shop"));
        if (shop == null) {
            src.sendFailure(Component.translatable("qshop.msg.shop_missing", StringArgumentType.getString(ctx, "shop")));
            return 0;
        }
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.translatable("qshop.cmd.player_required"));
            return 0;
        }
        String typeName = StringArgumentType.getString(ctx, "type").toUpperCase();
        double price = priceArg >= 0 ? priceArg : 1.0;
        String currency = getArgOr(ctx, "currency", CurrencyRegistry.firstId());

        ShopEntryType type;
        try {
            type = ShopEntryType.valueOf(typeName);
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.translatable("qshop.cmd.bad_type"));
            return 0;
        }
        if (!ShopManager.addEntryFromHeld(player, shop, type, price, currency)) {
            src.sendFailure(Component.translatable("qshop.cmd.need_item"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("qshop.cmd.entry_added", shop.entries.size() - 1), true);
        return 1;
    }

    private static int removeEntry(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Shop shop = ShopManager.byIdOrUuid(StringArgumentType.getString(ctx, "shop"));
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (shop == null || index < 0 || index >= shop.entries.size()) {
            return 0;
        }
        shop.entries.remove(index);
        ShopManager.save(shop);
        src.sendSuccess(() -> Component.translatable("qshop.cmd.entry_removed", index), true);
        return 1;
    }

    private static int setItem(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Shop shop = ShopManager.byIdOrUuid(StringArgumentType.getString(ctx, "shop"));
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (shop == null || index < 0 || index >= shop.entries.size()) {
            return 0;
        }
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.translatable("qshop.cmd.player_required"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.translatable("qshop.cmd.need_item"));
            return 0;
        }
        ShopEntry e = shop.entries.get(index);
        if (e.type == ShopEntryType.BARTER) {
            if (!e.receive.isEmpty()) {
                e.receive.set(0, held.copy());
            } else {
                e.receive.add(held.copy());
            }
            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty()) {
                if (!e.give.isEmpty()) {
                    e.give.set(0, off.copy());
                } else {
                    e.give.add(off.copy());
                }
            }
        } else {
            e.item = held.copy();
        }
        ShopManager.save(shop);
        src.sendSuccess(() -> Component.translatable("qshop.cmd.entry_updated", index, "item"), true);
        return 1;
    }

    private static int setField(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Shop shop = ShopManager.byIdOrUuid(StringArgumentType.getString(ctx, "shop"));
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (shop == null || index < 0 || index >= shop.entries.size()) {
            return 0;
        }
        String field = StringArgumentType.getString(ctx, "field").toLowerCase();
        String value = StringArgumentType.getString(ctx, "value");
        ShopEntry e = shop.entries.get(index);
        try {
            switch (field) {
                case "price" -> e.price = Math.max(0, Double.parseDouble(value));
                case "currency" -> e.currencyId = value;
                case "globallimit" -> e.globalLimit = Math.max(-1, Integer.parseInt(value));
                case "playerlimit" -> e.playerLimit = Math.max(-1, Integer.parseInt(value));
                case "reset" -> e.reset = LimitReset.fromName(value);
                default -> {
                    src.sendFailure(Component.literal("未知字段: " + field + "(可选 price/currency/globallimit/playerlimit/reset)"));
                    return 0;
                }
            }
        } catch (NumberFormatException ex) {
            src.sendFailure(Component.literal("数值解析失败: " + value));
            return 0;
        }
        ShopManager.save(shop);
        src.sendSuccess(() -> Component.translatable("qshop.cmd.entry_updated", index, field), true);
        return 1;
    }

    private static String getArgOr(CommandContext<CommandSourceStack> ctx, String name, String def) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
