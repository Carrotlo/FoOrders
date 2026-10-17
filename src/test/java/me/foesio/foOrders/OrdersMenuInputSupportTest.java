package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersMenuInputSupportTest {
    @Test
    void invalidAmountOrPriceKeepsTheExistingDraftOnNativeAndChatCallbackPath() {
        NewOrderDraft draft = new NewOrderDraft("custom", "DIAMOND", 37, 25.5D,
            Map.of("minecraft:sharpness", 2));

        assertEquals(draft, OrdersMenuInputSupport.withValidAmount(draft, -1));
        assertEquals(draft, OrdersMenuInputSupport.withValidAmount(draft, 0));
        assertEquals(draft, OrdersMenuInputSupport.withValidPrice(draft, -1D));
        assertEquals(draft, OrdersMenuInputSupport.withValidPrice(draft, 0D));
        assertEquals(draft, OrdersMenuInputSupport.withValidPrice(draft, Double.NaN));
        assertEquals(draft, OrdersMenuInputSupport.withValidPrice(draft, Double.POSITIVE_INFINITY));

        assertEquals(draft.withAmount(42), OrdersMenuInputSupport.withValidAmount(draft, 42));
        assertEquals(draft.withPricePerItem(19.75D), OrdersMenuInputSupport.withValidPrice(draft, 19.75D));
    }

    @Test
    void nativeAndChatSearchInputTrimsOnlyOuterWhitespace() {
        assertEquals("", OrdersMenuInputSupport.normalizeSearchInput(null));
        assertEquals("", OrdersMenuInputSupport.normalizeSearchInput("   "));
        assertEquals("SÖK {theme} diamond", OrdersMenuInputSupport.normalizeSearchInput(
            "  SÖK {theme} diamond  "));
    }
}
