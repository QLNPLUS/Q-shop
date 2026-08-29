package com.qshop.ftb;

import com.qshop.QShopMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * FTB Quests 集成入口(完全可选,运行时未安装 FTB Quests 时静默跳过)。
 * <p>参考 SDMShop 的 MoneyTask / MoneyReward 实现,为 QShop 提供两个自定义类型:
 * <ul>
 *   <li><b>任务 qshop:money</b> —— 攒够指定货币余额:进度 = 从钱包扣入任务的货币量(消耗资源)。</li>
 *   <li><b>奖励 qshop:money</b> —— 领取时向玩家钱包发放指定货币(+ 可选随机加成)。</li>
 * </ul>
 * 货币可在 FTB Quests 的配置界面里填写货币 id,留空使用 QShop 默认(第一种)货币。
 * <p>注册时机:mod 构造阶段(FTB Quests 的 quest 文件在世界加载时才会解析,远晚于构造)。
 * 注册代码内的 FTB 类引用全部位于被 try 保护的代码路径中,FTB 缺失时只抛
 * NoClassDefFoundError 并跳过,不会影响 QShop 本体。
 */
public final class QShopFtb {

    private QShopFtb() {
    }

    /** FTB Quests 是否已加载(mod 构造阶段 ModList 已可用) */
    public static boolean available() {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded("ftbquests");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 注册 qshop:money 任务/奖励类型;必须在 FTB Quests 加载 quest 文件之前调用 */
    public static void register() {
        if (!available()) {
            return;
        }
        try {
            registerTypes();
            QShopMod.LOGGER.info("QShop: 已注册 FTB Quests 货币任务/奖励类型 (qshop:money)");
        } catch (LinkageError e) {
            QShopMod.LOGGER.info("QShop: FTB Quests 不可用,跳过任务/奖励注册");
        } catch (Throwable t) {
            QShopMod.LOGGER.warn("QShop: FTB Quests 注册失败: {}", t.toString());
        }
    }

    private static void registerTypes() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("qshop", "money");
        QShopMoneyTask.TYPE = dev.ftb.mods.ftbquests.quest.task.TaskTypes.register(id,
                QShopMoneyTask::new,
                () -> dev.ftb.mods.ftblibrary.icon.Icon.getIcon("qshop:textures/gui/ftb_money.png"))
                .setDisplayName(net.minecraft.network.chat.Component.translatable("qshop.ftb.task_money"));
        QShopMoneyReward.TYPE = dev.ftb.mods.ftbquests.quest.reward.RewardTypes.register(id,
                QShopMoneyReward::new,
                () -> dev.ftb.mods.ftblibrary.icon.Icon.getIcon("qshop:textures/gui/ftb_money.png"))
                .setDisplayName(net.minecraft.network.chat.Component.translatable("qshop.ftb.reward_money"));
    }

    /** 配置的货币 id(空 = QShop 默认货币) */
    public static String resolveCurrency(String currency) {
        if (currency != null && !currency.isEmpty()) {
            return currency;
        }
        String first = com.qshop.currency.CurrencyRegistry.firstId();
        return first == null || first.isEmpty() ? "coins" : first;
    }

    /** 大数缩写(FTB 界面显示用):>=1K 用 K,>=1M 用 M,>=1B 用 B(保留 1 位小数) */
    public static String formatCompact(long v) {
        if (v >= 1_000_000_000L) {
            return trim(v / 1_000_000_000d) + "B";
        }
        if (v >= 1_000_000L) {
            return trim(v / 1_000_000d) + "M";
        }
        if (v >= 1_000L) {
            return trim(v / 1_000d) + "K";
        }
        return String.valueOf(v);
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
            return String.valueOf((long) d);
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
