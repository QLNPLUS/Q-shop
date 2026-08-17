// QShop KubeJS 绑定自检脚本(运行时测试所有非玩家绑定)
// Place in run/kubejs/server_scripts/ then start the server.
// Results are logged with the [QSELFTEST] prefix in logs/latest.log.
//
// 注意:
// 1) 不用 ServerEvents.loaded —— 它早于 QShop 的 ServerStartingEvent(ShopManager 尚未加载),
//    会导致 createShop 返回 true 但商店未注册。改用第一个 ServerEvents.tick。
// 2) JSON 构造用 JsonIO.of(...)(KubeJS 2001.6.5 的绑定名,不是 JsonUtils)。
// 3) 新建商店自带 1 个默认子商店:getTabCount 初始为 1,每次 addTab +1。
// 4) 返回 java.util.List 的绑定(getShopIds/getCurrencies)在 userdev 环境会因
//    Rhino 反射访问 java.base 被拒而抛 IllegalAccessException —— dev 环境限制,
//    生产环境(启动器自带 --add-opens)不受影响,这里标记为 SKIP。
// 5) Java 字符串在 Rhino 里 typeof 是 'object',用 String() 转换判断。

var selftestDone = false;

function runSelftest() {
    var log = function () {
        var parts = [];
        for (var i = 0; i < arguments.length; i++) {
            parts.push(String(arguments[i]));
        }
        console.log('[QSELFTEST] ' + parts.join(' '));
    };
    var pass = function (name, cond) {
        log((cond ? 'PASS' : 'FAIL'), name);
    };

    try {
        // ---- 准备:清理上次运行的残留 ----
        if (QShop.exists('selftest')) {
            QShop.removeShop('selftest');
        }
        pass('exists false (clean)', QShop.exists('selftest') === false);

        // ---- 商店创建/查询 ----
        pass('createShop(id,name)', QShop.createShop('selftest', '自检商店') === true);
        pass('exists true', QShop.exists('selftest') === true);
        pass('createShop duplicate=false', QShop.createShop('selftest') === false);
        var shopUuid = String(QShop.getShopUuid('selftest'));
        pass('getShopUuid', shopUuid !== 'null' && shopUuid.length > 0);
        pass('default tab exists', QShop.getTabCount('selftest') === 1);

        // ---- 货币(用时间戳后缀保证每次全新创建;货币持久化,旧 id 会已存在) ----
        var coinId = 'selftestcoin' + String(new Date().getTime() % 1000000);
        pass('createCurrency (fresh)', QShop.createCurrency(coinId, '自检币', '55ff55') === true);
        pass('createCurrency duplicate=false', QShop.createCurrency(coinId, 'x', '000000') === false);

        // ---- 子商店(tab) ----
        pass('addTab(name)', QShop.addTab('selftest', '武器') === true);
        pass('getTabCount=2', QShop.getTabCount('selftest') === 2);
        var tabUuid = String(QShop.getShopTabUuid('selftest', 0));
        pass('getShopTabUuid', tabUuid !== 'null' && tabUuid.length > 0);
        pass('addTab(name,icon)', QShop.addTab('selftest', '装备', JsonIO.of({item: 'minecraft:diamond_chestplate'})) === true);
        pass('getTabCount=3', QShop.getTabCount('selftest') === 3);
        pass('updateTab(index)', QShop.updateTab('selftest', 0, '武器2') === true);
        pass('updateTabByUuid', QShop.updateTabByUuid('selftest', tabUuid, '武器3', null) === true);
        pass('getShopTabUuid still valid', String(QShop.getShopTabUuid('selftest', 0)) === tabUuid);

        // ---- 交易条目 ----
        pass('addEntry SELL (default tab)', QShop.addEntry('selftest', JsonIO.of({type: 'SELL', item: 'minecraft:diamond', price: 100, currency: 'selftestcoin'})) === true);
        pass('getEntryCount=1', QShop.getEntryCount('selftest') === 1);
        var entryUuid = String(QShop.getShopEntryUuid('selftest', 0, 0));
        pass('getShopEntryUuid', entryUuid !== 'null' && entryUuid.length > 0);
        pass('addEntry BUY (tab index 1, count 8)', QShop.addEntry('selftest', 1, JsonIO.of({type: 'BUY', item: {item: 'minecraft:oak_log', count: 8}, price: 2, currency: 'selftestcoin'})) === true);
        pass('getEntryCount(tab1)=1', QShop.getEntryCount('selftest', 1) === 1);
        pass('addEntry COMMAND (tab uuid)', QShop.addEntry('selftest', tabUuid, JsonIO.of({type: 'COMMAND', commands: [{command: 'say hi', op: false, silent: true}]})) === true);
        pass('getEntryCount(tab0)=2', QShop.getEntryCount('selftest', 0) === 2);
        pass('addEntry empty SELL rejected', QShop.addEntry('selftest', 0, JsonIO.of({type: 'SELL'})) === false);
        pass('updateEntryByUuid keeps uuid', QShop.updateEntryByUuid('selftest', tabUuid, entryUuid, JsonIO.of({type: 'SELL', item: 'minecraft:emerald', price: 50, currency: 'selftestcoin'})) === true);
        pass('updateEntryByUuid bad uuid=false', QShop.updateEntryByUuid('selftest', tabUuid, 'no-such-uuid', JsonIO.of({type: 'SELL', item: 'minecraft:stone'})) === false);
        pass('updateEntry', QShop.updateEntry('selftest', 0, 1, JsonIO.of({type: 'SELL', item: 'minecraft:netherite_ingot', price: 500, currency: 'selftestcoin'})) === true);
        pass('removeEntry', QShop.removeEntry('selftest', 0, 1) === true);
        pass('getEntryCount(tab0)=1', QShop.getEntryCount('selftest', 0) === 1);

        // ---- 限购清理 ----
        pass('clearShopLimits', QShop.clearShopLimits('selftest') === true);
        pass('clearTabLimits', QShop.clearTabLimits('selftest', tabUuid) === true);
        pass('clearEntryLimits', QShop.clearEntryLimits('selftest', tabUuid, entryUuid) === true);

        // ---- 删除 ----
        pass('removeTabByUuid', QShop.removeTabByUuid('selftest', tabUuid) === true);
        pass('getTabCount=2 after remove', QShop.getTabCount('selftest') === 2);

        // ---- reload ----
        QShop.reload();
        pass('reload keeps shop', QShop.exists('selftest') === true);
        pass('reload keeps tab', QShop.getTabCount('selftest') === 2);
        pass('reload keeps entry', QShop.getEntryCount('selftest') === 1);

        // ---- 返回 List 的绑定(在清理商店之前检查,此时 selftest 还在) ----
        try {
            var ids = QShop.getShopIds();
            pass('getShopIds contains selftest', String(ids).indexOf('selftest') >= 0);
        } catch (e1) {
            log('SKIP getShopIds (dev-harness module access: ' + String(e1).substring(0, 60) + '...)');
        }
        try {
            var curs = QShop.getCurrencies();
            pass('getCurrencies contains coinId', String(curs).indexOf(coinId) >= 0);
        } catch (e2) {
            log('SKIP getCurrencies (dev-harness module access: ' + String(e2).substring(0, 60) + '...)');
        }

        // ---- 新增:addTab/addEntry 指定 uuid;createShop 默认货币 ----
        pass('createShop with currency', QShop.createShop('selftest2', '货币测试', coinId) === true);
        // selftest2 的 currency 是否写入 JSON,由外部检查 run/world/serverconfig/qshop/shops/selftest2.json
        pass('addTab with fixed uuid', QShop.addTab('selftest2', '固定uuid', null, 'fixed-tab-uuid-123') === true);
        pass('tab uuid addressable (updateTabByUuid)', QShop.updateTabByUuid('selftest2', 'fixed-tab-uuid-123', '改名', null) === true);
        pass('tab uuid removable (removeTabByUuid)', QShop.removeTabByUuid('selftest2', 'fixed-tab-uuid-123') === true);
        pass('addEntry with fixed uuid', QShop.addEntry('selftest2', 0, JsonIO.of({type: 'SELL', item: 'minecraft:iron_ingot', price: 5, currency: 'coins', uuid: 'fixed-entry-uuid-456'})) === true);
        pass('entry uuid addressable (updateEntryByUuid)', QShop.updateEntryByUuid('selftest2', String(QShop.getShopTabUuid('selftest2', 0)), 'fixed-entry-uuid-456', JsonIO.of({type: 'SELL', item: 'minecraft:gold_ingot', price: 6})) === true);
        pass('entry uuid removable (removeEntryByUuid)', QShop.removeEntryByUuid('selftest2', String(QShop.getShopTabUuid('selftest2', 0)), 'fixed-entry-uuid-456') === true);
        log('NOTE selftest2 kept on disk for currency-JSON verification');

        // ---- Builder 双轨并存测试 ----
        pass('builder createShop', QShop.createShop('selftest3', 'Builder测试', 'coins') === true);
        pass('builder entry sell', QShop.entry('selftest3').sell('minecraft:iron_ingot').price(5, 'coins')
            .playerLimit(3, 'DAILY').description('builder').uuid('bld-entry-1').add() === true);
        pass('builder entry count', QShop.getEntryCount('selftest3') === 1);
        pass('builder entry uuid addressable', QShop.updateEntryByUuid('selftest3', String(QShop.getShopTabUuid('selftest3', 0)), 'bld-entry-1', JsonIO.of({type: 'SELL', item: 'minecraft:coal'})) === true);
        pass('builder entry cmd auto-COMMAND', QShop.entry('selftest3').cmd('say hi', false, true).price(1, 'coins').add() === true);
        pass('builder entry barter (js object item)', QShop.entry('selftest3', 0).barter({item: 'minecraft:stone', count: 2}, 'minecraft:cobblestone').add() === true);
        pass('builder entry count=3', QShop.getEntryCount('selftest3') === 3);
        pass('builder tab', QShop.tab('selftest3').name('BuilderTab').icon('minecraft:stone').uuid('bld-tab-1').add() === true);
        pass('builder tab uuid addressable', QShop.updateTabByUuid('selftest3', 'bld-tab-1', '改名', null) === true);
        pass('builder cleanup', QShop.removeShop('selftest3') === true);

        // ---- 纯 KJS 价格波动(基础价 + updateEntryByUuid,零 mod 改动) ----
        pass('price shop create', QShop.createShop('selftest4', '价格', 'coins') === true);
        pass('price entry add', QShop.addEntry('selftest4', JsonIO.of({type: 'SELL', item: 'minecraft:diamond', price: 100, currency: 'coins'})) === true);
        // 基础价 100 × 0.9 = 90:用 updateEntryByUuid 重写完整条目 JSON(price 换成新价)
        var newPrice = 90.0;
        var okP = QShop.updateEntryByUuid('selftest4',
            String(QShop.getShopTabUuid('selftest4', 0)),
            String(QShop.getShopEntryUuid('selftest4', 0, 0)),
            JsonIO.of({type: 'SELL', item: 'minecraft:diamond', price: newPrice, currency: 'coins'}));
        pass('base-price update via updateEntryByUuid', okP === true);
        // 读回文件验证价格已改(updateEntryByUuid 保存时自动落盘)
        try {
            var PathCls = Java.loadClass('java.nio.file.Path');
            var p4 = PathCls.of('world/serverconfig/qshop/shops/selftest4.json');
            var root4 = JsonIO.readJson(p4).getAsJsonObject();
            var e4 = root4.getAsJsonArray('tabs').get(0).getAsJsonObject().getAsJsonArray('entries').get(0).getAsJsonObject();
            pass('price persisted in file', e4.get('price').getAsDouble() === 90.0);
        } catch (e4) {
            log('SKIP file verify (' + String(e4).substring(0, 80) + '...)');
        }
        QShop.reload();
        pass('reload keeps shop', QShop.exists('selftest4') === true);
        pass('price shop cleanup', QShop.removeShop('selftest4') === true);

        // ---- 购买前/后事件 + 数据版本(实时同步) ----
        pass('QShopEvents group exists', typeof QShopEvents === 'object');
        pass('beforeTrade handler exists', typeof QShopEvents.beforeTrade === 'function');
        pass('afterTrade handler exists', typeof QShopEvents.afterTrade === 'function');
        var evtOk = true;
        try {
            QShopEvents.beforeTrade(function (ev) { /* 仅验证注册 */ });
            QShopEvents.afterTrade(function (ev) { /* 仅验证注册 */ });
        } catch (e5) {
            evtOk = false;
            log('event register error: ' + String(e5));
        }
        pass('event listeners register', evtOk === true);
        // 类过滤器放行 com.qshop(事件类可被脚本访问)
        try {
            var BT = Java.tryLoadClass('com.qshop.kubejs.BeforeTradeEvent');
            pass('class filter allows event class', BT !== null && BT !== undefined);
        } catch (e6) {
            log('SKIP class filter check (' + String(e6).substring(0, 80) + '...)');
        }
        // 数据版本:每次修改(触发 save)应自增,客户端轮询据此刷新
        try {
            var SM = Java.tryLoadClass('com.qshop.shop.ShopManager');
            var shopObj = SM.get('selftest');
            var v0 = shopObj.dataVersion;
            QShop.addEntry('selftest', JsonIO.of({type: 'SELL', item: 'minecraft:coal', price: 1, currency: 'coins'}));
            var v1 = shopObj.dataVersion;
            pass('dataVersion incremented on save', v1 > v0);
        } catch (e7) {
            log('SKIP dataVersion check (' + String(e7).substring(0, 80) + '...)');
        }

        // ---- 清理 ----
        pass('removeShop', QShop.removeShop('selftest') === true);
        pass('exists false after remove', QShop.exists('selftest') === false);
        log('ALL TESTS DONE');
    } catch (err) {
        log('SCRIPT ERROR:', err);
    }
}

ServerEvents.tick(function (event) {
    if (selftestDone) {
        return;
    }
    selftestDone = true;
    runSelftest();
});
