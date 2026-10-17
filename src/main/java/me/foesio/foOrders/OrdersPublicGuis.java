package me.foesio.foOrders;

import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiClick;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiService;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The Core 1.10 registry for every player-accessible FoOrders inventory. */
final class OrdersPublicGuis implements AutoCloseable {
    private static final Map<String, Set<String>> ACTIONS = Map.ofEntries(
        Map.entry("main", Set.of("sort", "filter", "refresh", "search", "your-orders", "history",
            "previous-page", "next-page", "order-entry")),
        Map.entry("your-orders", Set.of("new-order", "order-entry")),
        Map.entry("new-order", Set.of("cancel", "item", "amount", "price", "enchants", "confirm")),
        Map.entry("item-select", Set.of("back", "sort", "filter", "search", "previous-page", "next-page", "entry")),
        Map.entry("enchant-select", Set.of("back", "clear", "done", "previous-page", "next-page", "entry")),
        Map.entry("manage-order", Set.of("claim", "cancel", "admin-actions")),
        Map.entry("claim-order", Set.of("drop-page", "previous-page", "next-page", "entry")),
        Map.entry("deliver", Set.of()),
        Map.entry("delivery-confirm", Set.of("cancel", "confirm")),
        Map.entry("history", Set.of("back-to-orders", "previous-page", "next-page",
            "order-tab", "deliver-tab"))
    );
    private static final Map<String, Set<String>> PLACEHOLDERS = Map.ofEntries(
        Map.entry("main", Set.of("amount", "current", "delivered", "item", "max_page",
            "owner", "page", "paid", "price", "total")),
        Map.entry("your-orders", Set.of("amount", "delivered", "item", "owner", "paid", "price", "total")),
        Map.entry("new-order", Set.of("amount", "item", "price", "subtotal", "tax", "tax_percent", "total")),
        Map.entry("item-select", Set.of("current", "custom_id", "enchant_state", "item", "max_page", "page")),
        Map.entry("enchant-select", Set.of("current", "enchant", "item", "max", "max_page", "page", "selected")),
        Map.entry("manage-order", Set.of("refund_line")),
        Map.entry("claim-order", Set.of("amount", "item", "max_page", "page")),
        Map.entry("deliver", Set.of()),
        Map.entry("delivery-confirm", Set.of()),
        Map.entry("history", Set.of("action", "details", "entries", "max_page", "page", "pages", "tab", "target", "time"))
    );

    private final FoOrders plugin;
    private final OrdersMenuManager manager;
    private final Map<String, PublicGuiService> services = new LinkedHashMap<>();

    OrdersPublicGuis(FoOrders plugin, OrdersMenuManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    static PublicGuiContract contractFor(String key) {
        String menuId = "orders." + key;
        Set<String> actions = ACTIONS.get(key).stream().map(item -> menuId + "." + item)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean physicalInput = key.equals("deliver");
        return new PublicGuiContract(actions,
            physicalInput ? Map.of("physical-input-slots", PublicGuiContract.GroupRule.exactly(
                PublicGuiContract.GroupRole.PHYSICAL_INPUT, 36)) : Map.of(),
            PLACEHOLDERS.get(key), physicalInput ? PublicGuiContract.InteractionMode.PLUGIN_OWNED
                : PublicGuiContract.InteractionMode.CORE);
    }

    void start(Set<String> availableScreens) {
        for (Map.Entry<String, Set<String>> registration : ACTIONS.entrySet()) {
            String key = registration.getKey();
            if (!availableScreens.contains(key)) continue;
            String menuId = "orders." + key;
            Set<String> actions = registration.getValue().stream().map(item -> menuId + "." + item)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean physicalInput = key.equals("deliver");
            PublicGuiContract contract = contractFor(key);
            Path installed = plugin.getDataFolder().toPath().resolve("guis").resolve(key + ".yml");
            PublicGuiService service = new PublicGuiService(plugin)
                .menu(menuId, installed, contract)
                .entryPoint("gameplay:" + key, menuId);
            switch (key) {
                case "main" -> {
                    service.requireEntry(menuId, "order-entry", 0);
                    service.requireEntryLoreState(menuId, "order-entry", "normal");
                    service.requireEntryLoreState(menuId, "order-entry", "admin");
                }
                case "your-orders" -> {
                    service.requireEntry(menuId, "order-entry", 0);
                    service.requireEntryLoreState(menuId, "order-entry", "normal");
                }
                case "new-order" -> {
                    service.requireEntry(menuId, "item", 1);
                    service.requireButton(menuId, "confirm");
                    service.requireButtonState(menuId, "confirm", "taxed");
                    service.requireButtonState(menuId, "confirm", "untaxed");
                    service.requireButtonState(menuId, "enchants", "hidden");
                }
                case "item-select" -> {
                    service.requireEntry(menuId, "entry", 0);
                    service.requireEntryLoreState(menuId, "entry", "vanilla");
                    service.requireEntryLoreState(menuId, "entry", "custom");
                }
                case "enchant-select" -> {
                    service.requireEntry(menuId, "entry", 0);
                    service.requireEntryLoreState(menuId, "entry", "selected");
                    service.requireEntryLoreState(menuId, "entry", "unselected");
                }
                case "claim-order" -> service.requireEntry(menuId, "entry", 0);
                case "history" -> {
                    service.requireEntry(menuId, "entry", 0);
                    service.requireEntryLoreState(menuId, "entry", "order");
                    service.requireEntryLoreState(menuId, "entry", "deliver");
                    for (String tab : Set.of("order-tab", "deliver-tab")) {
                        service.requireButtonState(menuId, tab, "selected");
                        service.requireButtonState(menuId, tab, "unselected");
                    }
                }
                default -> { }
            }
            if (physicalInput) {
                service.pluginRoute(menuId, new OrdersDeliveryGuiListener(manager));
            } else {
                for (String action : actions) {
                    service.action(action, manager::handlePublicGuiClick);
                }
            }
            service.start();
            GuiLoadReport report = service.reload();
            if (!report.successful()) {
                plugin.getLogger().severe("Public GUI " + key + " is unavailable; other FoOrders menus remain active.");
            }
            services.put(key, service);
        }
    }

    GuiLoadReport reload(String key) {
        return require(key).reload();
    }

    boolean reloadAll() {
        for (PublicGuiService service : services.values()) {
            if (!service.validateInstalled().successful()) return false;
        }
        for (PublicGuiService service : services.values()) {
            if (!service.reload().successful()) return false;
        }
        return true;
    }

    PublicGuiDefinition definition(String key) {
        PublicGuiService service = require(key);
        if (service.snapshot() == null) {
            throw new IllegalStateException("Public GUI " + key + " is unavailable");
        }
        return service.snapshot().require("orders." + key);
    }

    void open(Player player, String key, PublicGuiRenderInput input) {
        require(key).openEntryPoint(player, "gameplay:" + key, input);
    }

    PublicGuiService service(String key) {
        return require(key);
    }

    boolean available(String key) {
        PublicGuiService service = services.get(key);
        return service != null && service.snapshot() != null;
    }

    private PublicGuiService require(String key) {
        PublicGuiService service = services.get(key);
        if (service == null) {
            throw new IllegalArgumentException("Unknown FoOrders public GUI " + key);
        }
        return service;
    }

    @Override
    public void close() {
        services.values().forEach(PublicGuiService::close);
        services.clear();
    }
}
