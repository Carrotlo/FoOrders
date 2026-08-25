package me.foesio.core;

import me.foesio.core.dialog.DialogService;
import me.foesio.core.inventory.InventoryCloseSuppressor;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.update.UpdateNoticeService;
import org.bukkit.plugin.java.JavaPlugin;

public class FoCoreContext {
    private final JavaPlugin plugin;

    public FoCoreContext() {
        this(null);
    }

    public FoCoreContext(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public FoScheduler scheduler() {
        return new FoScheduler(plugin);
    }

    public void warnIfNativeDialogsUnavailable() {}

    public boolean supportsDialogSprites() {
        return false;
    }

    public DialogSpriteSupport dialogSpriteSupport() {
        return new DialogSpriteSupport();
    }

    public void metrics(int pluginId) {}

    public DialogService dialogService() {
        return new DialogService();
    }

    public InventoryCloseSuppressor inventoryCloseSuppressor() {
        return new InventoryCloseSuppressor();
    }

    public InventoryDepositService inventoryDeposits() {
        return new InventoryDepositService();
    }

    public UpdateNoticeService createUpdateNotices(UpdateNoticeService.UpdateMessenger messenger, String projectId) {
        return new UpdateNoticeService();
    }

    public void close() {}
}
