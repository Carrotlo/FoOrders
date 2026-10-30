package me.foesio.foOrders.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The supported FoOrders integration boundary. Calls involving a player must run on that player's owning server thread. */
public interface FoOrdersApi {
    /** Looks up one live order without scanning every player. */
    Optional<OrderSnapshot> getOrder(String orderId);

    /** Includes cancelled and filled orders that still have claimable items. */
    List<OrderSnapshot> getPlayerOrders(UUID ownerId);

    /** Returns orders still accepting deliveries. */
    List<OrderSnapshot> getOpenOrders();

    /** Reports current eligibility and effective pricing without reserving funds or a slot. */
    OrderQuote quote(Player owner, OrderRequest request);

    /** Charges the owner and creates an order only if all current rules permit it. */
    OrderActionResult createOrder(Player owner, OrderRequest request);

    /** Alias for player creation: charges Vault and lists the order in the player's own orders. */
    OrderActionResult createOrderForPlayer(Player owner, OrderRequest request);

    /**
     * Creates an order under a virtual owner. No account is charged, accepted items are discarded,
     * and deliverer payouts are created through Vault. The name must be 1..32 safe display characters.
     * NPC orders have no claimable items and close automatically when filled.
     */
    OrderActionResult createOrderForNpc(String npcListingName, OrderRequest request);

    /** Cancels an order owned by the player and refunds its remaining funds. */
    OrderActionResult cancelOrder(Player owner, String orderId);

    /** Applies FoOrders' current matching rules without consuming the item. */
    boolean matchesOrder(String orderId, ItemStack item);

    /** Includes owner-created and plugin-registered catalog entries. */
    List<CustomItemSnapshot> getCustomItems();

    Optional<CustomItemSnapshot> getCustomItem(String id);

    /** Namespaces the ID to the registering plugin; re-registering the same ID updates it. */
    CustomItemSnapshot registerCustomItem(Plugin source, String id, ItemStack template, boolean allowOrderEnchants);

    /** Removes only a catalog entry registered under the source plugin's namespace. */
    boolean unregisterCustomItem(Plugin source, String id);

    /** Returns retained history, if history collection is enabled. This is not a payment ledger. */
    List<HistorySnapshot> getOrderHistory(UUID playerId);

    List<HistorySnapshot> getDeliveryHistory(UUID playerId);
}
