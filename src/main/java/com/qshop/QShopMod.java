package com.qshop;

import com.qshop.client.QShopClientConfig;
import com.qshop.config.QShopServerConfig;
import com.qshop.ftb.QShopFtb;
import com.qshop.net.QShopNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QShop - Forge 1.20.1 服务器商店模组。
 */
@Mod(QShopMod.MODID)
public class QShopMod {

    public static final String MODID = "qshop";
    public static final Logger LOGGER = LogManager.getLogger("QShop");

    public QShopMod() {
        QShopNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, QShopClientConfig.SPEC, "qshop-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, QShopServerConfig.SPEC, "qshop-server.toml");
        // FTB Quests 可选集成:未安装时静默跳过(内部有 NoClassDefFoundError 保护)
        QShopFtb.register();
    }
}
