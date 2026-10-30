package me.foesio.foOrders.api;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSnapshotContractTest {
    @Test
    void externalChangesCannotRewriteAnOrderSnapshot() {
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        enchantments.put("minecraft:efficiency", 3);
        OrderSnapshot snapshot = new OrderSnapshot(UUID.randomUUID(), UUID.randomUUID().toString(),
            null, Material.DIAMOND_PICKAXE, enchantments, 10, 4, 2, 3.5, 14, false, 123);
        enchantments.clear();

        assertEquals(Map.of("minecraft:efficiency", 3), snapshot.enchantments());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.enchantments().put("minecraft:fortune", 2));
        assertEquals(6, snapshot.amountRemaining());
        assertTrue(snapshot.acceptsDeliveries());

        OrderSnapshot filled = new OrderSnapshot(snapshot.ownerId(), snapshot.orderId(), null,
            Material.DIAMOND_PICKAXE, Map.of(), 10, 10, 10, 3.5, 35, false, 123);
        assertFalse(filled.acceptsDeliveries());
        assertEquals(10, filled.amountClaimable());

        OrderSnapshot npc = new OrderSnapshot(snapshot.ownerId(), "npc-order", null,
            Material.DIAMOND, Map.of(), 10, 4, 0, 3.5, 14, false, 123, "Village Trader");
        assertTrue(npc.npcOrder());
        assertEquals("Village Trader", npc.npcName());
        assertEquals(0, npc.amountClaimable());
    }
}
