package com.qshop;

import com.qshop.config.QShopCommonConfig;
import com.qshop.config.QShopServerConfig;
import com.qshop.ftb.QShopFtb;
import com.qshop.net.QShopNetwork;
import com.qshop.wallet.WalletCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QShop - Forge 1.20.1 服务器商店模组。
 */
@Mod(QShopMod.MODID)
public class QShopMod {

    public static final String MODID = "qshop";
    public static final Logger LOGGER = LogManager.getLogger("QShop");

    public QShopMod(IEventBus modEventBus, ModContainer modContainer) {
        WalletCapability.ATTACHMENTS.register(modEventBus);
        QShopNetwork.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, QShopCommonConfig.SPEC, "qshop-common.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, QShopServerConfig.SPEC, "qshop-server.toml");
        // FTB Quests 可选集成:未安装时静默跳过(内部有 NoClassDefFoundError 保护)
        QShopFtb.register();
    }
}
