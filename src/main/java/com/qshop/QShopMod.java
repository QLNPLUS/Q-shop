package com.qshop;

import com.qshop.config.QShopCommonConfig;
import com.qshop.ftb.QShopFtb;
import com.qshop.net.QShopNetwork;
import com.qshop.wallet.WalletCapability;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * QShop - Forge 1.20.1 服务器商店模组。
 */
@Mod(QShopMod.MODID)
public class QShopMod {

    public static final String MODID = "qshop";
    public static final Logger LOGGER = LogManager.getLogger("QShop");
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final Supplier<SoundEvent> TRADE_SUCCESS_SOUND = SOUND_EVENTS.register(
            "trade_success", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "trade_success")));

    public QShopMod(IEventBus modEventBus, ModContainer modContainer) {
        WalletCapability.ATTACHMENTS.register(modEventBus);
        QShopNetwork.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, QShopCommonConfig.SPEC, "qshop-common.toml");
        // FTB Quests 可选集成:未安装时静默跳过(内部有 NoClassDefFoundError 保护)
        QShopFtb.register();
    }
}
