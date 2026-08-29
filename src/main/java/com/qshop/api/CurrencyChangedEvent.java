package com.qshop.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * Forge event posted after a player's effective currency balance changes.
 * The event is server-side and is not cancellable because the balance has
 * already been committed when it is delivered.
 */
public class CurrencyChangedEvent extends Event {

    @Nullable
    private final ServerPlayer player;
    private final UUID playerUuid;
    private final String currency;
    private final double oldValue;
    private final double newValue;
    private final ResourceLocation source;
    @Nullable
    private final BlockPos sourcePos;

    public CurrencyChangedEvent(ServerPlayer player, String currency,
                                double oldValue, double newValue,
                                ResourceLocation source, @Nullable BlockPos sourcePos) {
        this(player, player == null ? null : player.getUUID(), currency,
                oldValue, newValue, source, sourcePos);
    }

    /** Creates an event for an online player or an offline UUID. */
    public CurrencyChangedEvent(@Nullable ServerPlayer player, UUID playerUuid,
                                String currency, double oldValue, double newValue,
                                ResourceLocation source, @Nullable BlockPos sourcePos) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID must not be null");
        }
        this.player = player;
        this.playerUuid = playerUuid;
        this.currency = currency == null ? "" : currency;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.source = source;
        this.sourcePos = sourcePos;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    /** Returns the affected player UUID, including for offline-player events. */
    public UUID getPlayerUuid() {
        return playerUuid;
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

    public ResourceLocation getSource() {
        return source;
    }

    @Nullable
    public BlockPos getSourcePos() {
        return sourcePos;
    }
}
