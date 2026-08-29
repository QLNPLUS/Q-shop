package com.qshop.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/** Event fired after a player's effective currency balance changes. */
public class CurrencyChangedEvent implements KubeEvent {

    private final ServerPlayer player;
    private final String currency;
    private final double oldValue;
    private final double newValue;
    @Nullable
    private final ResourceLocation source;
    @Nullable
    private final BlockPos sourcePos;

    public CurrencyChangedEvent() {
        this(null, "", 0, 0, null, null);
    }

    public CurrencyChangedEvent(ServerPlayer player, String currency, double oldValue, double newValue) {
        this(player, currency, oldValue, newValue, null, null);
    }

    public CurrencyChangedEvent(ServerPlayer player, String currency, double oldValue, double newValue,
                                @Nullable ResourceLocation source, @Nullable BlockPos sourcePos) {
        this.player = player;
        this.currency = currency == null ? "" : currency;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.source = source;
        this.sourcePos = sourcePos;
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

    public double getDelta() {
        return newValue - oldValue;
    }

    @Nullable
    public ResourceLocation getSource() {
        return source;
    }

    @Nullable
    public BlockPos getSourcePos() {
        return sourcePos;
    }
}
