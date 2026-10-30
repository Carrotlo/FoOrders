# FoOrders

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foorders) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foorders/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Player-created item orders with delivery, payments, history, and GUIs.

*Developed by Carrotio.*

## Features

- Lets players create item orders backed by Vault money.
- Provides GUI flows for item selection, pricing, delivery, claims, and management.
- Supports search, personal orders, history, and administrator review.
- Includes item and name blacklists plus optional Discord webhooks.
- Keeps GUI, dialog, message, and sound behavior configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foorders). Check the [versions page](https://modrinth.com/plugin/foorders/versions) for supported Minecraft versions and server platforms.

- **Required:** `Vault`
- **Optional integrations:** None

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/order [<search>]` — Opens the orders menu Aliases: `/orders`.
- `/orderadmin <reload|version|editor|history <player>>` — Admin command for FoOrders Aliases: `/ordersadmin`.

### Permissions

- `foorders.use` — Allows using the /order menu Default: `true`.
- `foorders.admin` — Allows using FoOrders admin commands and editor Default: `op`.
- `foorders.receive-created-order-announcements` — Allows seeing chat announcements when new orders are created Default: `true`.
- `foorders.maxorders.*` — Allows setting max order limits via foorders.maxorders.<amount> Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `dialogs/*.yml` — Native dialog layouts and text.

For amount, price, and search input labels, edit `target-label` in `dialogs/amount.yml`, `dialogs/money.yml`, or `dialogs/search.yml`. The bundled native field label and first chat-prompt line use `{target}`, so this one value changes both. You can still write independent text in `field-label` or `fallback.lines`. Older `messages.yml:chat-input.targets.*` entries are legacy; customized values are carried into unchanged dialog target labels during the v34 upgrade, and v37 connects unchanged older field/prompt defaults to `{target}` without replacing owner-written text. Those message keys are no longer read at runtime.

Those three Paper text dialogs also expose `columns` (1 or 2) for the action-button layout. v36 adds the new field to older owner dialog files once; removing it on the current schema disables that native dialog instead of regenerating a hardcoded layout. Chat input remains available.

The custom-item picker displays template lore only when `guis/item-select.yml:items.custom-entry.lore` includes `{template_lore}`. An empty or removed lore list remains empty on upgrade. v35 also restores earlier empty/removed lists that v30 had turned into that token, but only when the pre-v30 backup proves the original value and the current list still exactly matches the generated line.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `max-order-per-player`, `order-menu-refresh-cooldown-ms`, `order-tax`, `file-logging`, `native-dialogs`, `announce-created-orders-in-chat`, `history`, `discord-webhook`, `blacklist-items`, `blacklist-names`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

### Maven dependency example

Install the matching `FoOrders-4.8.jar` locally if it is not in your Maven repository. These coordinates do not claim a published artifact. Use `provided` scope so the server supplies FoOrders:

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoOrders</artifactId>
  <version>4.8</version>
  <scope>provided</scope>
</dependency>
```

FoOrders registers `me.foesio.foOrders.api.FoOrdersApi` in Bukkit's `ServicesManager` after its data stores and Vault economy have initialized. The service is removed when FoOrders disables. Compile against the matching FoOrders jar as `compileOnly`/`provided`; do not shade FoOrders into your plugin. FoOrders does not publish a public Maven API artifact at present. For a Gradle project, place the FoOrders jar in `libs/` and use `compileOnly files('libs/FoOrders-4.8.jar')` with your actual jar name.

For a required integration, put `depend: [FoOrders]` in `plugin.yml`. For an optional integration, put `softdepend: [FoOrders]`, check that FoOrders is enabled, and load a separate integration class only after that check. Paper plugin metadata also needs a server dependency with classpath access to FoOrders. Obtain a fresh service lookup after each enable; do not retain it across a disable/re-enable cycle.

```java
import me.foesio.foOrders.api.FoOrdersApi;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

var registration = Bukkit.getServicesManager().getRegistration(FoOrdersApi.class);
if (registration != null) {
    FoOrdersApi orders = registration.getProvider();
    orders.registerCustomItem(this, "ruby_ingot", new ItemStack(Material.REDSTONE), false);
    var openOrders = orders.getOpenOrders();
}
```

The example registers `yourplugin_ruby_ingot` (using the source plugin's normalized name). Registration is an upsert: a repeat with the same template does no file write, and an update with a changed template is rejected while an active order uses that ID. The catalog persists in `custom-items.yml` until the source plugin unregisters it or a server owner removes it. Registration refuses to take over an owner-created ID or another plugin's registered ID. `getCustomItems()` and `getCustomItem(id)` include owner-created and plugin-registered entries. Templates returned by the API are copies. A plugin should register after its own item definitions are ready, usually in `onEnable()`; optional integrations need `softdepend` so FoOrders enables first.

### Order methods

- `getOrder(id)`, `getPlayerOrders(ownerId)`, and `getOpenOrders()` return immutable snapshots of *live* orders. Open means not cancelled and still accepting deliveries. Filled or cancelled orders can remain live while items are claimable; a closed order is absent. `getOrder(id)` uses an index. `getOpenOrders()` copies the global order set, so cache or rate-limit frequent displays.
- `quote(player, request)` validates the current item selection, blacklist, permission, effective order limit, amount, price, tax, Vault provider, and balance. Its `effectiveRequest` contains normalized enchantments and the catalog item's resolved material. A quote is an estimate, not a reservation. Check `status()` before using `effectiveRequest`.
- `createOrder(player, request)` repeats validation, fires `OrderBeforeCreateEvent`, rechecks conditions, withdraws the total, and inserts the order. `cancelOrder(player, id)` only cancels that player's own order and refunds the unpaid portion. Both return `OrderActionResult` with an explicit status; the snapshot is present only for `SUCCESS`. The caller's own permission is checked for creation. These methods do not grant admin rights.
- `createOrderForPlayer(player, request)` is an explicit alias for `createOrder`: the online player must have `foorders.use`, has their normal order limit, pays the configured price and tax through Vault, and sees the result in “Your Orders.”
- `createOrderForNpc(name, request)` creates a virtual order with the same item, blacklist, amount, price, and catalog validation. Names are 1–32 ASCII letters, digits, spaces, `_`, `.`, or `-`; an invalid name returns `INVALID_NPC_NAME`. It needs an available Vault economy to pay deliverers, but charges no account, tax, or listing fee and ignores player permissions and order limits. Its accepted items disappear; it never gains claimable items. It closes automatically on full delivery. Admins can cancel or delete an NPC order with no refund. The supplied name appears in listings and search, and `OrderSnapshot.npcName()` identifies it; its `ownerId()` is a virtual UUID and must not be treated as a player. Do not retry a successful creation blindly.
- `matchesOrder(id, item)` applies FoOrders' current material, custom template, and enchantment matching rules without taking the item. It returns `false` if the order does not exist.
- `getOrderHistory(playerId)` and `getDeliveryHistory(playerId)` return retained history rows when history is enabled. The first lookup for a player may read YAML from disk, so do not call these on every scoreboard update. Rows may include legacy free-text entries (`eventKey` empty) and are subject to the configured retention limit. They are not an accounting ledger. A plugin displaying another player's history must enforce its own access permissions.

Call methods involving a `Player`, Bukkit `ItemStack`, or catalog writes from the relevant server thread. In particular, create/cancel/quote require the player's owning thread. FoOrders does not move calls to another scheduler or accept arbitrary async writes. Catalog writes finish on disk before returning; order and history writes are queued asynchronously. Read-only order snapshots contain UUIDs, `Material`, numbers, strings, and copied maps. No API type exposes the shaded FoPluginCore or mutable storage objects. After FoOrders disables, calls on a cached service throw `IllegalStateException`.

Call `createOrderForNpc` from a server execution context too (global or an appropriate region thread). It runs validation, event listeners, and the order mutation inline. For example:

```java
OrderRequest request = new OrderRequest(null, Material.DIAMOND, Map.of(), 32, 50.0);
// player is an online Player; call this on their owning server thread.
OrderActionResult playerOrder = orders.createOrderForPlayer(player, request);
OrderActionResult result = orders.createOrderForNpc("Village Trader", request);
if (result.successful()) getLogger().info("NPC order: " + result.order().orderId());
```

### Events

All event classes are in `me.foesio.foOrders.api.event`. Register a normal Bukkit listener and use `@EventHandler`. The events run inline on the thread performing the FoOrders action. On regionized servers this can be an entity/region thread, not a universal main thread. The Bukkit asynchronous flag reflects whether dispatch occurred on Bukkit's primary thread; listeners should use the actor's scheduler before interacting with unrelated player or world state.

| Event | Meaning |
| --- | --- |
| `OrderBeforeCreateEvent` | Cancellable. Fires only after validation and before the player Vault withdrawal or NPC insertion. Carries the effective request and quote. `getNpcName()` is non-null for an NPC order; its quote has zero tax and total charge. Cancellation creates no order and makes no charge. The request is read-only; listeners cannot change price or items. |
| `OrderCreatedEvent` | The order entered live state. Player orders have a completed withdrawal; NPC orders have zero tax and charge, a virtual owner UUID, and no player actor. |
| `OrderDeliveryAcceptedEvent` | Matching items were accepted and payout succeeded. Player order claimable count increases; NPC items are discarded and claimable stays zero. Carries accepted count and payout. |
| `OrderFilledEvent` | The delivery first brought delivered count to the ordered amount. Items can still be unclaimed. |
| `OrderItemsClaimedEvent` | Claimable count decreased. Carries count and `INVENTORY` or `DROP`; dropped stacks may spawn on a later tick. |
| `OrderCancelledEvent` | Owner or admin cancellation succeeded. Carries cause and refund. |
| `OrderDeletedByAdminEvent` | Admin removed the order, with any refund applied. |
| `OrderClosedEvent` | The order left the live list. Carries reason: filled and claimed, NPC filled, cancelled, or admin deleted. A cleanup of an already terminal order has no actor ID. |

Post events describe completed **in-memory** transitions after the associated payment or item transfer. FoOrders schedules SQLite/history writes asynchronously, so these events do not mean disk commit. Failed requests and no-ops emit no post event. `OrderClosedEvent` can follow another event in the same action. Restart loading does not emit lifecycle events.

```java
@EventHandler
public void onOrderCreated(OrderCreatedEvent event) {
    var order = event.getOrder();
    getLogger().info(order.ownerId() + " ordered " + order.amountOrdered() + " " + order.material());
}
```

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoOrdersApi`

```java
Optional<OrderSnapshot> getOrder(String);
List<OrderSnapshot> getPlayerOrders(UUID);
List<OrderSnapshot> getOpenOrders();
OrderQuote quote(Player, OrderRequest);
OrderActionResult createOrder(Player, OrderRequest);
OrderActionResult createOrderForPlayer(Player, OrderRequest);
OrderActionResult createOrderForNpc(String, OrderRequest);
OrderActionResult cancelOrder(Player, String);
boolean matchesOrder(String, ItemStack);
List<CustomItemSnapshot> getCustomItems();
Optional<CustomItemSnapshot> getCustomItem(String);
CustomItemSnapshot registerCustomItem(Plugin, String, ItemStack, boolean);
boolean unregisterCustomItem(Plugin, String);
List<HistorySnapshot> getOrderHistory(UUID);
List<HistorySnapshot> getDeliveryHistory(UUID);
```

**Other public contract types:** `CustomItemSnapshot`, `HistorySnapshot`, `OrderActionResult`, `OrderQuote`, `OrderRequest`, `OrderSnapshot`.

### Complete Bukkit event index

- `OrderBeforeCreateEvent`.
- `OrderCancelledEvent`.
- `OrderClosedEvent`.
- `OrderCreatedEvent`.
- `OrderDeletedByAdminEvent`.
- `OrderDeliveryAcceptedEvent`.
- `OrderFilledEvent`.
- `OrderItemsClaimedEvent`.
- `OrderLifecycleEvent` — abstract base for related events.

</details>
