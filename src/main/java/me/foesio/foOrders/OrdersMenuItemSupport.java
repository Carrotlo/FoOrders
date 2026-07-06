package me.foesio.foOrders;

import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.TextFormat;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.number.NumberFormatters;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuItemSupport {
    private final OrdersMenuManager manager;
    private final CustomItemStore customItemStore;
    private final PlayerDataStore playerDataStore;

    OrdersMenuItemSupport(OrdersMenuManager manager) {
        this.manager = manager;
        this.customItemStore = manager.customItemStore;
        this.playerDataStore = manager.playerDataStore;
    }

    ItemStack createOrderItem(String ownerName, PlayerDataStore.OrderEntry order, boolean showAdminModerationLore) {
        ItemStack item = createOrderStack(order, 1);
        Material icon = item.getType();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ACCENT + ownerName + "'s Order");

        String materialName = order.getCustomItemId() == null
            ? formatMaterialName(icon)
            : resolveTemplateDisplayName(item);
        String amount = formatCompactAmount(order.getAmountOrdered());
        String delivered = formatCompactAmount(order.getAmountDelivered());
        String amountLeft = formatCompactAmount(Math.max(0, order.getAmountOrdered() - order.getAmountDelivered()));
        String price = formatCompactAmount(order.getPricePerItem());
        String total = formatCompactAmount(order.getTotalCost());
        String paid = formatCompactAmount(order.getAmountPaid());
        String paidLeft = formatCompactAmount(Math.max(0D, order.getTotalCost() - order.getAmountPaid()));
        Map<String, String> placeholders = TextFormat.placeholders(
            "owner", ownerName,
            "item", materialName,
            "material", materialName,
            "amount", amount,
            "amount_ordered", amount,
            "delivered", delivered,
            "amount_delivered", delivered,
            "amount_left", amountLeft,
            "price", price,
            "price_each", price,
            "total", total,
            "paid", paid,
            "paid_left", paidLeft
        );

        meta.setDisplayName(guiText(
            "items.main.order-entry.name",
            ACCENT + ownerName + "'s Order",
            placeholders
        ));

        List<String> lore = new ArrayList<>();
        lore.add(guiText(
            "items.main.order-entry.lore.amount",
            ACCENT + amount + " " + WHITE + materialName,
            placeholders
        ));
        lore.add(guiText(
            "items.main.order-entry.lore.price",
            ACCENT + "$" + price + " " + WHITE + "each",
            placeholders
        ));
        if (!order.getEnchantments().isEmpty()) {
            lore.add(guiText(
                "items.main.order-entry.lore.enchants-title",
                MUTED + "Enchants",
                placeholders
            ));
            lore.addAll(buildEnchantmentSummaryLore(order.getEnchantments(), 3));
        }
        lore.add("");
        lore.add(guiText(
            "items.main.order-entry.lore.delivered",
            PROGRESS_LEFT + delivered + "/" + PROGRESS_RIGHT + amount + " " + PROGRESS_LABEL + "Delivered",
            placeholders
        ));
        lore.add(guiText(
            "items.main.order-entry.lore.paid",
            PROGRESS_LEFT + "$" + paid + "/" + PROGRESS_RIGHT + "$" + total + " " + PROGRESS_LABEL + "Paid",
            placeholders
        ));
        if (showAdminModerationLore) {
            lore.add("");
            lore.add(CANCEL_RED + "Shift + Left Click: Cancel this order");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String guiText(String path, String fallback, Map<String, String> placeholders) {
        return manager.guis().text(path, fallback, placeholders);
    }

    ItemStack createCyclingItem(Material material, String name, List<String> options, int selectedIndex) {
        return createCyclingItem(material, name, options, selectedIndex, ACCENT, WHITE);
    }

    ItemStack createCyclingItem(
        Material material,
        String name,
        List<String> options,
        int selectedIndex,
        String accentColor,
        String defaultColor
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(accentColor + name);
        List<String> lore = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String color = i == selectedIndex ? accentColor : defaultColor;
            lore.add(color + "• " + options.get(i));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createSimpleItem(Material material, String displayName, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(displayName);
        meta.setLore(loreLines);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createOrderStack(PlayerDataStore.OrderEntry order, int amount) {
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(order.getCustomItemId());
        Material material;
        ItemStack item;
        if (customDefinition != null) {
            item = customDefinition.template().clone();
            material = item.getType();
        } else {
            material = resolveMaterial(order.getMaterial());
            item = new ItemStack(material);
        }

        item.setAmount(Math.max(1, amount));
        applyOrderEnchantments(item, sanitizeDraftEnchantments(order.getCustomItemId(), material, order.getEnchantments()));
        return item;
    }

    void applyOrderEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        if (enchantments.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (item.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Map.Entry<String, Integer> enchantEntry : enchantments.entrySet()) {
                Enchantment enchantment = resolveEnchantment(enchantEntry.getKey());
                if (enchantment == null) {
                    continue;
                }
                storageMeta.addStoredEnchant(enchantment, enchantEntry.getValue(), true);
            }
            item.setItemMeta(storageMeta);
            return;
        }

        for (Map.Entry<String, Integer> enchantEntry : enchantments.entrySet()) {
            Enchantment enchantment = resolveEnchantment(enchantEntry.getKey());
            if (enchantment == null) {
                continue;
            }
            meta.addEnchant(enchantment, enchantEntry.getValue(), true);
        }
        item.setItemMeta(meta);
    }

    boolean matchesOrderedItem(ItemStack item, PlayerDataStore.OrderEntry order) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material wantedMaterial;
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(order.getCustomItemId());
        if (customDefinition != null) {
            ItemStack template = customDefinition.template().clone();
            template.setAmount(1);
            wantedMaterial = template.getType();
            if (item.getType() != wantedMaterial) {
                return false;
            }
            if (!matchesCustomTemplateMeta(item, template)) {
                return false;
            }
        } else {
            wantedMaterial = resolveMaterial(order.getMaterial());
            if (item.getType() != wantedMaterial) {
                return false;
            }
        }

        Map<String, Integer> requiredEnchantments =
            sanitizeDraftEnchantments(order.getCustomItemId(), wantedMaterial, order.getEnchantments());
        if (requiredEnchantments.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Integer> required : requiredEnchantments.entrySet()) {
            Enchantment enchantment = resolveEnchantment(required.getKey());
            if (enchantment == null) {
                return false;
            }
            if (getAppliedEnchantmentLevel(item, enchantment) < required.getValue()) {
                return false;
            }
        }
        return true;
    }

    int getAppliedEnchantmentLevel(ItemStack item, Enchantment enchantment) {
        int directLevel = item.getEnchantmentLevel(enchantment);
        if (item.getType() != Material.ENCHANTED_BOOK) {
            return directLevel;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return Math.max(directLevel, storageMeta.getStoredEnchantLevel(enchantment));
        }
        return directLevel;
    }

    boolean matchesCustomTemplateMeta(ItemStack providedItem, ItemStack templateItem) {
        if (providedItem == null || templateItem == null) {
            return false;
        }
        if (providedItem.getType() != templateItem.getType()) {
            return false;
        }

        ItemMeta providedMeta = providedItem.getItemMeta();
        ItemMeta templateMeta = templateItem.getItemMeta();
        if (templateMeta == null) {
            return providedMeta == null;
        }
        if (providedMeta == null) {
            return false;
        }

        if (templateMeta.hasDisplayName() != providedMeta.hasDisplayName()) {
            return false;
        }
        if (templateMeta.hasDisplayName() && !Objects.equals(templateMeta.getDisplayName(), providedMeta.getDisplayName())) {
            return false;
        }

        if (templateMeta.hasLore() != providedMeta.hasLore()) {
            return false;
        }
        if (templateMeta.hasLore() && !Objects.equals(templateMeta.getLore(), providedMeta.getLore())) {
            return false;
        }

        if (templateMeta.hasCustomModelData() != providedMeta.hasCustomModelData()) {
            return false;
        }
        if (templateMeta.hasCustomModelData() && !Objects.equals(templateMeta.getCustomModelData(), providedMeta.getCustomModelData())) {
            return false;
        }

        if (templateMeta.isUnbreakable() != providedMeta.isUnbreakable()) {
            return false;
        }

        Map<Enchantment, Integer> requiredTemplateEnchantments = getItemEnchantments(templateItem);
        for (Map.Entry<Enchantment, Integer> required : requiredTemplateEnchantments.entrySet()) {
            if (getAppliedEnchantmentLevel(providedItem, required.getKey()) < required.getValue()) {
                return false;
            }
        }
        return true;
    }

    Map<Enchantment, Integer> getItemEnchantments(ItemStack item) {
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        if (item == null) {
            return enchantments;
        }

        enchantments.putAll(item.getEnchantments());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Map.Entry<Enchantment, Integer> entry : storageMeta.getStoredEnchants().entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = Math.max(entry.getValue(), enchantments.getOrDefault(enchantment, 0));
                enchantments.put(enchantment, level);
            }
        }
        return enchantments;
    }

    boolean supportsOrderEnchantments(String customItemId, Material material) {
        if (customItemId == null || customItemId.isBlank()) {
            return supportsOrderEnchantments(material);
        }
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(customItemId);
        return customDefinition != null
            && customDefinition.allowOrderEnchants()
            && supportsOrderEnchantments(material);
    }

    boolean supportsOrderEnchantments(Material material) {
        if (material == Material.ENCHANTED_BOOK) {
            return true;
        }
        ItemStack probe = new ItemStack(material);
        for (Enchantment enchantment : ALL_ENCHANTMENTS) {
            if (enchantment.canEnchantItem(probe)) {
                return true;
            }
        }
        return false;
    }

    List<Enchantment> getSelectableEnchantments(Material material) {
        if (!supportsOrderEnchantments(material)) {
            return List.of();
        }

        ItemStack probe = new ItemStack(material);
        List<Enchantment> selectable = new ArrayList<>();
        for (Enchantment enchantment : ALL_ENCHANTMENTS) {
            if (material == Material.ENCHANTED_BOOK || enchantment.canEnchantItem(probe)) {
                selectable.add(enchantment);
            }
        }
        selectable.sort(Comparator.comparing(this::formatEnchantmentName, String.CASE_INSENSITIVE_ORDER));
        return selectable;
    }

    Map<String, Integer> sanitizeDraftEnchantments(String customItemId, Material material, Map<String, Integer> rawEnchantments) {
        if (!supportsOrderEnchantments(customItemId, material) || rawEnchantments == null || rawEnchantments.isEmpty()) {
            return Map.of();
        }
        return sanitizeDraftEnchantments(material, rawEnchantments);
    }

    Map<String, Integer> sanitizeDraftEnchantments(Material material, Map<String, Integer> rawEnchantments) {
        if (!supportsOrderEnchantments(material) || rawEnchantments == null || rawEnchantments.isEmpty()) {
            return Map.of();
        }

        ItemStack probe = new ItemStack(material);
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> enchantEntry : rawEnchantments.entrySet()) {
            if (enchantEntry == null) {
                continue;
            }
            Enchantment enchantment = resolveEnchantment(enchantEntry.getKey());
            if (enchantment == null) {
                continue;
            }
            if (material != Material.ENCHANTED_BOOK && !enchantment.canEnchantItem(probe)) {
                continue;
            }

            Integer level = enchantEntry.getValue();
            if (level == null || level <= 0) {
                continue;
            }
            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            normalized.put(enchantment.getKey().toString(), Math.max(1, Math.min(maxLevel, level)));
        }
        return normalized.isEmpty() ? Map.of() : normalized;
    }

    Enchantment resolveEnchantment(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        NamespacedKey namespacedKey = NamespacedKey.fromString(rawKey);
        if (namespacedKey == null) {
            String normalized = rawKey.toLowerCase(Locale.ROOT);
            namespacedKey = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        }
        if (namespacedKey == null) {
            return null;
        }
        return Enchantment.getByKey(namespacedKey);
    }

    ItemStack createEnchantSelectionItem(Material material, Enchantment enchantment, int selectedLevel) {
        ItemStack item = new ItemStack(selectedLevel > 0 ? Material.ENCHANTED_BOOK : Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        int maxLevel = Math.max(1, enchantment.getMaxLevel());
        String enchantName = formatEnchantmentName(enchantment);
        meta.setDisplayName(ACCENT + enchantName);

        List<String> lore = new ArrayList<>();
        lore.add(
            LIGHT_GRAY + "Current: " + ACCENT
                + (selectedLevel > 0 ? toRomanNumeral(selectedLevel) + WHITE + " (" + selectedLevel + ")" : "None")
        );
        lore.add(LIGHT_GRAY + "Max: " + ACCENT + toRomanNumeral(maxLevel) + WHITE + " (" + maxLevel + ")");
        lore.add(MUTED + "For " + formatMaterialName(material));
        lore.add("");
        lore.add(WHITE + "Left click: +1 level");
        lore.add(WHITE + "Right click: -1 level");
        lore.add(WHITE + "Shift + Left: +5 levels");
        lore.add(WHITE + "Shift + Right: -5 levels");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    List<String> buildEnchantmentSummaryLore(Map<String, Integer> enchantments, int maxLines) {
        if (enchantments == null || enchantments.isEmpty() || maxLines <= 0) {
            return List.of();
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(enchantments.entrySet());
        entries.sort(Comparator.comparing(entry -> formatEnchantmentName(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        List<String> summary = new ArrayList<>();
        int shown = Math.min(maxLines, entries.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Integer> enchantEntry = entries.get(i);
            int level = Math.max(1, enchantEntry.getValue());
            summary.add(
                LIGHT_GRAY + "- " + formatEnchantmentName(enchantEntry.getKey()) + " "
                    + toRomanNumeral(level) + " (" + level + ")"
            );
        }

        int hidden = entries.size() - shown;
        if (hidden > 0) {
            summary.add(LIGHT_GRAY + "And " + hidden + " more");
        }
        return summary;
    }

    String formatOrderDisplayName(PlayerDataStore.OrderEntry order) {
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(order.getCustomItemId());
        Material material = customDefinition == null ? resolveMaterial(order.getMaterial()) : customDefinition.template().getType();
        String baseName = customDefinition == null ? formatMaterialName(material) : resolveTemplateDisplayName(customDefinition.template());
        Map<String, Integer> normalizedEnchantments =
            sanitizeDraftEnchantments(order.getCustomItemId(), material, order.getEnchantments());
        return appendEnchantmentsToName(baseName, normalizedEnchantments);
    }

    String formatOrderDisplayName(Material material, Map<String, Integer> enchantments) {
        Map<String, Integer> normalizedEnchantments = sanitizeDraftEnchantments(material, enchantments);
        return appendEnchantmentsToName(formatMaterialName(material), normalizedEnchantments);
    }

    String appendEnchantmentsToName(String baseName, Map<String, Integer> normalizedEnchantments) {
        if (normalizedEnchantments.isEmpty()) {
            return baseName;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(normalizedEnchantments.entrySet());
        entries.sort(Comparator.comparing(entry -> formatEnchantmentName(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        List<String> parts = new ArrayList<>();
        int shown = Math.min(2, entries.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Integer> enchantEntry = entries.get(i);
            int level = Math.max(1, enchantEntry.getValue());
            parts.add(formatEnchantmentName(enchantEntry.getKey()) + " " + toRomanNumeral(level));
        }
        if (entries.size() > shown) {
            parts.add("+" + (entries.size() - shown));
        }
        return baseName + " (" + String.join(", ", parts) + ")";
    }

    boolean isOrderBlacklisted(Material material, Map<String, Integer> enchantments) {
        if (manager.blacklistedOrderMaterials.contains(material)) {
            return true;
        }
        if (manager.blacklistedOrderNames.isEmpty()) {
            return false;
        }

        String plainName = normalizeBlacklistedName(formatMaterialName(material));
        if (manager.blacklistedOrderNames.contains(plainName)) {
            return true;
        }

        String decoratedName = normalizeBlacklistedName(formatOrderDisplayName(material, enchantments));
        return manager.blacklistedOrderNames.contains(decoratedName);
    }

    String normalizeBlacklistedName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String stripped = org.bukkit.ChatColor.stripColor(name);
        String normalized = (stripped == null ? name : stripped)
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('_', ' ')
            .replaceAll("\\s+", " ");
        return normalized;
    }

    String buildOrderSearchText(PlayerDataStore.OrderEntry order) {
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(order.getCustomItemId());
        Material material = customDefinition == null ? resolveMaterial(order.getMaterial()) : customDefinition.template().getType();
        StringBuilder searchable = new StringBuilder();
        searchable.append(formatMaterialName(material).toLowerCase(Locale.ROOT));
        if (customDefinition != null) {
            searchable
                .append(' ')
                .append(resolveTemplateDisplayName(customDefinition.template()).toLowerCase(Locale.ROOT))
                .append(' ')
                .append(customDefinition.id());
        }

        Map<String, Integer> normalized = sanitizeDraftEnchantments(order.getCustomItemId(), material, order.getEnchantments());
        for (Map.Entry<String, Integer> enchantEntry : normalized.entrySet()) {
            int level = Math.max(1, enchantEntry.getValue());
            String formattedName = formatEnchantmentName(enchantEntry.getKey()).toLowerCase(Locale.ROOT);
            String romanLevel = toRomanNumeral(level).toLowerCase(Locale.ROOT);
            searchable
                .append(' ')
                .append(formattedName)
                .append(' ')
                .append(level)
                .append(' ')
                .append(romanLevel)
                .append(' ')
                .append(formattedName)
                .append(' ')
                .append(romanLevel);
        }
        return searchable.toString();
    }

    String formatEnchantmentName(Enchantment enchantment) {
        return formatEnchantmentName(enchantment.getKey().toString());
    }

    String formatEnchantmentName(String enchantmentKey) {
        if (enchantmentKey == null || enchantmentKey.isBlank()) {
            return "Unknown";
        }

        String normalized = enchantmentKey.toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator < normalized.length() - 1) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }

        String[] rawParts = normalized.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : rawParts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    String toRomanNumeral(int value) {
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

    Inventory createMenu(MenuType menuType, int size, String title) {
        OrdersMenuHolder holder = new OrdersMenuHolder(menuType, manager.guis().revision(), title);
        Inventory menu = Bukkit.createInventory(holder, size, title);
        holder.setInventory(menu);
        return menu;
    }

    void openMenu(Player player, Inventory menu) {
        Inventory currentTopInventory = player.getOpenInventory().getTopInventory();
        boolean currentMenuCanHoldPlayerItems = currentTopInventory.getHolder() instanceof OrdersMenuHolder holder
            && holder.getMenuType() == MenuType.DELIVER;
        if (manager.isOrdersMenu(currentTopInventory) && !currentMenuCanHoldPlayerItems) {
            manager.inventoryCloseSuppressor.suppressNextClose(player);
        }
        player.openInventory(menu);
    }

    String resolvePlayerName(UUID playerId) {
        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        if (playerName == null || playerName.isBlank()) {
            return playerId.toString().substring(0, 8);
        }
        return playerName;
    }

    List<CustomItemStore.CustomItemDefinition> getSortedCustomItems() {
        List<CustomItemStore.CustomItemDefinition> customItems = customItemStore.listAll();
        customItems.sort((left, right) -> {
            String leftName = resolveTemplateDisplayName(left.template());
            String rightName = resolveTemplateDisplayName(right.template());
            int compare = leftName.compareToIgnoreCase(rightName);
            if (compare != 0) {
                return compare;
            }
            return left.id().compareToIgnoreCase(right.id());
        });
        return customItems;
    }

    String resolveTemplateDisplayName(ItemStack template) {
        if (template == null || template.getType() == Material.AIR) {
            return "Unknown Item";
        }
        ItemMeta meta = template.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.isBlank()) {
                return stripped;
            }
            String raw = meta.getDisplayName();
            if (raw != null && !raw.isBlank()) {
                return raw;
            }
        }
        return formatMaterialName(template.getType());
    }

    boolean isCustomItemInUse(String customItemId) {
        if (customItemId == null || customItemId.isBlank()) {
            return false;
        }

        for (PlayerDataStore.PlayerOrderRecord record : playerDataStore.getAllOrdersSnapshot()) {
            PlayerDataStore.OrderEntry order = record.getOrder();
            if (!customItemId.equalsIgnoreCase(order.getCustomItemId())) {
                continue;
            }

            boolean noClaimable = order.getAmountClaimable() <= 0;
            boolean completedNormally = order.getAmountDelivered() >= order.getAmountOrdered() && noClaimable;
            boolean completedAfterCancel = order.isCancelled() && noClaimable;
            if (!completedNormally && !completedAfterCancel) {
                return true;
            }
        }
        return false;
    }

    String generateUniqueCustomItemId(ItemStack template) {
        String base = resolveTemplateDisplayName(template);
        return generateUniqueCustomItemId(base, null);
    }

    String generateUniqueCustomItemId(String preferredBase) {
        return generateUniqueCustomItemId(preferredBase, null);
    }

    String generateUniqueCustomItemId(String preferredBase, String keepId) {
        String normalizedBase = CustomItemStore.normalizeId(preferredBase);
        if (normalizedBase == null) {
            normalizedBase = "custom_item";
        }

        String normalizedKeepId = CustomItemStore.normalizeId(keepId);
        String candidate = normalizedBase;
        int suffix = 2;
        while (true) {
            CustomItemStore.CustomItemDefinition existing = customItemStore.get(candidate);
            if (existing == null || (normalizedKeepId != null && normalizedKeepId.equals(existing.id()))) {
                return candidate;
            }
            candidate = normalizedBase + "_" + suffix;
            suffix++;
        }
    }

    CustomItemStore.CustomItemDefinition resolveCustomItemDefinition(String customItemId) {
        if (customItemId == null || customItemId.isBlank()) {
            return null;
        }
        return customItemStore.get(customItemId);
    }

    Material resolveDraftMaterial(NewOrderDraft draft) {
        CustomItemStore.CustomItemDefinition customDefinition = resolveCustomItemDefinition(draft.customItemId());
        if (customDefinition != null) {
            return customDefinition.template().getType();
        }
        return resolveMaterial(draft.materialName());
    }

    ItemStack createOrderableSelectItem(OrderableItemOption option) {
        if (!option.isCustom()) {
            return createSimpleItem(option.material(), LIGHT_ACCENT + option.displayName(), List.of(WHITE + "Click to Select"));
        }

        ItemStack preview = option.previewItem().clone();
        preview.setAmount(1);
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return preview;
        }
        if (!meta.hasDisplayName()) {
            meta.setDisplayName(LIGHT_ACCENT + option.displayName());
        }

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(MUTED + "Custom Item");
        lore.add(MUTED + "ID: " + option.customItemId());
        lore.add(WHITE + "Order enchants: " + (option.allowOrderEnchants() ? CONFIRM_GREEN + "Enabled" : CANCEL_RED + "Disabled"));
        lore.add(WHITE + "Click to Select");
        meta.setLore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    List<OrderableItemOption> getCachedFilteredSortedItems(ItemSelectState itemSelectState) {
        if (itemSelectState == null) {
            return List.of();
        }

        String search = normalizeItemSelectSearch(itemSelectState.search);
        ItemSelectCacheKey cacheKey = new ItemSelectCacheKey(
            manager.itemSelectContentRevision(),
            itemSelectState.sortIndex,
            itemSelectState.filterIndex,
            search
        );
        List<OrderableItemOption> cachedItems = itemSelectState.cachedItems(cacheKey);
        if (cachedItems != null) {
            return cachedItems;
        }

        itemSelectState.search = search;
        List<OrderableItemOption> filtered = getFilteredSortedItems(itemSelectState);
        itemSelectState.cacheItems(cacheKey, filtered);
        return itemSelectState.cachedItems(cacheKey);
    }

    private String normalizeItemSelectSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private List<OrderableItemOption> getFilteredSortedItems(ItemSelectState itemSelectState) {
        List<OrderableItemOption> filtered = new ArrayList<>();
        String searchLower = normalizeItemSelectSearch(itemSelectState.search).toLowerCase(Locale.ROOT);

        for (Material material : ALL_ORDERABLE_ITEMS) {
            if (isOrderBlacklisted(material, Map.of())) {
                continue;
            }
            if (!matchesItemFilter(material, itemSelectState.filterIndex)) {
                continue;
            }

            String displayName = formatMaterialName(material);
            String searchText = displayName.toLowerCase(Locale.ROOT);
            if (!searchLower.isBlank()) {
                if (!searchText.contains(searchLower)) {
                    continue;
                }
            }
            filtered.add(new OrderableItemOption(
                material,
                displayName,
                searchText,
                new ItemStack(material),
                null,
                supportsOrderEnchantments(material)
            ));
        }

        for (CustomItemStore.CustomItemDefinition customItem : getSortedCustomItems()) {
            ItemStack template = customItem.template().clone();
            template.setAmount(1);
            Material material = template.getType();
            if (!matchesItemFilter(material, itemSelectState.filterIndex)) {
                continue;
            }

            String displayName = resolveTemplateDisplayName(template);
            String searchText = (displayName + " " + customItem.id() + " " + formatMaterialName(material)).toLowerCase(Locale.ROOT);
            if (!searchLower.isBlank() && !searchText.contains(searchLower)) {
                continue;
            }

            filtered.add(new OrderableItemOption(
                material,
                displayName,
                searchText,
                template,
                customItem.id(),
                customItem.allowOrderEnchants()
            ));
        }

        filtered.sort((left, right) -> {
            int compare = left.displayName().compareToIgnoreCase(right.displayName());
            if (compare == 0) {
                compare = left.searchText().compareToIgnoreCase(right.searchText());
            }
            return itemSelectState.sortIndex == 0 ? compare : -compare;
        });
        return filtered;
    }

    String getSignInputFirstLine(SignInputType inputType, String mainSearch, String itemSearch, NewOrderDraft draft) {
        return switch (inputType) {
            case AMOUNT -> formatCompactAmount(draft.amount());
            case PRICE -> formatCompactAmount(draft.pricePerItem());
            case MAIN_SEARCH -> mainSearch;
            case ITEM_SEARCH -> itemSearch;
        };
    }

    String getSignInputTargetLabel(SignInputType inputType) {
        return switch (inputType) {
            case AMOUNT -> manager.messages().get("chat-input.targets.amount");
            case PRICE -> manager.messages().get("chat-input.targets.price");
            case MAIN_SEARCH, ITEM_SEARCH -> manager.messages().get("chat-input.targets.search");
        };
    }

    void reopenAfterSignInput(Player player, SignInputType inputType) {
        switch (inputType) {
            case AMOUNT, PRICE -> manager.openNewOrderMenu(player);
            case MAIN_SEARCH -> manager.openOrdersMenu(player, null);
            case ITEM_SEARCH -> manager.openItemSelectMenu(player, false);
        }
    }

    boolean matchesItemFilter(Material material, int filterIndex) {
        return switch (filterIndex) {
            case 0 -> true;
            case 1 -> material.isBlock();
            case 2 -> isToolItem(material);
            case 3 -> isFoodItem(material);
            case 4 -> isCombatItem(material);
            case 5 -> isPotionItem(material);
            case 6 -> isBookItem(material);
            case 7 -> isIngredientItem(material);
            case 8 -> isUtilityItem(material);
            default -> true;
        };
    }

    boolean isToolItem(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || material == Material.SHEARS
            || material == Material.BRUSH
            || material == Material.FISHING_ROD
            || material == Material.FLINT_AND_STEEL;
    }

    boolean isFoodItem(Material material) {
        return material.isEdible() || Tag.ITEMS_FISHES.isTagged(material);
    }

    boolean isCombatItem(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material)
            || material == Material.BOW
            || material == Material.CROSSBOW
            || material == Material.TRIDENT
            || material == Material.MACE
            || material == Material.SHIELD
            || Tag.ITEMS_ARROWS.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(material);
    }

    boolean isPotionItem(Material material) {
        String name = material.name();
        return name.equals("POTION")
            || name.equals("SPLASH_POTION")
            || name.equals("LINGERING_POTION")
            || name.equals("TIPPED_ARROW");
    }

    boolean isBookItem(Material material) {
        return material.name().contains("BOOK");
    }

    boolean isIngredientItem(Material material) {
        if (material.isBlock() || isToolItem(material) || isFoodItem(material) || isCombatItem(material) || isPotionItem(material) || isBookItem(material)) {
            return false;
        }

        String name = material.name();
        return name.endsWith("_INGOT")
            || name.endsWith("_NUGGET")
            || name.endsWith("_SHARD")
            || name.endsWith("_GEM")
            || name.endsWith("_DUST")
            || name.endsWith("_HIDE")
            || name.endsWith("_MEMBRANE")
            || name.endsWith("_FRAGMENT")
            || name.endsWith("_SEEDS")
            || name.endsWith("_BEANS")
            || name.endsWith("_SAC")
            || name.endsWith("_PEARL")
            || name.endsWith("_ROD")
            || name.endsWith("_CRYSTAL")
            || name.endsWith("_SHELL")
            || name.endsWith("_CLAW")
            || name.endsWith("_SCRAP")
            || name.endsWith("_STRING")
            || name.endsWith("_LEATHER")
            || name.endsWith("_QUARTZ")
            || name.endsWith("_AMETHYST")
            || name.endsWith("_BLAZE_POWDER")
            || name.endsWith("_BLAZE_ROD")
            || name.endsWith("_GHAST_TEAR")
            || name.endsWith("_SPIDER_EYE")
            || name.endsWith("_FERMENTED_SPIDER_EYE")
            || name.endsWith("_GUNPOWDER")
            || name.endsWith("_SLIME_BALL")
            || name.endsWith("_MAGMA_CREAM")
            || name.endsWith("_PHANTOM_MEMBRANE")
            || name.endsWith("_RABBIT_FOOT")
            || name.endsWith("_GLOWSTONE_DUST")
            || name.endsWith("_REDSTONE")
            || name.endsWith("_SUGAR")
            || name.endsWith("_WHEAT")
            || name.endsWith("_KELP");
    }

    boolean isUtilityItem(Material material) {
        if (material.isBlock() || isToolItem(material) || isFoodItem(material) || isCombatItem(material) || isPotionItem(material) || isBookItem(material)) {
            return false;
        }
        return !isIngredientItem(material);
    }

    Material resolveMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || !material.isItem()) {
            return Material.STONE;
        }
        return material;
    }

    String formatMaterialName(Material material) {
        String[] rawParts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : rawParts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    int parsePositiveInt(String value) {
        OptionalInt parsed = LargeNumberParser.parsePositiveInt(value);
        return parsed.isPresent() ? parsed.getAsInt() : -1;
    }

    double parsePositiveDouble(String value) {
        OptionalDouble parsed = LargeNumberParser.parsePositiveDouble(value);
        return parsed.isPresent() ? parsed.getAsDouble() : -1;
    }

    String formatMoney(double value) {
        return NumberFormatters.money(value);
    }

    String formatCompactAmount(double value) {
        return NumberFormatters.compact(value);
    }
}
