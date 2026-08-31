package com.qshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Optional FTB Quests client integration. The mod remains usable without FTB Quests. */
public final class FtbQuestClient {

    private static final String CLIENT_FILE_CLASS = "dev.ftb.mods.ftbquests.client.ClientQuestFile";
    private static final String CLIENT_PROXY_CLASS = "dev.ftb.mods.ftbquests.client.FTBQuestsClient";

    private FtbQuestClient() {
    }

    /** Resolves quest titles for tooltips, falling back to the configured id. */
    public static List<String> questNames(List<String> questIds) {
        List<String> names = new ArrayList<>();
        if (questIds == null) {
            return names;
        }
        for (String questId : questIds) {
            if (questId == null || questId.isBlank()) {
                continue;
            }
            names.add(questName(questId));
        }
        return names;
    }

    /** Opens FTB Quests focused on the first configured quest that exists client-side. */
    public static boolean openFirstQuest(List<String> questIds) {
        if (questIds == null) {
            return false;
        }
        for (String questId : questIds) {
            if (questId != null && !questId.isBlank() && openQuest(questId)) {
                return true;
            }
        }
        return false;
    }

    private static String questName(String questId) {
        try {
            QuestLookup lookup = findQuest(questId);
            if (lookup == null) {
                return questId;
            }
            Object title = lookup.quest().getClass().getMethod("getTitle").invoke(lookup.quest());
            if (title instanceof Component component && !component.getString().isBlank()) {
                return component.getString();
            }
        } catch (Throwable ignored) {
            // FTB Quests is optional and its client data may not be loaded yet.
        }
        return questId;
    }

    private static boolean openQuest(String questId) {
        try {
            QuestLookup lookup = findQuest(questId);
            if (lookup == null) {
                return false;
            }
            Class<?> clientFileClass = Class.forName(CLIENT_FILE_CLASS);
            Method open = clientFileClass.getMethod("openBookToQuestObject", long.class);
            // FTB opens its screen through Minecraft#setScreen; close QShop first so
            // the two screens are never left stacked by screen-wrapper mods.
            Minecraft.getInstance().setScreen(null);
            open.invoke(null, lookup.id());
            return true;
        } catch (Throwable ignored) {
            // Missing FTB Quests or an unloaded client file should not break QShop clicks.
            return false;
        }
    }

    private static QuestLookup findQuest(String questId) throws Exception {
        Object file = clientQuestFile();
        if (file == null) {
            return null;
        }
        Method getQuest = file.getClass().getMethod("getQuest", long.class);
        for (long candidate : questIdCandidates(questId)) {
            Object quest = getQuest.invoke(file, candidate);
            if (quest != null) {
                return new QuestLookup(quest, candidate);
            }
        }
        return null;
    }

    private static Object clientQuestFile() throws Exception {
        Class<?> clientFileClass = Class.forName(CLIENT_FILE_CLASS);
        Object file = clientFileClass.getField("INSTANCE").get(null);
        if (file != null) {
            return file;
        }
        Class<?> proxyClass = Class.forName(CLIENT_PROXY_CLASS);
        return proxyClass.getMethod("getClientQuestFile").invoke(null);
    }

    private static Set<Long> questIdCandidates(String questId) {
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
        return candidates;
    }

    private record QuestLookup(Object quest, long id) {
    }
}
