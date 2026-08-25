package me.foesio.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public class FoScheduler {
    private final JavaPlugin plugin;
    private final boolean folia;

    public FoScheduler() {
        this(null);
    }

    public FoScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public void runForPlayer(Player player, Runnable task) {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (folia) {
            runEntityTask(player, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runLaterForPlayer(Player player, Runnable task, long delayTicks) {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (folia) {
            runEntityTaskDelayed(player, task, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private void runEntityTask(Entity entity, Runnable task) {
        try {
            Object entityScheduler = Entity.class.getMethod("getScheduler").invoke(entity);
            Method runMethod = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            Consumer<Object> wrappedTask = scheduledTask -> task.run();
            runMethod.invoke(entityScheduler, plugin, wrappedTask, null);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity scheduler call failed: " + e.getMessage());
        }
    }

    private void runEntityTaskDelayed(Entity entity, Runnable task, long delayTicks) {
        try {
            Object entityScheduler = Entity.class.getMethod("getScheduler").invoke(entity);
            Method runDelayedMethod = entityScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            Consumer<Object> wrappedTask = scheduledTask -> task.run();
            runDelayedMethod.invoke(entityScheduler, plugin, wrappedTask, null, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity scheduler delayed call failed: " + e.getMessage());
        }
    }
}
