package com.qshop.wallet;

import com.qshop.data.PurchaseCounts;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钱包实现,可序列化为 NBT。
 */
public class WalletImpl implements IWallet, ValueIOSerializable {

    private final Map<String, Double> currencies = new LinkedHashMap<>();
    private final PurchaseCounts limits = new PurchaseCounts();

    @Override
    public double getBalance(String currencyId) {
        return currencies.getOrDefault(currencyId, 0D);
    }

    @Override
    public void setBalance(String currencyId, double amount) {
        if (amount <= 0) {
            currencies.remove(currencyId);
        } else {
            currencies.put(currencyId, amount);
        }
    }

    @Override
    public double add(String currencyId, double amount) {
        double next = getBalance(currencyId) + amount;
        setBalance(currencyId, next);
        return next;
    }

    @Override
    public boolean take(String currencyId, double amount) {
        double balance = getBalance(currencyId);
        if (balance + 1e-9 < amount) {
            return false;
        }
        setBalance(currencyId, balance - amount);
        return true;
    }

    @Override
    public boolean has(String currencyId, double amount) {
        return getBalance(currencyId) + 1e-9 >= amount;
    }

    @Override
    public Map<String, Double> snapshot() {
        return new LinkedHashMap<>(currencies);
    }

    @Override
    public int getLimitCount(String key, String period) {
        return limits.getCount(key, period);
    }

    @Override
    public void addLimitCount(String key, int amount, String period) {
        limits.addCount(key, amount, period);
    }

    @Override
    public void clearLimitCount(String key) {
        limits.remove(key);
    }

    @Override
    public void copyFrom(IWallet other) {
        currencies.clear();
        currencies.putAll(other.snapshot());
        if (other instanceof WalletImpl w) {
            limits.deserialize(w.limits.serialize());
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag cur = new CompoundTag();
        for (Map.Entry<String, Double> kv : currencies.entrySet()) {
            cur.putDouble(kv.getKey(), kv.getValue());
        }
        tag.put("currencies", cur);
        tag.put("limits", limits.serialize());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        currencies.clear();
        if (tag.contains("currencies")) {
            CompoundTag cur = tag.getCompoundOrEmpty("currencies");
            for (String key : cur.keySet()) {
                currencies.put(key, cur.getDoubleOr(key, 0D));
            }
        }
        if (tag.contains("limits")) {
            limits.deserialize(tag.getCompoundOrEmpty("limits"));
        }
    }

    @Override
    public void serialize(ValueOutput output) {
        output.store("currencies", CompoundTag.CODEC, serializeNBT().getCompoundOrEmpty("currencies"));
        output.store("limits", CompoundTag.CODEC, serializeNBT().getCompoundOrEmpty("limits"));
    }

    @Override
    public void deserialize(ValueInput input) {
        CompoundTag tag = new CompoundTag();
        input.read("currencies", CompoundTag.CODEC).ifPresent(value -> tag.put("currencies", value));
        input.read("limits", CompoundTag.CODEC).ifPresent(value -> tag.put("limits", value));
        deserializeNBT(tag);
    }
}
