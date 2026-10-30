package me.foesio.foOrders.api;

/** A current estimate; balances, permissions, and order slots can change before creation. */
public record OrderQuote(Status status, OrderRequest effectiveRequest, int currentOrders, int maxOrders,
                         double subtotal, double tax, double total) {
    public enum Status {
        ELIGIBLE, PLAYER_OFFLINE, NO_PERMISSION, INVALID_REQUEST, ITEM_UNAVAILABLE, BLACKLISTED, ORDER_LIMIT,
        ECONOMY_UNAVAILABLE, INSUFFICIENT_FUNDS
    }

    public boolean eligible() {
        return status == Status.ELIGIBLE;
    }
}
