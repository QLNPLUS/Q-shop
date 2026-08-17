package com.qshop.data;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * 按周期键统计的购买/出售计数。
 * 周期键变化(如跨天/跨周/跨月)时计数自动清零。
 */
public class PurchaseCounts {

    public static class Entry {
        public int count = 0;
        public String period = "all";
    }

    private final Map<String, Entry> map = new HashMap<>();

    /** 读取计数;若存储的周期与当前周期不同,自动清零并更新 */
    public int getCount(String key, String period) {
        Entry en = map.get(key);
        if (en == null) {
            return 0;
        }
        if (!en.period.equals(period)) {
            en.count = 0;
            en.period = period;
            return 0;
        }
        return en.count;
    }

    public void addCount(String key, int amount, String period) {
        Entry en = map.computeIfAbsent(key, k -> new Entry());
        if (!en.period.equals(period)) {
            en.count = 0;
            en.period = period;
        }
        en.count += amount;
    }

    /** 删除指定键的全部计数(含所有周期);返回是否存在 */
    public boolean remove(String key) {
        return map.remove(key) != null;
    }

    /** 是否包含指定键 */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Entry> kv : map.entrySet()) {
            CompoundTag en = new CompoundTag();
            en.putInt("count", kv.getValue().count);
            en.putString("period", kv.getValue().period);
            tag.put(kv.getKey(), en);
        }
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        map.clear();
        for (String key : tag.getAllKeys()) {
            CompoundTag en = tag.getCompound(key);
            Entry e = new Entry();
            e.count = en.getInt("count");
            e.period = en.getString("period");
            map.put(key, e);
        }
    }
}
