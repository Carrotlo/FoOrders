package me.foesio.foOrders;

import me.foesio.foOrders.api.CustomItemSnapshot;
import me.foesio.foOrders.api.FoOrdersApi;
import me.foesio.foOrders.api.HistorySnapshot;
import me.foesio.foOrders.api.OrderActionResult;
import me.foesio.foOrders.api.OrderQuote;
import me.foesio.foOrders.api.OrderRequest;
import me.foesio.foOrders.api.OrderSnapshot;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class FoOrdersApiImpl implements FoOrdersApi {
    private final OrdersMenuManager manager;

    FoOrdersApiImpl(OrdersMenuManager manager) {
        this.manager = manager;
    }

    private void requireEnabled() {
        if (!manager.plugin.isEnabled()) throw new IllegalStateException("FoOrders is disabled");
    }

    @Override
    public Optional<OrderSnapshot> getOrder(String orderId) {
        requireEnabled();
        PlayerDataStore.PlayerOrderRecord found = manager.playerDataStore.getOrderSnapshot(orderId);
        return found == null ? Optional.empty() : Optional.of(OrderSnapshots.of(found.getPlayerId(), found.getOrder()));
    }

    @Override
    public List<OrderSnapshot> getPlayerOrders(UUID ownerId) {
        requireEnabled();
        Objects.requireNonNull(ownerId, "ownerId");
        return manager.playerDataStore.getPlayerOrdersSnapshot(ownerId).stream()
            .map(record -> OrderSnapshots.of(record.getPlayerId(), record.getOrder())).toList();
    }

    @Override
    public List<OrderSnapshot> getOpenOrders() {
        requireEnabled();
        return manager.playerDataStore.getAllOrdersSnapshot().stream()
            .map(record -> OrderSnapshots.of(record.getPlayerId(), record.getOrder()))
            .filter(OrderSnapshot::acceptsDeliveries).toList();
    }

    @Override
    public OrderQuote quote(Player owner, OrderRequest request) {
        requireEnabled();
        return manager.interactionSupport.actionSupport.quote(owner, request);
    }

    @Override
    public OrderActionResult createOrder(Player owner, OrderRequest request) {
        requireEnabled();
        return manager.interactionSupport.actionSupport.createOrder(owner, request);
    }

    @Override
    public OrderActionResult createOrderForPlayer(Player owner, OrderRequest request) {
        return createOrder(owner, request);
    }

    @Override
    public OrderActionResult createOrderForNpc(String npcListingName, OrderRequest request) {
        requireEnabled();
        return manager.interactionSupport.actionSupport.createNpcOrder(npcListingName, request);
    }

    @Override
    public OrderActionResult cancelOrder(Player owner, String orderId) {
        requireEnabled();
        return manager.interactionSupport.deliverySupport.cancelOrder(owner, orderId);
    }

    @Override
    public boolean matchesOrder(String orderId, ItemStack item) {
        requireEnabled();
        PlayerDataStore.PlayerOrderRecord found = manager.playerDataStore.getOrderSnapshot(orderId);
        return found != null && manager.itemSupport.matchesOrderedItem(item, found.getOrder());
    }

    @Override
    public List<CustomItemSnapshot> getCustomItems() {
        requireEnabled();
        return manager.customItemStore.listAll().stream().map(this::snapshot).toList();
    }

    @Override
    public Optional<CustomItemSnapshot> getCustomItem(String id) {
        requireEnabled();
        CustomItemStore.CustomItemDefinition definition = manager.customItemStore.get(id);
        return definition == null ? Optional.empty() : Optional.of(snapshot(definition));
    }

    @Override
    public CustomItemSnapshot registerCustomItem(Plugin source, String id, ItemStack template, boolean allowOrderEnchants) {
        requireEnabled();
        String namespacedId = namespacedId(source, id);
        if (template == null || template.getType() == Material.AIR || !template.getType().isItem()) {
            throw new IllegalArgumentException("Invalid custom item template");
        }
        CustomItemStore.CustomItemDefinition existing = manager.customItemStore.get(namespacedId);
        if (existing != null && source.getName().equals(existing.registeredBy())) {
            if (template != null && existing.template().equals(template)
                && existing.allowOrderEnchants() == allowOrderEnchants) {
                return snapshot(existing);
            }
            if (manager.itemSupport.isCustomItemInUse(namespacedId)) {
                throw new IllegalStateException("Cannot change a custom item used by an active order");
            }
        }
        CustomItemSnapshot result = snapshot(manager.customItemStore.registerManaged(source.getName(),
            namespacedId, template, allowOrderEnchants));
        manager.invalidateItemSelectCaches();
        return result;
    }

    @Override
    public boolean unregisterCustomItem(Plugin source, String id) {
        requireEnabled();
        String namespacedId = namespacedId(source, id);
        if (manager.itemSupport.isCustomItemInUse(namespacedId)) return false;
        boolean removed = manager.customItemStore.removeManaged(source.getName(), namespacedId);
        if (removed) manager.invalidateItemSelectCaches();
        return removed;
    }

    @Override
    public List<HistorySnapshot> getOrderHistory(UUID playerId) {
        requireEnabled();
        Objects.requireNonNull(playerId, "playerId");
        return manager.historyEnabled ? manager.historyDataStore.getOrderHistory(playerId).stream().map(this::snapshot).toList() : List.of();
    }

    @Override
    public List<HistorySnapshot> getDeliveryHistory(UUID playerId) {
        requireEnabled();
        Objects.requireNonNull(playerId, "playerId");
        return manager.historyEnabled ? manager.historyDataStore.getDeliverHistory(playerId).stream().map(this::snapshot).toList() : List.of();
    }

    private String namespacedId(Plugin source, String id) {
        Objects.requireNonNull(source, "source");
        if (!source.isEnabled()) throw new IllegalArgumentException("Source plugin must be enabled");
        String sourceId = CustomItemStore.normalizeId(source.getName());
        String itemId = CustomItemStore.normalizeId(id);
        if (sourceId == null || itemId == null) throw new IllegalArgumentException("Invalid plugin or custom item ID");
        return sourceId + "_" + itemId;
    }

    private CustomItemSnapshot snapshot(CustomItemStore.CustomItemDefinition definition) {
        return new CustomItemSnapshot(definition.id(), definition.template(),
            definition.allowOrderEnchants(), definition.createdAtEpochMillis(), definition.registeredBy());
    }

    private HistorySnapshot snapshot(HistoryDataStore.HistoryEntry entry) {
        return new HistorySnapshot(entry.timestamp(), entry.action(), entry.details(), entry.eventKey(), entry.values());
    }
}
