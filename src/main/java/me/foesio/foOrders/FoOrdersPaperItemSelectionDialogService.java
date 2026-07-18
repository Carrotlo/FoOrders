package me.foesio.foOrders;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.text.FoText;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

final class FoOrdersPaperItemSelectionDialogService implements FoOrdersItemSelectionDialogService {
    private static final String INPUT_KEY = "value";
    private static final int BODY_WIDTH = 320;
    private static final int INPUT_WIDTH = 300;
    private static final int BUTTON_WIDTH = 144;
    private static final int COLUMNS = 3;
    private static final int MAX_CACHED_DIALOGS = 96;
    private static final long PENDING_SELECTION_TICKS = 20L * 60L * 5L;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CACHED_CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(Integer.MAX_VALUE)
        .lifetime(Duration.ofHours(6))
        .build();

    private final OrdersMenuManager manager;
    private final NativeDialogSupport support;
    private final ConcurrentMap<UUID, PendingSelection> pendingSelections = new ConcurrentHashMap<>();
    private final ConcurrentMap<ButtonVisualKey, ButtonVisual> buttonVisuals = new ConcurrentHashMap<>();
    private final Map<DialogCacheKey, Dialog> dialogCache = Collections.synchronizedMap(
        new LinkedHashMap<DialogCacheKey, Dialog>(64, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<DialogCacheKey, Dialog> eldest) {
                return size() > MAX_CACHED_DIALOGS;
            }
        }
    );

    FoOrdersPaperItemSelectionDialogService(OrdersMenuManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.support = Objects.requireNonNull(manager.dialogService().support(), "support");
    }

    @Override
    public boolean open(Player player, String currentChoiceKey, Consumer<OrderableItemOption> onSelect, Runnable onFallback) {
        return open(player, currentChoiceKey, "", onSelect, onFallback);
    }

    @Override
    public void clearCache() {
        dialogCache.clear();
        buttonVisuals.clear();
    }

    @Override
    public void clearPending(UUID playerId) {
        if (playerId != null) {
            pendingSelections.remove(playerId);
        }
    }

    private boolean open(
        Player player,
        String currentChoiceKey,
        String filter,
        Consumer<OrderableItemOption> onSelect,
        Runnable onFallback
    ) {
        if (player == null || !player.isOnline() || !support.canUseNativeDialogs() || onSelect == null) {
            return false;
        }

        String safeCurrentKey = safeChoiceKey(currentChoiceKey);
        UUID playerId = player.getUniqueId();
        PendingSelection pending = new PendingSelection(safeCurrentKey, onSelect, onFallback);
        pendingSelections.put(playerId, pending);
        manager.scheduler.runLaterForPlayer(player, () -> pendingSelections.remove(playerId, pending), PENDING_SELECTION_TICKS);

        try {
            ((Audience) player).showDialog(cachedDialog(safeCurrentKey, filter));
            return true;
        } catch (RuntimeException | LinkageError exception) {
            pendingSelections.remove(playerId, pending);
            support.disableForSession("FoOrders item selection dialog failed: " + exception.getMessage());
            String message = "Native item selection dialog failed. Falling back to inventory menu: " + exception.getMessage();
            manager.plugin.getLogger().warning(message);
            if (manager.fileLogger() != null) {
                manager.fileLogger().warn(message);
            }
            return false;
        }
    }

    private Dialog cachedDialog(String currentChoiceKey, String filter) {
        String normalizedFilter = normalizeFilter(filter);
        DialogCacheKey key = new DialogCacheKey(manager.itemSelectContentRevision(), currentChoiceKey, normalizedFilter);
        Dialog cached = dialogCache.get(key);
        if (cached != null) {
            return cached;
        }

        Dialog dialog = createDialog(currentChoiceKey, normalizedFilter);
        dialogCache.put(key, dialog);
        return dialog;
    }

    private Dialog createDialog(String currentChoiceKey, String filter) {
        ItemSelectState itemSelectState = new ItemSelectState();
        itemSelectState.search = filter;
        List<OrderableItemOption> choices = manager.itemSupport.getCachedFilteredSortedItems(itemSelectState);

        return Dialog.create(factory -> factory.empty()
            .base(base(currentChoiceKey, choices, filter))
            .type(DialogType.multiAction(buttons(currentChoiceKey, choices), null, COLUMNS)));
    }

    private DialogBase base(String currentChoiceKey, List<OrderableItemOption> choices, String filter) {
        Component title = component(DialogIcons.withIcon("Select Item", "chest"));
        List<DialogBody> body = new ArrayList<>();
        OrderableItemOption current = findChoice(currentChoiceKey);
        if (current != null) {
            body.add(DialogBody.item(
                preview(current),
                null,
                false,
                true,
                16,
                16
            ));
        }
        if (choices.isEmpty()) {
            body.add(DialogBody.plainMessage(component(OrdersMenuManager.MUTED + "No items match this search."), BODY_WIDTH));
        }

        return DialogBase.builder(title)
            .externalTitle(title)
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.copyOf(body))
            .inputs(List.of(DialogInput.text(INPUT_KEY, component("Search"))
                .width(INPUT_WIDTH)
                .labelVisible(true)
                .initial(filter)
                .maxLength(64)
                .build()))
            .build();
    }

    private List<ActionButton> buttons(String currentChoiceKey, List<OrderableItemOption> choices) {
        List<ActionButton> buttons = new ArrayList<>(choices.size() + 1);
        buttons.add(searchButton());
        for (OrderableItemOption choice : choices) {
            buttons.add(choiceButton(choice, choice.choiceKey().equals(currentChoiceKey)));
        }
        return List.copyOf(buttons);
    }

    private ActionButton searchButton() {
        ButtonVisual visual = buttonVisuals.computeIfAbsent(
            ButtonVisualKey.searchButton(),
            ignored -> visual(DialogButton.search("Search", "Search items.", BUTTON_WIDTH))
        );
        return visual.withAction(DialogAction.customClick(this::handleSearch, CACHED_CALLBACK_OPTIONS));
    }

    private ActionButton choiceButton(OrderableItemOption choice, boolean current) {
        ButtonVisualKey key = ButtonVisualKey.choice(choice.choiceKey(), choice.material(), choice.choiceLabel(), current);
        ButtonVisual visual = buttonVisuals.computeIfAbsent(key, ignored -> {
            String color = current ? DialogButton.CONFIRM_ICON_COLOR : OrdersMenuManager.WHITE;
            String tooltip = current ? "Current item." : "Choose this item.";
            return visual(DialogButton.icon(
                choice.material().name().toLowerCase(Locale.ROOT),
                color + choice.choiceLabel(),
                tooltip,
                BUTTON_WIDTH
            ));
        });
        return visual.withAction(DialogAction.customClick(
            (response, audience) -> handleSelect(audience, choice.choiceKey()),
            CACHED_CALLBACK_OPTIONS
        ));
    }

    private ButtonVisual visual(DialogButton button) {
        return new ButtonVisual(component(button.labelWithIcon()), component(button.tooltip()), button.width());
    }

    private void handleSearch(DialogResponseView view, Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingSelection pending = pendingSelections.get(playerId);
        if (pending == null) {
            return;
        }
        String filter = readText(view);
        runForPlayer(playerId, audience, currentPlayer ->
            open(currentPlayer, pending.currentChoiceKey(), filter, pending.onSelect(), pending.onFallback()));
    }

    private void handleSelect(Audience audience, String choiceKey) {
        if (!(audience instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingSelection pending = pendingSelections.remove(playerId);
        if (pending == null) {
            return;
        }
        OrderableItemOption selected = findChoice(choiceKey);
        if (selected == null) {
            runForPlayer(playerId, audience, pending.onFallback());
            return;
        }
        runForPlayer(playerId, audience, () -> pending.onSelect().accept(selected));
    }

    private OrderableItemOption findChoice(String choiceKey) {
        String normalizedKey = safeChoiceKey(choiceKey);
        ItemSelectState itemSelectState = new ItemSelectState();
        for (OrderableItemOption option : manager.itemSupport.getCachedFilteredSortedItems(itemSelectState)) {
            if (option.choiceKey().equals(normalizedKey)) {
                return option;
            }
        }
        return null;
    }

    private ItemStack preview(OrderableItemOption option) {
        ItemStack preview = option.previewItem().clone();
        preview.setAmount(1);
        return preview;
    }

    private void runForPlayer(UUID expectedPlayerId, Audience audience, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        runForPlayer(expectedPlayerId, audience, player -> runnable.run());
    }

    private void runForPlayer(UUID expectedPlayerId, Audience audience, Consumer<Player> action) {
        if (action == null || !(audience instanceof Player player) || !player.getUniqueId().equals(expectedPlayerId)) {
            return;
        }
        manager.scheduler.runForPlayer(player, () -> {
            if (manager.plugin.isEnabled() && player.isOnline() && player.getUniqueId().equals(expectedPlayerId)) {
                action.accept(player);
            }
        });
    }

    private String readText(DialogResponseView view) {
        if (view == null) {
            return "";
        }
        String value = view.getText(INPUT_KEY);
        return value == null ? "" : value;
    }

    private Component component(String text) {
        return DialogIcons.inlineTokens(LEGACY.deserialize(FoText.color(text == null ? "" : text)));
    }

    private String normalizeFilter(String filter) {
        return filter == null ? "" : filter.trim();
    }

    private String safeChoiceKey(String currentChoiceKey) {
        if (currentChoiceKey == null || currentChoiceKey.isBlank()) {
            return OrderableItemOption.materialChoiceKey(Material.STONE);
        }
        return currentChoiceKey.trim().toLowerCase(Locale.ROOT).startsWith("custom:")
            ? OrderableItemOption.customChoiceKey(currentChoiceKey.substring("custom:".length()))
            : materialChoiceKey(currentChoiceKey);
    }

    private String materialChoiceKey(String choiceKey) {
        String trimmed = choiceKey.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("material:")) {
            return trimmed;
        }

        String materialName = trimmed.substring("material:".length()).trim();
        Material material = Material.matchMaterial(materialName);
        return material == null ? trimmed : OrderableItemOption.materialChoiceKey(material);
    }

    private record PendingSelection(
        String currentChoiceKey,
        Consumer<OrderableItemOption> onSelect,
        Runnable onFallback
    ) {
    }

    private record DialogCacheKey(int contentRevision, String currentChoiceKey, String filter) {
    }

    private record ButtonVisual(Component label, Component tooltip, int width) {
        ActionButton withAction(DialogAction action) {
            return ActionButton.create(label, tooltip, width, action);
        }
    }

    private record ButtonVisualKey(
        String choiceKey,
        Material material,
        String label,
        boolean current,
        boolean search
    ) {
        static ButtonVisualKey searchButton() {
            return new ButtonVisualKey("", null, "Search", false, true);
        }

        static ButtonVisualKey choice(String choiceKey, Material material, String label, boolean current) {
            return new ButtonVisualKey(choiceKey, material, label, current, false);
        }
    }
}
