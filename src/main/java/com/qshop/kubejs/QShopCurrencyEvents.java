package com.qshop.kubejs;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Bridge for currency balance changes. This class deliberately has no KubeJS
 * types so currency updates remain safe when KubeJS is not installed.
 */
public final class QShopCurrencyEvents {

    public interface Hook {
        void call(ServerPlayer player, String currency, double oldValue, double newValue,
                  ResourceLocation source, @Nullable BlockPos sourcePos);
    }

    public static Hook hook;

    /** Posts a change only when the effective balance actually changed. */
    public static void post(ServerPlayer player, String currency, double oldValue, double newValue) {
        post(player, currency, oldValue, newValue, null, null);
    }

    /** Posts a change with the source metadata used by Java addon events. */
    public static void post(ServerPlayer player, String currency, double oldValue, double newValue,
                            @Nullable ResourceLocation source, @Nullable BlockPos sourcePos) {
        if (player == null || currency == null || currency.isEmpty()
                || Double.compare(oldValue, newValue) == 0) {
            return;
        }
        Hook current = hook;
        if (current != null) {
            current.call(player, currency, oldValue, newValue, source, sourcePos);
        }
    }

    private QShopCurrencyEvents() {
    }
}
