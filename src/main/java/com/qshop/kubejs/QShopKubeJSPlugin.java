package com.qshop.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS 插件:注册 QShop 绑定与购买前/后、货币变动事件。
 * 仅在安装了 KubeJS 时由 KubeJS 通过 kubejs.plugins.txt 加载。
 */
public class QShopKubeJSPlugin implements KubeJSPlugin {

    /** 事件组:脚本用 QShopEvents.beforeTrade / QShopEvents.afterTrade */
    public static final EventGroup QSHOP_EVENTS = EventGroup.of("QShopEvents");
    public static final EventHandler BEFORE_TRADE =
            QSHOP_EVENTS.server("beforeTrade", () -> BeforeTradeEvent.class).hasResult();
    public static final EventHandler AFTER_TRADE =
            QSHOP_EVENTS.server("afterTrade", () -> AfterTradeEvent.class);
    public static final EventHandler CURRENCY_CHANGED =
            QSHOP_EVENTS.server("currencyChanged", () -> CurrencyChangedEvent.class);

    @Override
    public void init() {
        // 注入交易钩子:TradeService 触发事件(仅在 KubeJS 存在时生效)
        QShopTradeEvents.before = (player, shop, tabIndex, entryIndex, entry, requestedUnits) -> {
            BeforeTradeEvent event = new BeforeTradeEvent(player, shop, tabIndex, entryIndex, entry, requestedUnits);
            EventResult result = BEFORE_TRADE.post(event);
            return !result.interruptFalse(); // 脚本调用了 event.cancel() → 取消交易
        };
        QShopTradeEvents.after = (player, shop, tabIndex, entryIndex, entry, tradedUnits, totalItems, partial) -> {
            AfterTradeEvent event = new AfterTradeEvent(player, shop, tabIndex, entryIndex, entry,
                    tradedUnits, totalItems, partial);
            AFTER_TRADE.post(event);
        };
        QShopCurrencyEvents.hook = (player, currency, oldValue, newValue, source, sourcePos) ->
                CURRENCY_CHANGED.post(new CurrencyChangedEvent(
                        player, currency, oldValue, newValue, source, sourcePos));
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(QSHOP_EVENTS);
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        // Only the Builder-first facade and event payloads are public to scripts.
        filter.allow("com.qshop.kubejs.QShopApi");
        filter.allow("com.qshop.kubejs.EntryBuilder");
        filter.allow("com.qshop.kubejs.TabBuilder");
        filter.allow("com.qshop.kubejs.BeforeTradeEvent");
        filter.allow("com.qshop.kubejs.AfterTradeEvent");
        filter.allow("com.qshop.kubejs.CurrencyChangedEvent");
        filter.allow("com.qshop.shop");
        filter.allow("com.qshop.currency");
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("QShop", QShopApi.INSTANCE);
    }
}
