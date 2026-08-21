package com.qshop.api;

import com.qshop.kubejs.QShopCurrencyEvents;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * Official wallet mutation service for QShop addons.
 *
 * <p>All public mutations synchronize the client and publish a Forge event.
 * The overload with {@code triggerEvent} is intended for QShop's command
 * compatibility flag; addons should use the default overloads.</p>
 */
public final class CurrencyService {

    public static final String API_VERSION = "1.1.0";
    public static final CurrencyService INSTANCE = new CurrencyService();

    public static final ResourceLocation SOURCE_API = ResourceLocation.fromNamespaceAndPath("qshop", "api");
    public static final ResourceLocation SOURCE_TRADE = ResourceLocation.fromNamespaceAndPath("qshop", "trade");
    public static final ResourceLocation SOURCE_COMMAND = ResourceLocation.fromNamespaceAndPath("qshop", "command");
    public static final ResourceLocation SOURCE_FTB_REWARD = ResourceLocation.fromNamespaceAndPath("qshop", "ftb_reward");
    public static final ResourceLocation SOURCE_FTB_TASK = ResourceLocation.fromNamespaceAndPath("qshop", "ftb_task");
    public static final ResourceLocation SOURCE_KUBEJS = ResourceLocation.fromNamespaceAndPath("qshop", "kubejs");
    public static final ResourceLocation SOURCE_DEATH = ResourceLocation.fromNamespaceAndPath("qshop", "death");

    private CurrencyService() {
    }

    public double getBalance(ServerPlayer player, String currencyId) {
        IWallet wallet = wallet(player, currencyId);
        return wallet == null ? 0D : wallet.getBalance(currencyId);
    }

    /** Adds a non-negative amount and returns the resulting balance. */
    public double deposit(ServerPlayer player, String currencyId, double amount) {
        return deposit(player, currencyId, amount, SOURCE_API, null, true);
    }

    /** Adds a positive amount with an addon-provided source and block position. */
    public double deposit(ServerPlayer player, String currencyId, double amount,
                          ResourceLocation source, @Nullable BlockPos sourcePos) {
        return deposit(player, currencyId, amount, source, sourcePos, true);
    }

    /** Internal-compatible overload used by commands with an explicit event flag. */
    public double deposit(ServerPlayer player, String currencyId, double amount,
                          ResourceLocation source, @Nullable BlockPos sourcePos,
                          boolean triggerEvent) {
        validateAmount(amount);
        IWallet wallet = wallet(player, currencyId);
        if (wallet == null) {
            return 0D;
        }
        double oldValue = wallet.getBalance(currencyId);
        wallet.add(currencyId, amount);
        double newValue = wallet.getBalance(currencyId);
        finish(player, wallet, currencyId, oldValue, newValue, source, sourcePos, triggerEvent);
        return newValue;
    }

    /** Removes a non-negative amount, returning false when the wallet is insufficient. */
    public boolean withdraw(ServerPlayer player, String currencyId, double amount) {
        return withdraw(player, currencyId, amount, SOURCE_API, null, true);
    }

    /** Removes a positive amount with an addon-provided source and block position. */
    public boolean withdraw(ServerPlayer player, String currencyId, double amount,
                            ResourceLocation source, @Nullable BlockPos sourcePos) {
        return withdraw(player, currencyId, amount, source, sourcePos, true);
    }

    /** Internal-compatible overload used by commands with an explicit event flag. */
    public boolean withdraw(ServerPlayer player, String currencyId, double amount,
                            ResourceLocation source, @Nullable BlockPos sourcePos,
                            boolean triggerEvent) {
        validateAmount(amount);
        IWallet wallet = wallet(player, currencyId);
        if (wallet == null) {
            return false;
        }
        double oldValue = wallet.getBalance(currencyId);
        if (!wallet.take(currencyId, amount)) {
            return false;
        }
        double newValue = wallet.getBalance(currencyId);
        finish(player, wallet, currencyId, oldValue, newValue, source, sourcePos, triggerEvent);
        return true;
    }

    /** Sets a non-negative balance and returns the resulting balance. */
    public double set(ServerPlayer player, String currencyId, double amount) {
        return set(player, currencyId, amount, SOURCE_API, null, true);
    }

    /** Sets a non-negative balance with an addon-provided source and block position. */
    public double set(ServerPlayer player, String currencyId, double amount,
                      ResourceLocation source, @Nullable BlockPos sourcePos) {
        return set(player, currencyId, amount, source, sourcePos, true);
    }

    /** Internal-compatible overload used by commands with an explicit event flag. */
    public double set(ServerPlayer player, String currencyId, double amount,
                      ResourceLocation source, @Nullable BlockPos sourcePos,
                      boolean triggerEvent) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Currency balance must be finite and non-negative");
        }
        IWallet wallet = wallet(player, currencyId);
        if (wallet == null) {
            return 0D;
        }
        double oldValue = wallet.getBalance(currencyId);
        wallet.setBalance(currencyId, amount);
        double newValue = wallet.getBalance(currencyId);
        finish(player, wallet, currencyId, oldValue, newValue, source, sourcePos, triggerEvent);
        return newValue;
    }

    private static IWallet wallet(ServerPlayer player, String currencyId) {
        if (player == null || currencyId == null || currencyId.isBlank()) {
            return null;
        }
        return WalletCapability.get(player);
    }

    private static void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Currency amount must be finite and non-negative");
        }
    }

    private static void finish(ServerPlayer player, IWallet wallet, String currencyId,
                               double oldValue, double newValue, ResourceLocation source,
                               @Nullable BlockPos sourcePos, boolean triggerEvent) {
        QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
        if (!triggerEvent || Double.compare(oldValue, newValue) == 0) {
            return;
        }
        ResourceLocation actualSource = source == null ? SOURCE_API : source;
        BlockPos actualSourcePos = sourcePos == null ? null : sourcePos.immutable();
        MinecraftForge.EVENT_BUS.post(new CurrencyChangedEvent(
                player, currencyId, oldValue, newValue, actualSource, actualSourcePos));
        QShopCurrencyEvents.post(player, currencyId, oldValue, newValue, actualSource, actualSourcePos);
    }
}
