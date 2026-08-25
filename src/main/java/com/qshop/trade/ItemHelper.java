package com.qshop.trade;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品数量辅助方法。
 */
public final class ItemHelper {

    private ItemHelper() {
    }

    /** 统计玩家背包(含副手)中匹配物品的件数 */
    public static int countItems(Player player, ItemStack target) {
        if (target.isEmpty()) {
            return 0;
        }
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(s, target)) {
                count += s.getCount();
            }
        }
        return count;
    }

    /** 从背包扣除指定数量的匹配物品(不足时返回 false 且不扣除) */
    public static boolean removeItems(Player player, ItemStack target, int amount) {
        if (countItems(player, target) < amount) {
            return false;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!ItemStack.isSameItemSameTags(s, target)) {
                continue;
            }
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
            if (s.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return true;
    }

    /** 背包是否能容纳该物品(整组) */
    public static boolean canFit(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int needed = stack.getCount();
        int max = stack.getMaxStackSize();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                needed -= max;
            } else if (ItemStack.isSameItemSameTags(s, stack)) {
                needed -= max - s.getCount();
            }
            if (needed <= 0) {
                return true;
            }
        }
        return false;
    }

    /** 背包是否能同时容纳多组物品(先合并同类再逐一检查) */
    public static boolean canFitAll(Player player, List<ItemStack> stacks) {
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            String key = s.getItem().getDescriptionId() + "|" + (s.getTag() == null ? "" : s.getTag());
            ItemStack m = merged.get(key);
            if (m == null) {
                merged.put(key, s.copy());
            } else {
                m.grow(s.getCount());
            }
        }
        for (ItemStack s : merged.values()) {
            if (!canFit(player, s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 给予物品,放不下则丢在地上。
     *
     * <p>一个交易单位可能被放大成很多物品,不能把超过物品自身堆叠上限的
     * ItemStack 直接交给 Inventory.add,否则某些物品会被写成非法的超大堆叠。
     * 这里按每个 ItemStack 的实际堆叠上限拆分后再放入背包。</p>
     */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        int remaining = stack.getCount();
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStackSize);
            ItemStack copy = stack.copy();
            copy.setCount(amount);
            boolean added = player.getInventory().add(copy);
            if (!added || !copy.isEmpty()) {
                player.drop(copy, false);
            }
            remaining -= amount;
        }
    }

    public static void giveAll(Player player, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            give(player, s);
        }
    }

    public static List<ItemStack> scaled(List<ItemStack> stacks, int units) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            ItemStack c = s.copy();
            c.setCount(s.getCount() * units);
            out.add(c);
        }
        return out;
    }
}
