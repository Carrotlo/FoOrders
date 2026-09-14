package me.foesio.foOrders.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Lets another plugin route items into the open orders FoOrders is holding,
 * without opening the deliver menu.
 *
 * <p>This is deliberately written so a caller can use it purely through
 * reflection: every parameter and return type is a Bukkit or JDK type, so the
 * caller never needs FoOrders on its compile classpath. Get an instance with
 * {@code ((FoOrders) Bukkit.getPluginManager().getPlugin("FoOrders")).orderFillApi()},
 * or from Bukkit's services manager, which FoOrders registers this under.
 *
 * <p>Every method matches the deliver menu's rules: cancelled orders and orders
 * that are already fully delivered are skipped, an item has to satisfy the
 * order's material, custom item template and required enchantments, and the
 * seller's own orders are skipped unless the caller opts in.
 */
public interface FoOrdersOrderFillApi {

    /** Bumped whenever the shape of this interface changes. */
    int API_VERSION = 1;

    /**
     * The version of this API the running FoOrders implements. Callers binding
     * through reflection should check it before trusting the other methods.
     */
    int apiVersion();

    /**
     * Every open order {@code sample} could be delivered into, best paying
     * first, without changing anything.
     *
     * <p>The result is flat so it survives reflection across plugin class
     * loaders: {@code [orderCount, pricePerItem0, unitsStillNeeded0,
     * pricePerItem1, unitsStillNeeded1, ...]}. An empty array means FoOrders
     * could not answer (no economy provider, say); a {@code [0]} array means
     * there is simply nothing matching.
     *
     * @param seller            the player who would deliver the items
     * @param sample            the item to match against; its stack size is ignored
     * @param minPricePerItem   orders paying less than this per item are skipped
     * @param includeOwnOrders  whether the seller's own orders may be filled
     */
    double[] openOrderQuotes(Player seller, ItemStack sample, double minPricePerItem, boolean includeOwnOrders);

    /**
     * Delivers up to {@code amount} units of {@code sample} into open orders,
     * best paying first, and pays the seller for what was accepted.
     *
     * <p>The caller is responsible for taking the accepted items out of the
     * seller's inventory afterwards - FoOrders never touches their inventory
     * here. Orders are locked while they are being filled, exactly as the
     * deliver menu locks them, so a partially completed fill is possible when
     * somebody else is delivering into the same order at that moment.
     *
     * <p>Returns {@code [unitsAccepted, totalPayout, ordersCompleted]}, where
     * {@code ordersCompleted} counts orders this call finished off.
     *
     * @param seller            the player delivering the items, who gets paid
     * @param sample            the item to match against; its stack size is ignored
     * @param amount            how many units the caller is offering
     * @param minPricePerItem   orders paying less than this per item are skipped
     * @param includeOwnOrders  whether the seller's own orders may be filled
     */
    double[] fillOrders(Player seller, ItemStack sample, int amount, double minPricePerItem, boolean includeOwnOrders);
}
