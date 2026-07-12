package me.foesio.foOrders.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.text.FoText;
import me.foesio.foOrders.FoOrders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class FoOrdersPaperEnchantDialogService implements FoOrdersEnchantDialogService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(5))
        .build();

    private final FoOrders plugin;
    private final NativeDialogSupport support;
    private final FoScheduler scheduler;
    private final EnchantDialogConfig config;

    public FoOrdersPaperEnchantDialogService(
        FoOrders plugin,
        NativeDialogSupport support,
        FoScheduler scheduler,
        EnchantDialogConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.support = Objects.requireNonNull(support, "support");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public boolean open(Player player, EnchantDialogRequest request, Consumer<EnchantDialogAction> onAction) {
        if (!config.enabled() || !support.canUseNativeDialogs() || player == null || !player.isOnline() || request == null) {
            return false;
        }

        try {
            Dialog dialog = Dialog.create(factory -> {
                DialogRegistryEntry.Builder builder = factory.empty();
                builder.base(base(request));
                builder.type(DialogType.multiAction(actions(player, request, onAction), null, config.columns()));
            });
            ((Audience) player).showDialog(dialog);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            support.disableForSession("FoOrders enchant dialog failed: " + exception.getMessage());
            String message = "Native enchant dialog failed. Falling back to inventory menu: " + exception.getMessage();
            plugin.getLogger().warning(message);
            if (plugin.fileLogger() != null) {
                plugin.fileLogger().warn(message);
            }
            return false;
        }
    }

    private DialogBase base(EnchantDialogRequest request) {
        List<DialogBody> body = new ArrayList<>();
        ItemStack displayItem = request.displayItem() == null
            ? new ItemStack(request.material())
            : request.displayItem().clone();
        displayItem.setAmount(1);
        body.add(DialogBody.item(
            displayItem,
            null,
            config.showItemDecorations(),
            config.showItemTooltip(),
            config.itemWidth(),
            config.itemHeight()
        ));

        Component title = title();
        return DialogBase.builder(title)
            .externalTitle(title)
            .canCloseWithEscape(config.canCloseWithEscape())
            .pause(config.pause())
            // Level buttons reopen this dialog; CLOSE can restore the previous item-select inventory after the callback.
            .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
            .body(body)
            .inputs(List.of())
            .build();
    }

    private List<ActionButton> actions(Player player, EnchantDialogRequest request, Consumer<EnchantDialogAction> onAction) {
        List<ActionButton> actions = new ArrayList<>();
        for (EnchantDialogRow row : request.rows()) {
            actions.add(enchantButton(player, row, onAction));
            for (int level = 1; level <= config.maxLevelColumns(); level++) {
                actions.add(level <= row.maxLevel()
                    ? levelButton(player, row, level, onAction)
                    : spacerButton());
            }
        }

        Map<String, String> placeholders = Map.of(
            "item", safe(request.itemName()),
            "selected", String.valueOf(request.selectedCount())
        );
        actions.add(button(config.backButton(), placeholders, action(player, onAction, EnchantDialogAction.backToItems())));
        actions.add(button(config.continueButton(), placeholders, action(player, onAction, EnchantDialogAction.continueOrder())));
        return actions;
    }

    private ActionButton enchantButton(Player player, EnchantDialogRow row, Consumer<EnchantDialogAction> onAction) {
        EnchantDialogConfig.EnchantButton button = config.enchantButton();
        String color = row.cursed()
            ? button.curseColor()
            : row.selectedLevel() > 0 ? button.selectedColor() : button.color();
        Map<String, String> placeholders = enchantPlaceholders(row, Math.max(1, row.selectedLevel()), color);
        return ActionButton.create(
            component(replace(button.label(), placeholders)),
            tooltip(button.tooltip(), placeholders),
            button.width(),
            action(player, onAction, EnchantDialogAction.setLevel(row.enchantmentKey(), row.selectedLevel() > 0 ? 0 : 1))
        );
    }

    private ActionButton levelButton(Player player, EnchantDialogRow row, int level, Consumer<EnchantDialogAction> onAction) {
        EnchantDialogConfig.LevelButton button = config.levelButton();
        String color = row.selectedLevel() == level ? button.selectedColor() : button.color();
        Map<String, String> placeholders = enchantPlaceholders(row, level, color);
        return ActionButton.create(
            component(replace(button.label(), placeholders)),
            tooltip(button.tooltip(), placeholders),
            button.width(),
            action(player, onAction, EnchantDialogAction.setLevel(row.enchantmentKey(), level))
        );
    }

    private ActionButton spacerButton() {
        return button(config.spacerButton(), Map.of(), null);
    }

    private ActionButton button(EnchantDialogConfig.Button button, Map<String, String> placeholders, DialogAction action) {
        String label = replace(button.label(), placeholders);
        if (button.icon() != null && !button.icon().isBlank()) {
            label = DialogIcons.withIcon(label, button.icon());
        }
        return ActionButton.create(
            component(label),
            tooltip(button.tooltip(), placeholders),
            button.width(),
            action
        );
    }

    private DialogAction action(Player player, Consumer<EnchantDialogAction> onAction, EnchantDialogAction action) {
        UUID playerId = player.getUniqueId();
        return DialogAction.customClick(
            (response, audience) -> runForPlayer(playerId, audience, () -> onAction.accept(action)),
            CALLBACK_OPTIONS
        );
    }

    private void runForPlayer(UUID playerId, Audience audience, Runnable action) {
        if (action == null || !(audience instanceof Player player) || !player.getUniqueId().equals(playerId)) {
            return;
        }
        scheduler.runForPlayer(player, () -> {
            if (plugin.isEnabled() && player.isOnline() && player.getUniqueId().equals(playerId)) {
                action.run();
            }
        });
    }

    private Component title() {
        String title = config.title();
        if (config.titleIcon() != null && !config.titleIcon().isBlank()) {
            title = DialogIcons.withIcon(title, config.titleIcon());
        }
        return component(title);
    }

    private Component component(String text) {
        return DialogIcons.inlineTokens(LEGACY.deserialize(FoText.color(safe(text))));
    }

    private Component tooltip(String rawTooltip, Map<String, String> placeholders) {
        if (rawTooltip == null || rawTooltip.isBlank()) {
            return null;
        }
        return component(replace(rawTooltip, placeholders));
    }

    private Map<String, String> enchantPlaceholders(EnchantDialogRow row, int level, String color) {
        return Map.of(
            "enchant", safe(row.name()),
            "enchantment", safe(row.name()),
            "level", String.valueOf(level),
            "roman", roman(level),
            "color", safe(color)
        );
    }

    private String replace(String text, Map<String, String> placeholders) {
        String formatted = safe(text);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = safe(entry.getValue());
            formatted = formatted
                .replace("{" + entry.getKey() + "}", value)
                .replace("%" + entry.getKey() + "%", value);
        }
        return formatted;
    }

    private String roman(int value) {
        int normalized = Math.max(1, value);
        int[] numbers = new int[]{1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = new String[]{"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = normalized;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                remaining -= numbers[i];
                result.append(numerals[i]);
            }
        }
        return result.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
