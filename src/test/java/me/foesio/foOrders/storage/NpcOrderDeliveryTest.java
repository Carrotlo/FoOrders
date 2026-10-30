package me.foesio.foOrders.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcOrderDeliveryTest {
    @Test
    void npcDeliveriesPayWithoutCreatingClaimableItems() {
        PlayerDataStore.OrderEntry npc = new PlayerDataStore.OrderEntry();
        npc.setAmountOrdered(5);
        npc.setPricePerItem(12);
        npc.setNpcName("Village Trader");
        assertFalse(npc.recordDelivery(2, 24));
        assertEquals(0, npc.getAmountClaimable());
        assertEquals(24, npc.getAmountPaid());
        assertTrue(npc.recordDelivery(3, 36));
        assertEquals(5, npc.getAmountDelivered());
        assertEquals(0, npc.getAmountClaimable());

        PlayerDataStore.OrderEntry player = new PlayerDataStore.OrderEntry();
        player.setAmountOrdered(5);
        player.setPricePerItem(12);
        assertFalse(player.recordDelivery(2, 24));
        assertEquals(2, player.getAmountClaimable());
    }
}
