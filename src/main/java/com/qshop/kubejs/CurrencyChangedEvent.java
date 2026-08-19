package com.qshop.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/** Event fired after a player's effective currency balance changes. */
public class CurrencyChangedEvent extends EventJS {

    private final ServerPlayer player;
    private final String currency;
    private final double oldValue;
    private final double newValue;

    public CurrencyChangedEvent() {
        this(null, "", 0, 0);
    }

    public CurrencyChangedEvent(ServerPlayer player, String currency, double oldValue, double newValue) {
        this.player = player;
        this.currency = currency == null ? "" : currency;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getCurrency() {
        return currency;
    }

    public double getOldValue() {
        return oldValue;
    }

    public double getNewValue() {
        return newValue;
    }
}
