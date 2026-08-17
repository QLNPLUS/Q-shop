package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import com.qshop.shop.ShopTab;

/**
 * KubeJS 子商店(tab)builder(链式,与现有 addTab(参数) 双轨并存)。
 *
 * <pre>
 * QShop.tab('vip').name('武器').icon('minecraft:iron_sword').uuid('my-tab')
 *     .stage('vip').add();
 * </pre>
 *
 * <p>图标参数支持:ItemStack / 物品 id 字符串 / 物品 JSON / JS 对象(经 KubeJS JsonIO 转换)。
 * uuid 缺省时随机生成。</p>
 */
public class TabBuilder {

    private final String shopId;
    private final ShopTab tab = new ShopTab();

    TabBuilder(String shopId) {
        this.shopId = shopId;
    }

    public TabBuilder name(String name) {
        tab.name = name == null ? "" : name;
        return this;
    }

    public TabBuilder icon(Object icon) {
        tab.icon = QShopBindings.parseItem(icon);
        return this;
    }

    /** 子商店描述(悬停在 tab 上时以 tooltip 显示;多行用 \n 分隔) */
    public TabBuilder description(String description) {
        tab.description = description == null ? "" : description;
        return this;
    }

    /** 指定子商店 uuid(空/省略则随机生成),便于后续按 uuid 稳定引用 */
    public TabBuilder uuid(String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            tab.uuid = uuid.trim();
        }
        return this;
    }

    public TabBuilder quest(String questId) {
        if (questId != null && !questId.isBlank()) {
            tab.requiredQuests.add(questId.trim());
        }
        return this;
    }

    public TabBuilder stage(String stage) {
        if (stage != null && !stage.isBlank()) {
            tab.requiredStages.add(stage.trim());
        }
        return this;
    }

    /** 添加到商店并保存,返回是否成功 */
    public boolean add() {
        Shop shop = ShopManager.get(shopId);
        if (shop == null) {
            return false;
        }
        shop.ensureTabs();
        tab.ensureUuid();
        for (ShopTab existing : shop.tabs) {
            if (tab.uuid.equals(existing.uuid)) {
                existing.name = tab.name;
                existing.icon = tab.icon;
                existing.description = tab.description;
                existing.requiredQuests.clear();
                existing.requiredQuests.addAll(tab.requiredQuests);
                existing.requiredStages.clear();
                existing.requiredStages.addAll(tab.requiredStages);
                ShopManager.save(shop);
                return true;
            }
        }
        shop.tabs.add(tab);
        ShopManager.save(shop);
        return true;
    }
}
