package com.qshop.api;

/**
 * Result returned by an addon trade attempt.
 *
 * <p>The result is deliberately independent of Minecraft GUI classes so addon
 * blocks can decide how to display failures.</p>
 */
public final class TradeResult {

    public enum Status {
        SUCCESS,
        INVALID_ARGUMENT,
        SHOP_NOT_FOUND,
        TAB_NOT_FOUND,
        ENTRY_NOT_FOUND,
        UNSUPPORTED_ENTRY,
        REQUIREMENTS_NOT_MET,
        CANCELLED,
        LIMIT_REACHED,
        NOT_ENOUGH_CURRENCY,
        NOT_ENOUGH_ITEMS,
        NO_SPACE,
        FAILED
    }

    private final Status status;
    private final int requestedUnits;
    private final int tradedUnits;
    private final int totalItems;
    private final double totalPrice;
    private final String message;

    private TradeResult(Status status, int requestedUnits, int tradedUnits,
                        int totalItems, double totalPrice, String message) {
        this.status = status;
        this.requestedUnits = requestedUnits;
        this.tradedUnits = tradedUnits;
        this.totalItems = totalItems;
        this.totalPrice = totalPrice;
        this.message = message == null ? "" : message;
    }

    public static TradeResult success(int requestedUnits, int tradedUnits,
                                      int totalItems, double totalPrice) {
        return new TradeResult(Status.SUCCESS, requestedUnits, tradedUnits,
                totalItems, totalPrice, "");
    }

    public static TradeResult failure(Status status, int requestedUnits, String message) {
        if (status == null || status == Status.SUCCESS) {
            throw new IllegalArgumentException("A failure result requires a non-success status");
        }
        return new TradeResult(status, requestedUnits, 0, 0, 0, message);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public int getRequestedUnits() {
        return requestedUnits;
    }

    public int getTradedUnits() {
        return tradedUnits;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public String getMessage() {
        return message;
    }
}
