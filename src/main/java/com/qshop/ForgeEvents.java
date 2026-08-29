package com.qshop;

import com.qshop.api.CurrencyService;
import com.qshop.cmd.QShopCommands;
import com.qshop.config.QShopCommonConfig;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.shop.ShopManager;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** NeoForge game-bus events for QShop. */
@EventBusSubscriber(modid = QShopMod.MODID)
public final class ForgeEvents {
    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QShopCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        IWallet oldWallet = WalletCapability.get(event.getOriginal());
        IWallet newWallet = WalletCapability.get(event.getEntity());
        if (oldWallet == null || newWallet == null) {
            return;
        }
        var snapshot = oldWallet.snapshot();
        newWallet.copyFrom(oldWallet);
        if (event.isWasDeath() && QShopCommonConfig.loseCurrencyOnDeath()) {
            for (var entry : snapshot.entrySet()) {
                double retained = entry.getValue() * QShopCommonConfig.currencyRetention(entry.getKey());
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    CurrencyService.INSTANCE.set(player, entry.getKey(), retained,
                            CurrencyService.SOURCE_DEATH, null);
                } else {
                    newWallet.setBalance(entry.getKey(), retained);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            IWallet wallet = WalletCapability.get(player);
            if (wallet != null) {
                QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            IWallet wallet = WalletCapability.get(player);
            if (wallet != null) {
                QShopNetwork.sendToPlayer(player, new SyncWalletPacket(wallet.snapshot()));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ShopManager.load(event.getServer());
    }
}
