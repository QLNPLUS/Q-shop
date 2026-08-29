package com.qshop.api;

import com.qshop.QShopMod;
import com.qshop.kubejs.QShopCurrencyEvents;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import com.qshop.wallet.WalletImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Official wallet mutation service for QShop addons.
 *
 * <p>Online mutations synchronize the client and publish a Forge event.
 * UUID overloads also support offline players by reading/writing their
 * playerdata NBT; offline mutations publish the UUID-bearing Forge event but
 * cannot send a client packet or a KubeJS player event.</p>
 */
public final class CurrencyService {

    public static final String API_VERSION = "1.1.0";
    public static final CurrencyService INSTANCE = new CurrencyService();
    private static final String FORGE_CAPS_KEY = "ForgeCaps";
    private static final String WALLET_CAPABILITY_KEY = "qshop:wallet";
    private static final String NEOFORGE_ATTACHMENTS_KEY = "neoforge:attachments";
    private static final Object OFFLINE_WALLET_LOCK = new Object();

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

    /** Reads a balance for an online or offline player identified by UUID. */
    public double getBalance(MinecraftServer server, UUID playerUuid, String currencyId) {
        validateServerAndUuid(server, playerUuid);
        if (currencyId == null || currencyId.isBlank()) {
            return 0D;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            return getBalance(online, currencyId);
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            return offline == null ? 0D : offline.wallet().getBalance(currencyId);
        }
    }

    /** Reads a per-player limit counter for an online or offline UUID. */
    public int getLimitCount(MinecraftServer server, UUID playerUuid, String key, String period) {
        validateServerAndUuid(server, playerUuid);
        if (key == null || key.isBlank() || period == null || period.isBlank()) {
            return 0;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            IWallet wallet = WalletCapability.get(online);
            return wallet == null ? 0 : wallet.getLimitCount(key, period);
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            return offline == null ? 0 : offline.wallet().getLimitCount(key, period);
        }
    }

    /** Clears every period of one player's personal limit counter. */
    public boolean clearLimitCount(MinecraftServer server, UUID playerUuid, String key) {
        validateServerAndUuid(server, playerUuid);
        if (key == null || key.isBlank()) {
            return false;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            IWallet wallet = WalletCapability.get(online);
            if (wallet == null) {
                return false;
            }
            wallet.clearLimitCount(key);
            return true;
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            if (offline == null) {
                return false;
            }
            offline.wallet().clearLimitCount(key);
            return saveOffline(offline);
        }
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

    /** Adds currency for an online or offline player identified by UUID. */
    public double deposit(MinecraftServer server, UUID playerUuid, String currencyId, double amount) {
        return deposit(server, playerUuid, currencyId, amount, SOURCE_API, null, true);
    }

    /** Adds currency by UUID with addon source metadata. */
    public double deposit(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                          ResourceLocation source, @Nullable BlockPos sourcePos) {
        return deposit(server, playerUuid, currencyId, amount, source, sourcePos, true);
    }

    /** Adds currency by UUID with explicit event control. */
    public double deposit(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                          ResourceLocation source, @Nullable BlockPos sourcePos,
                          boolean triggerEvent) {
        validateAmount(amount);
        validateServerAndUuid(server, playerUuid);
        if (currencyId == null || currencyId.isBlank()) {
            return 0D;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            return deposit(online, currencyId, amount, source, sourcePos, triggerEvent);
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            if (offline == null) {
                return 0D;
            }
            double oldValue = offline.wallet().getBalance(currencyId);
            offline.wallet().add(currencyId, amount);
            double newValue = offline.wallet().getBalance(currencyId);
            if (!saveOffline(offline)) {
                return oldValue;
            }
            finish(null, playerUuid, offline.wallet(), currencyId, oldValue, newValue,
                    source, sourcePos, triggerEvent);
            return newValue;
        }
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

    /** Removes currency for an online or offline player identified by UUID. */
    public boolean withdraw(MinecraftServer server, UUID playerUuid, String currencyId, double amount) {
        return withdraw(server, playerUuid, currencyId, amount, SOURCE_API, null, true);
    }

    /** Removes currency by UUID with addon source metadata. */
    public boolean withdraw(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                            ResourceLocation source, @Nullable BlockPos sourcePos) {
        return withdraw(server, playerUuid, currencyId, amount, source, sourcePos, true);
    }

    /** Removes currency by UUID with explicit event control. */
    public boolean withdraw(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                            ResourceLocation source, @Nullable BlockPos sourcePos,
                            boolean triggerEvent) {
        validateAmount(amount);
        validateServerAndUuid(server, playerUuid);
        if (currencyId == null || currencyId.isBlank()) {
            return false;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            return withdraw(online, currencyId, amount, source, sourcePos, triggerEvent);
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            if (offline == null) {
                return false;
            }
            double oldValue = offline.wallet().getBalance(currencyId);
            if (!offline.wallet().take(currencyId, amount)) {
                return false;
            }
            double newValue = offline.wallet().getBalance(currencyId);
            if (!saveOffline(offline)) {
                return false;
            }
            finish(null, playerUuid, offline.wallet(), currencyId, oldValue, newValue,
                    source, sourcePos, triggerEvent);
            return true;
        }
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

    /** Sets currency for an online or offline player identified by UUID. */
    public double set(MinecraftServer server, UUID playerUuid, String currencyId, double amount) {
        return set(server, playerUuid, currencyId, amount, SOURCE_API, null, true);
    }

    /** Sets currency by UUID with addon source metadata. */
    public double set(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                      ResourceLocation source, @Nullable BlockPos sourcePos) {
        return set(server, playerUuid, currencyId, amount, source, sourcePos, true);
    }

    /** Sets currency by UUID with explicit event control. */
    public double set(MinecraftServer server, UUID playerUuid, String currencyId, double amount,
                      ResourceLocation source, @Nullable BlockPos sourcePos,
                      boolean triggerEvent) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Currency balance must be finite and non-negative");
        }
        validateServerAndUuid(server, playerUuid);
        if (currencyId == null || currencyId.isBlank()) {
            return 0D;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            return set(online, currencyId, amount, source, sourcePos, triggerEvent);
        }
        synchronized (OFFLINE_WALLET_LOCK) {
            OfflineWallet offline = loadOffline(server, playerUuid);
            if (offline == null) {
                return 0D;
            }
            double oldValue = offline.wallet().getBalance(currencyId);
            offline.wallet().setBalance(currencyId, amount);
            double newValue = offline.wallet().getBalance(currencyId);
            if (!saveOffline(offline)) {
                return oldValue;
            }
            finish(null, playerUuid, offline.wallet(), currencyId, oldValue, newValue,
                    source, sourcePos, triggerEvent);
            return newValue;
        }
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
        finish(player, player.getUUID(), wallet, currencyId, oldValue, newValue,
                source, sourcePos, triggerEvent);
    }

    private static void finish(@Nullable ServerPlayer player, UUID playerUuid, IWallet wallet,
                               String currencyId, double oldValue, double newValue,
                               ResourceLocation source, @Nullable BlockPos sourcePos,
                               boolean triggerEvent) {
        if (player != null) {
            QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
        }
        if (!triggerEvent || Double.compare(oldValue, newValue) == 0) {
            return;
        }
        ResourceLocation actualSource = source == null ? SOURCE_API : source;
        BlockPos actualSourcePos = sourcePos == null ? null : sourcePos.immutable();
        NeoForge.EVENT_BUS.post(new CurrencyChangedEvent(
                player, playerUuid, currencyId, oldValue, newValue, actualSource, actualSourcePos));
        if (player != null) {
            QShopCurrencyEvents.post(player, currencyId, oldValue, newValue, actualSource, actualSourcePos);
        }
    }

    private static void validateServerAndUuid(MinecraftServer server, UUID playerUuid) {
        if (server == null) {
            throw new IllegalArgumentException("MinecraftServer must not be null");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID must not be null");
        }
    }

    @Nullable
    private static OfflineWallet loadOffline(MinecraftServer server, UUID playerUuid) {
        File file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerUuid + ".dat").toFile();
        if (!file.isFile()) {
            return null;
        }
        try {
            CompoundTag playerData = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag forgeAttachments = playerData.getCompound(NEOFORGE_ATTACHMENTS_KEY);
            CompoundTag forgeCaps = playerData.getCompound(FORGE_CAPS_KEY);
            WalletImpl wallet = new WalletImpl();
            CompoundTag walletTag = forgeAttachments.contains(WALLET_CAPABILITY_KEY)
                    ? forgeAttachments.getCompound(WALLET_CAPABILITY_KEY)
                    : forgeCaps.getCompound(WALLET_CAPABILITY_KEY);
            wallet.deserializeNBT(walletTag);
            return new OfflineWallet(file, playerData, forgeAttachments, wallet);
        } catch (IOException | RuntimeException ex) {
            QShopMod.LOGGER.warn("QShop: failed to read offline wallet for {}", playerUuid, ex);
            return null;
        }
    }

    private static boolean saveOffline(OfflineWallet offline) {
        offline.attachments().put(WALLET_CAPABILITY_KEY, offline.wallet().serializeNBT());
        offline.playerData().put(NEOFORGE_ATTACHMENTS_KEY, offline.attachments());
        Path target = offline.file().toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".qshop.tmp");
        try {
            NbtIo.writeCompressed(offline.playerData(), temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            QShopMod.LOGGER.warn("QShop: failed to write offline wallet file {}", target, ex);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupEx) {
                QShopMod.LOGGER.debug("QShop: failed to clean temporary wallet file {}", temp, cleanupEx);
            }
            return false;
        }
    }

    private record OfflineWallet(File file, CompoundTag playerData,
                                 CompoundTag attachments, WalletImpl wallet) {
    }
}
