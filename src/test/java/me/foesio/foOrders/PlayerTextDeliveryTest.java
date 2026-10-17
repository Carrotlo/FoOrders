package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTextDeliveryTest {
    @Test
    void paperApiKeepsComponentMessagePathAvailable() {
        assertTrue(PlayerTextDelivery.usesPaperMessages());
    }
}
