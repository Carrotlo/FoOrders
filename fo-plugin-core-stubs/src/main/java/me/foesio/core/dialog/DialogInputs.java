package me.foesio.core.dialog;

import me.foesio.core.scheduler.FoScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public class DialogInputs {
    private DialogInputs() {}

    public static DialogInputs create(Plugin plugin, NativeDialogSupport support, FoScheduler scheduler) {
        return new DialogInputs();
    }

    public TextInput textInput() {
        return new TextInput();
    }

    public void clear(Player player) {}

    public void close() {}

    public static class TextInput {
        public void openTextInput(
            Player player,
            TextDialogRequest request,
            Consumer<String> onSubmit,
            Runnable onCancel
        ) {}
    }
}
