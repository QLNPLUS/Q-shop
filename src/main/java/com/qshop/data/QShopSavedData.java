package com.qshop.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全服限购计数,保存在世界存档数据中(qshop_data)。
 */
public class QShopSavedData extends SavedData {

    public final PurchaseCounts globalCounts = new PurchaseCounts();

    public static QShopSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(QShopSavedData::load, QShopSavedData::new, "qshop_data");
    }

    public static QShopSavedData load(CompoundTag tag) {
        QShopSavedData data = new QShopSavedData();
        if (tag.contains("global")) {
            data.globalCounts.deserialize(tag.getCompound("global"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("global", globalCounts.serialize());
        return tag;
    }
}
