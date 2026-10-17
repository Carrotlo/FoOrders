package me.foesio.foOrders;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.text.FoText;
import me.foesio.foOrders.util.OwnerYaml;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

final class FoOrdersPaperItemSelectionDialogService implements FoOrdersItemSelectionDialogService {
    private static final String INPUT_KEY = "value";
    private static final int MAX_CACHED_DIALOGS = 96;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CACHED_CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofDays(3650))
        .build();

    private final OrdersMenuManager manager;
    private final NativeDialogSupport support;
    private final ConcurrentMap<UUID, PendingSelection> pendingSelections = new ConcurrentHashMap<>();
    private volatile ItemSelectDialogConfig config;
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
        loadConfig(true);
    }

    @Override
    public boolean open(Player player, String currentChoiceKey, Consumer<OrderableItemOption> onSelect, Runnable onFallback) {
        if (player == null) {
            return false;
        }
        ItemSelectState inventoryState = manager.menuStates
            .computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState())
            .getOrCreateItemSelectState();
        return open(player, currentChoiceKey, new PickerState(
            inventoryState.search, 0, inventoryState.sortIndex, inventoryState.filterIndex), onSelect, onFallback);
    }

    @Override
    public void clearCache() {
        dialogCache.clear();
    }

    @Override
    public void clearPending(UUID playerId) {
        if (playerId != null) {
            pendingSelections.remove(playerId);
        }
    }

    @Override
    public void clearAllPending() {
        pendingSelections.clear();
    }

    private boolean open(
        Player player,
        String currentChoiceKey,
        PickerState state,
        Consumer<OrderableItemOption> onSelect,
        Runnable onFallback
    ) {
        ItemSelectDialogConfig currentConfig = config;
        if (player == null || !player.isOnline() || !support.canUseNativeDialogs(player)
            || onSelect == null || currentConfig == null || !currentConfig.enabled()
            || !manager.publicGuiAvailable("item-select")) {
            return false;
        }

        state = clearUnavailableControls(state, currentConfig.buttonOrder());

        // Do not strand choices on an unreachable page or show an empty
        // dialog with no Escape route. The inventory picker has its own Back.
        ItemSelectState visibleState = new ItemSelectState();
        visibleState.search = state.search();
        visibleState.sortIndex = state.sortIndex();
        visibleState.filterIndex = state.filterIndex();
        int choiceCount = manager.itemSupport.getCachedFilteredSortedItems(visibleState).size();
        if (!currentConfig.canReachChoiceCount(choiceCount)) {
            return false;
        }
        state = clampPage(state, choiceCount, currentConfig.pageSize());

        String safeCurrentKey = safeChoiceKey(currentChoiceKey);
        UUID playerId = player.getUniqueId();
        PendingSelection pending = new PendingSelection(safeCurrentKey, state, onSelect, onFallback);
        pendingSelections.put(playerId, pending);

        try {
            ((Audience) player).showDialog(cachedDialog(player, safeCurrentKey, state));
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

    static PickerState clearUnavailableControls(PickerState state, List<String> buttonOrder) {
        if (state == null) {
            return new PickerState("", 0, 0, 0);
        }
        ItemSelectState normalized = new ItemSelectState();
        normalized.search = state.search();
        normalized.page = state.page() + 1;
        normalized.sortIndex = state.sortIndex();
        normalized.filterIndex = state.filterIndex();
        normalized.clearUnavailableControls(buttonOrder.contains("sort"), buttonOrder.contains("filter"),
            buttonOrder.contains("search"));
        return new PickerState(normalized.search, normalized.page - 1,
            normalized.sortIndex, normalized.filterIndex);
    }

    static PickerState clampPage(PickerState state, int choiceCount, int pageSize) {
        int lastPage = Math.max(0, (Math.max(0, choiceCount) - 1) / pageSize);
        return new PickerState(state.search(), Math.max(0, Math.min(state.page(), lastPage)),
            state.sortIndex(), state.filterIndex());
    }

    private Dialog cachedDialog(Player player, String currentChoiceKey, PickerState state) {
        DialogCacheKey key = new DialogCacheKey(player.getUniqueId(), manager.itemSelectContentRevision(), currentChoiceKey, state);
        Dialog cached = dialogCache.get(key);
        if (cached != null) {
            return cached;
        }

        Dialog dialog = createDialog(player, currentChoiceKey, state);
        dialogCache.put(key, dialog);
        return dialog;
    }

    private Dialog createDialog(Player player, String currentChoiceKey, PickerState state) {
        ItemSelectState itemSelectState = new ItemSelectState();
        itemSelectState.search = state.search();
        itemSelectState.sortIndex = state.sortIndex();
        itemSelectState.filterIndex = state.filterIndex();
        List<OrderableItemOption> choices = manager.itemSupport.getCachedFilteredSortedItems(itemSelectState);
        int pageCount = Math.max(1, (choices.size() + config.pageSize() - 1) / config.pageSize());
        int page = Math.min(state.page(), pageCount - 1);
        int from = page * config.pageSize();
        List<OrderableItemOption> visible = choices.subList(from, Math.min(choices.size(), from + config.pageSize()));

        return Dialog.create(factory -> factory.empty()
            .base(base(player, currentChoiceKey, choices, state.search()))
            .type(DialogType.multiAction(buttons(player, currentChoiceKey, visible, page, pageCount, state), null, config.columns())));
    }

    private DialogBase base(Player player, String currentChoiceKey, List<OrderableItemOption> choices, String filter) {
        ItemSelectDialogConfig presentation = config;
        Component title = component(player, presentation.title());
        List<DialogBody> body = new ArrayList<>();
        OrderableItemOption current = findChoice(currentChoiceKey);
        if (current != null) {
            ItemStack preview = preview(player, current);
            if (preview != null) {
                body.add(DialogBody.item(preview, null,
                    presentation.previewDecorations(), presentation.previewTooltip(),
                    presentation.previewWidth(), presentation.previewHeight()));
            }
        }
        if (choices.isEmpty() && !presentation.emptyMessage().isEmpty()) {
            body.add(DialogBody.plainMessage(component(player, presentation.emptyMessage()), presentation.bodyWidth()));
        }

        return DialogBase.builder(title)
            .externalTitle(title)
            .canCloseWithEscape(presentation.canCloseWithEscape())
            .pause(presentation.pause())
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.copyOf(body))
            .inputs(presentation.buttonOrder().contains("search")
                ? List.of(DialogInput.text(INPUT_KEY, component(player, presentation.searchInputLabel()))
                    .width(presentation.searchInputWidth())
                    .labelVisible(presentation.searchInputLabelVisible())
                    .initial(filter)
                    .maxLength(presentation.searchInputMaxLength())
                    .build())
                : List.of())
            .build();
    }

    private List<ActionButton> buttons(Player player, String currentChoiceKey, List<OrderableItemOption> choices,
                                       int page, int pageCount, PickerState state) {
        List<ActionButton> buttons = new ArrayList<>(choices.size() + 5);
        for (String id : config.buttonOrder()) {
            switch (id) {
                case "search" -> buttons.add(searchButton(player));
                case "sort" -> buttons.add(stateButton(player, config.sortButton(),
                    option(manager.guis().labels("item-select.item-sort-options", List.of("A-Z", "Z-A")), state.sortIndex()),
                    (response, audience) -> handleCycle(audience, true)));
                case "filter" -> buttons.add(stateButton(player, config.filterButton(),
                    option(manager.guis().labels("item-select.filter-options", List.of("All", "Blocks", "Tools", "Food",
                        "Combat", "Potions", "Books", "Ingredients", "Utilities")), state.filterIndex()),
                    (response, audience) -> handleCycle(audience, false)));
                case "previous" -> {
                    if (page > 0) {
                        buttons.add(pageButton(player, config.previousButton(), page, pageCount, -1));
                    }
                }
                case "choices" -> {
                    for (OrderableItemOption choice : choices) {
                        buttons.add(choiceButton(player, choice, choice.choiceKey().equals(currentChoiceKey)));
                    }
                }
                case "next" -> {
                    if (page + 1 < pageCount) {
                        buttons.add(pageButton(player, config.nextButton(), page, pageCount, 1));
                    }
                }
                default -> throw new IllegalStateException("Invalid native picker button order: " + id);
            }
        }
        return List.copyOf(buttons);
    }

    private String option(List<String> labels, int index) {
        return index >= 0 && index < labels.size() ? labels.get(index) : "";
    }

    private ActionButton stateButton(Player player, ItemSelectDialogConfig.Button template, String option,
                                     DialogActionCallback action) {
        return ActionButton.create(componentFinal(player, substitute(template.label(), Map.of("option", option))),
            tooltipFinal(player, substitute(template.tooltip(), Map.of("option", option))), template.width(),
            DialogAction.customClick(action, CACHED_CALLBACK_OPTIONS));
    }

    private ActionButton pageButton(Player player, ItemSelectDialogConfig.Button template,
                                    int page, int pageCount, int delta) {
        Map<String, String> values = Map.of("page", Integer.toString(page + 1),
            "max_page", Integer.toString(pageCount));
        return ActionButton.create(componentFinal(player, substitute(template.label(), values)),
            tooltipFinal(player, substitute(template.tooltip(), values)), template.width(),
            DialogAction.customClick((response, audience) -> handlePage(audience, delta), CACHED_CALLBACK_OPTIONS));
    }

    private String substitute(String template, Map<String, String> values) {
        return substitute(template, values, manager.messages()::resolveOwnerTokens);
    }

    static String substitute(String template, Map<String, String> values,
                             Function<String, String> resolveOwnerTokens) {
        return me.foesio.foOrders.util.TextFormat.applyPlaceholders(
            resolveOwnerTokens.apply(template == null ? "" : template), values);
    }

    private ActionButton searchButton(Player player) {
        return ActionButton.create(component(player, config.searchButtonLabel()),
            tooltip(player, config.searchButtonTooltip()), config.searchButtonWidth(),
            DialogAction.customClick(this::handleSearch, CACHED_CALLBACK_OPTIONS));
    }

    private ActionButton choiceButton(Player player, OrderableItemOption choice, boolean current) {
        ItemSelectDialogConfig presentation = config;
        String labelTemplate = choice.isCustom() ? presentation.customChoiceLabel() : presentation.choiceLabel();
        String label = me.foesio.foOrders.util.TextFormat.applyPlaceholders(
            manager.messages().resolveOwnerTokens(labelTemplate), Map.of(
                "color", manager.messages().resolveOwnerTokens(current
                    ? presentation.currentChoiceColor() : presentation.otherChoiceColor()),
                "item", choice.displayName(),
                "custom_id", choice.customItemId() == null ? "" : choice.customItemId(),
                "material", choice.material().name().toLowerCase(Locale.ROOT)
            ));
        return ActionButton.create(componentFinal(player, label),
            tooltip(player, current ? presentation.currentChoiceTooltip() : presentation.otherChoiceTooltip()),
            presentation.choiceButtonWidth(), DialogAction.customClick(
            (response, audience) -> handleSelect(audience, choice.choiceKey()),
            CACHED_CALLBACK_OPTIONS
        ));
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
        String filter = normalizeFilter(readText(view));
        runForPlayer(playerId, audience, currentPlayer -> {
            manager.playSound(currentPlayer, OrdersMenuManager.GUI_SEARCH_SOUND);
            reopenOrFallback(currentPlayer, pending,
                new PickerState(filter, 0, pending.state().sortIndex(), pending.state().filterIndex()));
        });
    }

    private void handleCycle(Audience audience, boolean sort) {
        if (!(audience instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingSelection pending = pendingSelections.get(playerId);
        if (pending == null) {
            return;
        }
        PickerState state = pending.state();
        int sortCount = manager.guis().labels("item-select.item-sort-options", List.of("A-Z", "Z-A")).size();
        int filterCount = manager.guis().labels("item-select.filter-options", List.of("All", "Blocks", "Tools", "Food",
            "Combat", "Potions", "Books", "Ingredients", "Utilities")).size();
        PickerState next = sort
            ? new PickerState(state.search(), 0, (state.sortIndex() + 1) % Math.max(1, sortCount), state.filterIndex())
            : new PickerState(state.search(), 0, state.sortIndex(), (state.filterIndex() + 1) % Math.max(1, filterCount));
        runForPlayer(playerId, audience, currentPlayer -> {
            manager.playSoundWithPitchVariation(currentPlayer, sort
                ? OrdersMenuManager.GUI_SORT_SOUND : OrdersMenuManager.GUI_FILTER_SOUND);
            reopenOrFallback(currentPlayer, pending, next);
        });
    }

    private void handlePage(Audience audience, int delta) {
        if (!(audience instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingSelection pending = pendingSelections.get(playerId);
        if (pending == null) {
            return;
        }
        PickerState state = pending.state();
        PickerState next = new PickerState(state.search(), Math.max(0, state.page() + delta),
            state.sortIndex(), state.filterIndex());
        runForPlayer(playerId, audience, currentPlayer -> {
            manager.playSound(currentPlayer, delta < 0
                ? OrdersMenuManager.GUI_PAGE_PREVIOUS_SOUND : OrdersMenuManager.GUI_PAGE_NEXT_SOUND);
            reopenOrFallback(currentPlayer, pending, next);
        });
    }

    private void reopenOrFallback(Player player, PendingSelection pending, PickerState state) {
        if (open(player, pending.currentChoiceKey(), state, pending.onSelect(), pending.onFallback())) {
            return;
        }
        pendingSelections.remove(player.getUniqueId(), pending);
        rememberPickerState(player, state);
        if (pending.onFallback() != null) {
            pending.onFallback().run();
        }
    }

    private void rememberPickerState(Player player, PickerState state) {
        ItemSelectState inventoryState = manager.menuStates
            .computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState())
            .getOrCreateItemSelectState();
        copyToInventoryState(inventoryState, state.search(), state.sortIndex(), state.filterIndex());
    }

    static void copyToInventoryState(ItemSelectState target, String search, int sortIndex, int filterIndex) {
        target.search = search == null ? "" : search;
        target.sortIndex = Math.max(0, sortIndex);
        target.filterIndex = Math.max(0, filterIndex);
        target.page = 1;
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
            runForPlayer(playerId, audience, currentPlayer -> {
                manager.playSound(currentPlayer, OrdersMenuManager.GUI_ERROR_SOUND);
                manager.interactionSupport.sendErrorActionbar(currentPlayer,
                    manager.messages().get("actionbar.action-unavailable"));
                rememberPickerState(currentPlayer, pending.state());
                if (pending.onFallback() != null) {
                    pending.onFallback().run();
                }
            });
            return;
        }
        runForPlayer(playerId, audience, currentPlayer -> {
            rememberPickerState(currentPlayer, pending.state());
            pending.onSelect().accept(selected);
        });
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

    private ItemStack preview(Player player, OrderableItemOption option) {
        return manager.itemSupport.createOrderableSelectItem(player, option);
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

    private Component component(Player player, String text) {
        return componentFinal(player, manager.messages().resolveOwnerTokens(text));
    }

    private Component componentFinal(Player player, String text) {
        return DialogIcons.inlineTokens(player, LEGACY.deserialize(FoText.color(text)));
    }

    private Component tooltip(Player player, String raw) {
        return raw == null || raw.isBlank() ? null : component(player, raw);
    }

    private Component tooltipFinal(Player player, String raw) {
        return raw == null || raw.isBlank() ? null : componentFinal(player, raw);
    }

    private String normalizeFilter(String filter) {
        return filter == null ? "" : filter.trim();
    }

    @Override
    public void reloadConfig() {
        loadConfig(false);
    }

    private void loadConfig(boolean startup) {
        File file = new File(manager.plugin.getDataFolder(), "dialogs" + File.separator + "item-select.yml");
        try {
            ItemSelectDialogConfig loaded = ItemSelectDialogConfig.load(file.isFile()
                ? OwnerYaml.load(file) : new YamlConfiguration());
            config = loaded;
            dialogCache.clear();
        } catch (IllegalStateException malformed) {
            if (!startup) throw malformed;
            manager.plugin.getLogger().warning("Unparseable owner dialogs/item-select.yml; "
                + "native item selection is unavailable until repaired: " + malformed.getMessage());
        }
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
        PickerState state,
        Consumer<OrderableItemOption> onSelect,
        Runnable onFallback
    ) {
    }

    record PickerState(String search, int page, int sortIndex, int filterIndex) {
    }

    private record DialogCacheKey(UUID viewerId, int contentRevision, String currentChoiceKey, PickerState state) {
    }

}
