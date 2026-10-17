package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersMenuCycleOptionTest {
    @Test
    void removedOptionStyleDoesNotBecomeBlankGeneratedLore() {
        List<String> lines = OrdersMenuViewSupport.cycleOptionLines(
            List.of("Nyeste", "Billigste", "Eldste"), 1,
            (selected, option) -> selected ? "§a▶ " + option : "");

        assertEquals(List.of("§a▶ Billigste"), lines);
        assertEquals(List.of(" "), OrdersMenuViewSupport.cycleOptionLines(
            List.of("Nyeste"), 0, (selected, option) -> " "));
        assertEquals(List.of(), OrdersMenuViewSupport.cycleOptionLines(
            List.of("Nyeste"), 0, (selected, option) -> ""));
    }
}
