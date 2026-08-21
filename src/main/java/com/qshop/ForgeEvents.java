package com.qshop;

import com.qshop.api.CurrencyService;
import com.qshop.cmd.QShopCommands;
import com.qshop.config.QShopCommonConfig;
import com.qshop.net.QShopNetwork;
import com.qshop.net.SyncWalletPacket;
import com.qshop.shop.ShopManager;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 总线事件。
 */
@Mod.EventBusSubscriber(modid = QShopMod.MODID)
public final class ForgeEvents {

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QShopCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(QShopMod.MODID, "wallet"), new WalletCapability.Provider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        original.getCapability(WalletCapability.WALLET).ifPresent(oldWallet ->
                player.getCapability(WalletCapability.WALLET).ifPresent(newWallet -> {
                    newWallet.copyFrom(oldWallet);
                    if (event.isWasDeath() && QShopCommonConfig.loseCurrencyOnDeath()) {
                        for (var entry : oldWallet.snapshot().entrySet()) {
                            double retained = entry.getValue()
                                    * QShopCommonConfig.currencyRetention(entry.getKey());
                            if (player instanceof ServerPlayer sp) {
                                CurrencyService.INSTANCE.set(sp, entry.getKey(), retained,
                                        CurrencyService.SOURCE_DEATH, null);
                            } else {
                                newWallet.setBalance(entry.getKey(), retained);
                            }
                        }
                    }
                }));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            IWallet wallet = WalletCapability.get(sp);
            if (wallet != null) {
                QShopNetwork.sendToPlayer(sp, new SyncWalletPacket(wallet.snapshot()));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            IWallet wallet = WalletCapability.get(sp);
            if (wallet != null) {
                QShopNetwork.sendToPlayer(sp, new SyncWalletPacket(wallet.snapshot()));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ShopManager.load(event.getServer());
    }
}
