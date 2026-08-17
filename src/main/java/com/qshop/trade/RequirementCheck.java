package com.qshop.trade;

import com.qshop.shop.ShopEntry;
import com.qshop.shop.ShopTab;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 交易前提检查(全部基于反射,兼容"未安装对应模组"的情况):
 * <ul>
 *   <li>FTB Quests:玩家所在队伍需已完成指定任务(id 以字符串形式配置)。</li>
 *   <li>GameStages / KubeJS stage:玩家需拥有指定 stage。</li>
 * </ul>
 * 配置了要求却无法找到对应 provider 时按未满足处理，避免受限内容意外显示。
 */
public final class RequirementCheck {

    private static final Logger LOGGER = LogManager.getLogger("QShop");

    private RequirementCheck() {
    }

    /**
     * 返回玩家未满足的要求描述列表(空列表 = 全部满足)。
     * 只在服务端调用。
     */
    public static List<String> missing(ServerPlayer player, ShopEntry e) {
        return missing(player, e.requiredQuests, e.requiredStages);
    }

    /** 子商店(tab)级别的任务/阶段要求 */
    public static List<String> missing(ServerPlayer player, ShopTab tab) {
        return missing(player, tab.requiredQuests, tab.requiredStages);
    }

    /** 按任务 id 列表与阶段列表检查(供条目与子商店共用) */
    public static List<String> missing(ServerPlayer player, List<String> quests, List<String> stages) {
        List<String> missing = new ArrayList<>();
        for (String q : quests) {
            if (!questCompleted(player, q)) {
                missing.add("quest:" + q);
            }
        }
        for (String s : stages) {
            if (!hasStage(player, s)) {
                missing.add("stage:" + s);
            }
        }
        return missing;
    }

    public static boolean satisfied(ServerPlayer player, ShopEntry e) {
        return missing(player, e).isEmpty();
    }

    public static boolean satisfied(ServerPlayer player, ShopTab tab) {
        return missing(player, tab).isEmpty();
    }

    /** 将未满足要求列表翻译为聊天组件(与条目名一起显示) */
    public static Component formatMissing(ServerPlayer player, ShopEntry e) {
        List<String> m = missing(player, e);
        if (m.isEmpty()) {
            return Component.translatable("qshop.msg.requirements_met");
        }
        StringBuilder sb = new StringBuilder();
        for (String s : m) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            if (s.startsWith("quest:")) {
                sb.append(Component.translatable("qshop.msg.req_quest").getString())
                        .append(' ').append(s.substring(6));
            } else if (s.startsWith("stage:")) {
                sb.append(Component.translatable("qshop.msg.req_stage").getString())
                        .append(' ').append(s.substring(6));
            } else {
                sb.append(s);
            }
        }
        return Component.translatable("qshop.msg.requirements_missing", sb.toString());
    }

    // ---------------- FTB Quests ----------------

    private static boolean questCompleted(ServerPlayer player, String questId) {
        Class<?> sqfClass;
        try {
            sqfClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        } catch (ClassNotFoundException absent) {
            return false;
        }
        try {
            Object file = sqfClass.getField("INSTANCE").get(null);
            if (file == null) {
                return false;
            }
            Object quest = findQuest(file, questId);
            if (quest == null) {
                LOGGER.debug("QShop: FTB 任务 {} 不存在", questId);
                return false;
            }
            Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            Object teamData = invokePlayerMethod(teamDataClass, null, "get", player);
            if (teamData == null) {
                teamData = invokePlayerMethod(file.getClass(), file, "getTeamData", player);
            }
            if (teamData == null) {
                teamData = invokePlayerMethod(file.getClass(), file, "getOrCreateTeamData", player);
            }
            if (teamData == null) {
                return false;
            }
            for (Method method : teamData.getClass().getMethods()) {
                if (!method.getName().equals("isCompleted") || method.getParameterCount() < 1
                        || !method.getParameterTypes()[0].isAssignableFrom(quest.getClass())) {
                    continue;
                }
                Object result;
                if (method.getParameterCount() == 1) {
                    result = method.invoke(teamData, quest);
                } else if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == boolean.class) {
                    result = method.invoke(teamData, quest, false);
                } else {
                    continue;
                }
                if (result instanceof Boolean completed) {
                    return completed;
                }
            }
            LOGGER.warn("QShop: 找不到兼容的 FTB Quests isCompleted API");
            return false;
        } catch (Throwable t) {
            LOGGER.debug("QShop: FTB Quests 检查不可用: {}", t.toString());
            return false;
        }
    }

    private static Object findQuest(Object file, String questId) throws Exception {
        String id = questId == null ? "" : questId.trim().replace("_", "");
        Set<Long> candidates = new LinkedHashSet<>();
        try {
            candidates.add(Long.parseLong(id));
        } catch (NumberFormatException ignored) {
        }
        String hex = id.startsWith("0x") || id.startsWith("0X") ? id.substring(2) : id;
        try {
            candidates.add(Long.parseUnsignedLong(hex, 16));
        } catch (NumberFormatException ignored) {
        }
        Method getQuest = file.getClass().getMethod("getQuest", long.class);
        for (long candidate : candidates) {
            Object quest = getQuest.invoke(file, candidate);
            if (quest != null) {
                return quest;
            }
        }
        return null;
    }

    private static Object invokePlayerMethod(Class<?> owner, Object receiver, String name,
                                             ServerPlayer player) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(player.getClass())
                    && (receiver != null || Modifier.isStatic(method.getModifiers()))) {
                try {
                    return method.invoke(receiver, player);
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Another compatible API entry point may still be available below.
                }
            }
        }
        return null;
    }

    // ---------------- GameStages / KubeJS stages ----------------

    /**
     * 检查玩家是否拥有阶段。
     * <p>服务端的 GameStages / KubeJS PlayerStages 两份数据可能不同步(原 sdmshop 就存在此问题,
     * last_one_core 通过服务端改动后推送镜像包修复)。这里在两个 provider 上做"并集"判断:
     * 任一 provider 报告拥有即视为满足,避免因某一侧数据滞后把已获得阶段的玩家误判为未满足。
     * 两个阶段模组都没安装时返回 false，配置过的阶段要求必须能被明确验证。
     */
    private static boolean hasStage(ServerPlayer player, String stage) {
        boolean providerPresent = false;
        boolean found = false;
        // 1) GameStages 模组
        try {
            Class<?> helper = Class.forName("net.darkhax.gamestages.GameStageHelper");
            providerPresent = true;
            for (Method method : helper.getMethods()) {
                if (method.getName().equals("hasStage") && method.getParameterCount() == 2
                        && method.getParameterTypes()[0].isAssignableFrom(player.getClass())
                        && method.getParameterTypes()[1] == String.class) {
                    found |= (Boolean) method.invoke(null, player, stage);
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            LOGGER.debug("QShop: GameStages 检查失败: {}", t.toString());
        }
        // 2) KubeJS PlayerStages
        try {
            Class<?> playerKjs = Class.forName("dev.latvian.mods.kubejs.core.PlayerKJS");
            providerPresent = true;
            Object stages = null;
            for (String methodName : List.of("kjs$getStages", "getStages")) {
                try {
                    stages = playerKjs.getMethod(methodName).invoke(player);
                    break;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
            if (stages == null) {
                Class<?> stagesClass = Class.forName("dev.latvian.mods.kubejs.stages.Stages");
                for (Method method : stagesClass.getMethods()) {
                    if (method.getName().equals("get") && Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(player.getClass())) {
                        stages = method.invoke(null, player);
                        break;
                    }
                }
            }
            if (stages != null) {
                Method has = stages.getClass().getMethod("has", String.class);
                found |= (Boolean) has.invoke(stages, stage);
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            LOGGER.debug("QShop: stage 检查不可用: {}", t.toString());
        }
        // 无 provider 或所有 provider 均未确认拥有该阶段时，都按未满足处理。
        return providerPresent && found;
    }
}
