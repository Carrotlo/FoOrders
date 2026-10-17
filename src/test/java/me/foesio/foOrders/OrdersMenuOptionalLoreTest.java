package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersMenuOptionalLoreTest {
    @Test
    void emptyConfiguredDetailRemovesOnlyThatGeneratedLine() {
        assertEquals(List.of(), OrdersMenuItemSupport.optionalLoreLine(""));
        assertEquals(List.of(), OrdersMenuItemSupport.optionalLoreLine(null));
        assertEquals(List.of(" "), OrdersMenuItemSupport.optionalLoreLine(" "));
        assertEquals(List.of("Hoeveelheid: 4"), OrdersMenuItemSupport.optionalLoreLine("Hoeveelheid: 4"));
    }
}
