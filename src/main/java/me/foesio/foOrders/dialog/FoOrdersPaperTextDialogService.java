package me.foesio.foOrders.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Paper-only path, reflectively instantiated so Spigot never loads Paper dialog classes. */
public final class FoOrdersPaperTextDialogService implements FoOrdersTextDialogService {
    private static final String INPUT_KEY = "value";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1).lifetime(Duration.ofMinutes(5)).build();

    private final FoOrders plugin;
    private final NativeDialogSupport support;
    private final FoScheduler scheduler;

    public FoOrdersPaperTextDialogService(FoOrders plugin, NativeDialogSupport support, FoScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.support = Objects.requireNonNull(support, "support");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean open(Player player, DialogTextPresentation presentation,
                        Consumer<String> onSubmit, Runnable onCancel) {
        if (player == null || !player.isOnline() || presentation == null || !presentation.nativeReady()
            || !support.canUseNativeDialogs(player)) {
            return false;
        }
        try {
            String rawTitle = presentation.titleIcon().isBlank() ? presentation.title()
                : DialogIcons.withIcon(presentation.title(), presentation.titleIcon());
            Component title = component(player, rawTitle);
            List<DialogBody> body = presentation.body().stream()
                .map(line -> (DialogBody) DialogBody.plainMessage(component(player, line), presentation.bodyWidth()))
                .toList();
            List<ActionButton> buttons = new ArrayList<>(2);
            for (String key : presentation.buttonOrder()) {
                boolean confirm = "confirm".equals(key);
                DialogTextPresentation.Button visual = confirm ? presentation.confirm() : presentation.cancel();
                DialogAction action = DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player viewer) || !viewer.getUniqueId().equals(player.getUniqueId())) {
                        return;
                    }
                    String input = view == null ? "" : Objects.toString(view.getText(INPUT_KEY), "");
                    scheduler.runForPlayer(viewer, () -> {
                        if (confirm) {
                            onSubmit.accept(input);
                        } else {
                            onCancel.run();
                        }
                    });
                }, CALLBACK_OPTIONS);
                String label = visual.icon().isBlank() ? visual.label()
                    : DialogIcons.withIcon(visual.label(), visual.icon());
                buttons.add(ActionButton.create(component(player, label),
                    visual.tooltip().isBlank() ? null : component(player, visual.tooltip()),
                    visual.width(), action));
            }
            Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                    .externalTitle(title)
                    .canCloseWithEscape(presentation.canCloseWithEscape())
                    .pause(presentation.pause())
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(body)
                    .inputs(List.of(DialogInput.text(INPUT_KEY, component(player, presentation.fieldLabel()))
                        .width(presentation.inputWidth())
                        .labelVisible(presentation.labelVisible())
                        .initial(presentation.initialValue())
                        .maxLength(presentation.maxLength())
                        .build()))
                    .build())
                .type(DialogType.multiAction(buttons, null, presentation.columns())));
            ((Audience) player).showDialog(dialog);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            support.disableForSession("FoOrders text dialog failed: " + exception.getMessage());
            plugin.getLogger().warning("Native text dialog unavailable; using chat fallback: " + exception.getMessage());
            return false;
        }
    }

    private Component component(Player player, String raw) {
        return DialogIcons.inlineTokens(player, LEGACY.deserialize(
            FoText.color(raw)));
    }
}
