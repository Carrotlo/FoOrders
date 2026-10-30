package me.foesio.foOrders.api;

/** Result of a supported state change. Snapshot is present only on success. */
public record OrderActionResult(Status status, OrderSnapshot order) {
    public enum Status {
        SUCCESS, NO_PERMISSION, INVALID_REQUEST, ITEM_UNAVAILABLE, BLACKLISTED, ORDER_LIMIT,
        ECONOMY_UNAVAILABLE, INSUFFICIENT_FUNDS, ECONOMY_ERROR, CANCELLED_BY_EVENT,
        BUSY, NOT_FOUND, ALREADY_CANCELLED, PLAYER_OFFLINE, INVALID_NPC_NAME
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }
}
