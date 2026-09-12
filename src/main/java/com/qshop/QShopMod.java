package com.qshop;

import com.qshop.config.QShopCommonConfig;
import com.qshop.ftb.QShopFtb;
import com.qshop.net.QShopNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QShop - Forge 1.20.1 服务器商店模组。
 */
@Mod(QShopMod.MODID)
public class QShopMod {

    public static final String MODID = "qshop";
    public static final Logger LOGGER = LogManager.getLogger("QShop");
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> TRADE_SUCCESS_SOUND = SOUND_EVENTS.register(
            "trade_success", () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(MODID, "trade_success")));

    public QShopMod() {
        SOUND_EVENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        QShopNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QShopCommonConfig.SPEC, "qshop-common.toml");
        // FTB Quests 可选集成:未安装时静默跳过(内部有 NoClassDefFoundError 保护)
        QShopFtb.register();
    }
}
