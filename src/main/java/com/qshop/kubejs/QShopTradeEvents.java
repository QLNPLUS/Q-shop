package com.qshop.kubejs;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopEntry;
import net.minecraft.server.level.ServerPlayer;

/**
 * 交易事件桥:TradeService 通过这里触发购买前/后事件。
 * <p>本类不引用任何 KubeJS 类型,避免未装 KubeJS 时类加载失败;
 * 钩子由 {@link QShopKubeJSPlugin} 在初始化时注入(仅当 KubeJS 存在)。</p>
 */
public final class QShopTradeEvents {

    /** 购买前钩子:返回 false 表示脚本取消了交易 */
    public interface BeforeHook {
        boolean call(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                     ShopEntry entry, int requestedUnits);
    }

    /** 购买后钩子 */
    public interface AfterHook {
        void call(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                  ShopEntry entry, int tradedUnits, int totalItems, boolean partial);
    }

    public static BeforeHook before = null;
    public static AfterHook after = null;

    /** 购买前事件:返回 true 表示允许交易,false 表示被脚本取消 */
    public static boolean postBefore(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                                     ShopEntry entry, int requestedUnits) {
        BeforeHook hook = before;
        if (hook == null) {
            return true;
        }
        return hook.call(player, shop, tabIndex, entryIndex, entry, requestedUnits);
    }

    public static void postAfter(ServerPlayer player, Shop shop, int tabIndex, int entryIndex,
                                 ShopEntry entry, int tradedUnits, int totalItems, boolean partial) {
        AfterHook hook = after;
        if (hook != null) {
            hook.call(player, shop, tabIndex, entryIndex, entry, tradedUnits, totalItems, partial);
        }
    }

    private QShopTradeEvents() {
    }
}
