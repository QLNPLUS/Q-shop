package com.qshop.data;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服限购计数,保存在世界存档数据中(qshop_data)。
 */
public class QShopSavedData extends SavedData {

    public final PurchaseCounts globalCounts = new PurchaseCounts();
    private static final Codec<QShopSavedData> CODEC = CompoundTag.CODEC.xmap(
            QShopSavedData::load,
            QShopSavedData::saveTag);
    private static final SavedDataType<QShopSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("qshop", "qshop_data"),
            QShopSavedData::new,
            CODEC);

    public static QShopSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(TYPE);
    }

    public static QShopSavedData load(CompoundTag tag) {
        QShopSavedData data = new QShopSavedData();
        if (tag.contains("global")) {
            data.globalCounts.deserialize(tag.getCompoundOrEmpty("global"));
        }
        return data;
    }

    private CompoundTag saveTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("global", globalCounts.serialize());
        return tag;
    }

    /** Legacy helper retained for addons that used the old SavedData callback. */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.merge(saveTag());
        return tag;
    }
}
