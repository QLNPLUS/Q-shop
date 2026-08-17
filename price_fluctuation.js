// price_fluctuation.js — QShop 价格随机波动(纯 KubeJS,零 mod 改动)
// 用法:放进 server_scripts/ 后执行 /kubejs reload server_scripts(或重启服务器)。
//
// 思路:基础价存在 global.price(按物品 id,脚本重载也不丢),到点后
//       新价格 = 基础价 × 随机乘数,再用 updateEntryByUuid 写入对应条目。

// ===== 配置 =====
const FLUCTUATE_INTERVAL = 6000;   // 6000 tick = 5 分钟
const MIN_MULT = 0.8;              // 最低 80%
const MAX_MULT = 1.2;              // 最高 120%

// 需要波动的条目:shopId -> [{tab: 子商店序号, entry: 条目序号, item: 物品id, currency}]
// tab/entry 用序号即可(脚本自动转 uuid);item 用来查 global.price 取基础价
const SHOP_ENTRIES = {
    'vip': [
        { tab: 0, entry: 0, item: 'minecraft:diamond', currency: 'coins' },
        { tab: 0, entry: 1, item: 'minecraft:netherite_ingot', currency: 'coins' },
    ],
};

// ===== 基础价格表(按物品 id)=====
// 想改基础价直接改这里,或游戏里执行 /kubejs reload server_scripts
global.price = global.price || {
    'minecraft:diamond': 100,
    'minecraft:netherite_ingot': 500,
    'minecraft:oak_log': 8,
};

// ===== 波动逻辑 =====
function fluctuatePrice() {
    for (let shopId in SHOP_ENTRIES) {
        if (!QShop.exists(shopId)) continue;
        for (let d of SHOP_ENTRIES[shopId]) {
            let base = global.price[d.item];
            if (!base) continue;
            let mult = MIN_MULT + Math.random() * (MAX_MULT - MIN_MULT);   // 0.8 ~ 1.2
            let price = Math.round(base * mult * 100) / 100;               // 从基础价生成新价
            let tabUuid = String(QShop.getShopTabUuid(shopId, d.tab));
            let entryUuid = String(QShop.getShopEntryUuid(shopId, d.tab, d.entry));
            if (tabUuid === 'null' || entryUuid === 'null') continue;
            let ok = QShop.updateEntryByUuid(shopId, tabUuid, entryUuid, JsonIO.of({
                type: d.type || 'SELL',
                item: d.item,
                price: price,
                currency: d.currency || 'coins',
            }));
            console.log('[QShopPrice] ' + shopId + ' #' + d.entry + ' ' + d.item + ' = ' + price + (ok ? ' OK' : ' FAIL'));
        }
    }
}

ServerEvents.tick(event => {
    if (event.server.tickCount % FLUCTUATE_INTERVAL !== 0) return;
    fluctuatePrice();
});
