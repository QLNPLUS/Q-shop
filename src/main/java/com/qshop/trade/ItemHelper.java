package com.qshop.trade;

import com.qshop.util.ItemStackData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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

    /**
     * 判断两个物品栈是否是"同一种物品"。
     *
     * <p>{@link ItemStack#isSameItemSameComponents} 比较的是整张组件补丁(prototype + patch),
     * 因此"带一个显式空 custom_data 组件"与"完全没有该组件"会被判成不同物品。这个差异对玩家
     * 不可见:配置里手写/脚本生成的物品通常不带组件,而容器里被其它模组 set 过空 CustomData 的
     * 同一件物品会带一个空组件,结果就是交易/收购判定为"一件都找不到"。
     *
     * <p>所以这里先按原版严格比较,不相等时再对"一方有该组件、另一方没有"的空组件情况放宽;
     * 其它组件(附魔、枪械 GunId 之类)仍然是严格比较。
     */
    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        if (ItemStack.isSameItemSameComponents(a, b)) {
            return true;
        }
        if (!a.is(b.getItem())) {
            return false;
        }
        if (!isEmptyCustomData(a) || !isEmptyCustomData(b)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(withoutEmptyCustomData(a), withoutEmptyCustomData(b));
    }

    /** 空 custom_data 与没有该组件等价(用于合并同类物品) */
    public static String stackKey(ItemStack stack) {
        CompoundTag tag = ItemStackData.getCustomTag(stack);
        return stack.getItem().getDescriptionId() + "|" + (tag == null || tag.isEmpty() ? "" : tag);
    }

    private static boolean isEmptyCustomData(ItemStack stack) {
        CompoundTag tag = ItemStackData.getCustomTag(stack);
        return tag == null || tag.isEmpty();
    }

    private static ItemStack withoutEmptyCustomData(ItemStack stack) {
        if (!isEmptyCustomData(stack) || ItemStackData.getCustomTag(stack) == null) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.CUSTOM_DATA);
        return copy;
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
            if (sameItem(s, target)) {
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
            if (!sameItem(s, target)) {
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
            } else if (sameItem(s, stack)) {
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
            String key = stackKey(s);
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
