package com.qshop.kubejs;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridge for currency balance changes. This class deliberately has no KubeJS
 * types so currency updates remain safe when KubeJS is not installed.
 */
public final class QShopCurrencyEvents {

    public interface Hook {
        void call(ServerPlayer player, String currency, double oldValue, double newValue);
    }

    public static Hook hook;

    /** Posts a change only when the effective balance actually changed. */
    public static void post(ServerPlayer player, String currency, double oldValue, double newValue) {
        if (player == null || currency == null || currency.isEmpty()
                || Double.compare(oldValue, newValue) == 0) {
            return;
        }
        Hook current = hook;
        if (current != null) {
            current.call(player, currency, oldValue, newValue);
        }
    }

    private QShopCurrencyEvents() {
    }
}
