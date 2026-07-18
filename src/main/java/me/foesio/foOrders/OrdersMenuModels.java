package me.foesio.foOrders;

import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

enum MenuType {
    MAIN,
    YOUR_ORDERS,
    NEW_ORDER,
    ITEM_SELECT,
    ENCHANT_SELECT,
    MANAGE_ORDER,
    CLAIM_ORDER,
    DELIVER,
    DELIVERY_CONFIRM,
    HISTORY,
    ADMIN_ORDER_ACTIONS,
    ADMIN_ITEM_EDITOR,
    ADMIN_ITEM_EDIT
}

final class MenuViewState {
    int page = 1;
    String search = "";
    List<MainOrderView> visibleMainOrders;
    int manageOrderIndex = -1;
    int claimPage = 1;
    int enchantPage = 1;
    int historyPage = 1;
    int adminEditorPage = 1;
    boolean newOrderConfirmLocked = false;
    String claimSessionOrderId;
    List<Integer> claimSessionStacks;
    UUID historyTargetId;
    HistoryDataStore.HistoryType historyType = HistoryDataStore.HistoryType.ORDER;
    UUID adminTargetOwnerId;
    String adminTargetOrderId;
    NewOrderDraft draft;
    ItemSelectState itemSelectState;
    AdminItemDraft adminItemDraft;

    NewOrderDraft getOrCreateDraft() {
        if (draft == null) {
            draft = NewOrderDraft.defaults();
        }
        return draft;
    }

    ItemSelectState getOrCreateItemSelectState() {
        if (itemSelectState == null) {
            itemSelectState = new ItemSelectState();
        }
        return itemSelectState;
    }

    AdminItemDraft getOrCreateAdminItemDraft() {
        if (adminItemDraft == null) {
            adminItemDraft = AdminItemDraft.newDraft("custom_item");
        }
        return adminItemDraft;
    }
}

final class ItemSelectState {
    int page = 1;
    int sortIndex = 0;
    int filterIndex = 0;
    String search = "";
}

record ItemSelectCacheKey(int contentRevision, int sortIndex, int filterIndex, String search) {
    ItemSelectCacheKey {
        search = search == null ? "" : search;
    }
}

record OrderableItemOption(
    Material material,
    String displayName,
    String searchText,
    ItemStack previewItem,
    String customItemId,
    boolean allowOrderEnchants
) {
    private static final String MATERIAL_CHOICE_PREFIX = "material:";
    private static final String CUSTOM_CHOICE_PREFIX = "custom:";

    boolean isCustom() {
        return customItemId != null && !customItemId.isBlank();
    }

    String choiceKey() {
        return isCustom() ? customChoiceKey(customItemId) : materialChoiceKey(material);
    }

    String choiceLabel() {
        return isCustom() ? displayName + " (" + customItemId + ")" : displayName;
    }

    static String materialChoiceKey(Material material) {
        return MATERIAL_CHOICE_PREFIX + material.name();
    }

    static String customChoiceKey(String customItemId) {
        String normalizedId = customItemId == null ? "" : customItemId.trim().toLowerCase(Locale.ROOT);
        return CUSTOM_CHOICE_PREFIX + normalizedId;
    }
}

record MainOrderView(UUID ownerId, String ownerName, PlayerDataStore.OrderEntry order) {
}

record AdminOrderTarget(UUID ownerId, PlayerDataStore.PlayerData ownerData, PlayerDataStore.OrderEntry order) {
}

record NewOrderDraft(String customItemId, String materialName, int amount, double pricePerItem, Map<String, Integer> enchantLevels) {
    NewOrderDraft {
        customItemId = customItemId == null || customItemId.isBlank() ? null : customItemId.trim().toLowerCase(Locale.ROOT);
        enchantLevels = enchantLevels == null ? Map.of() : new LinkedHashMap<>(enchantLevels);
    }

    static NewOrderDraft defaults() {
        return new NewOrderDraft(null, Material.STONE.name(), 1, 1, Map.of());
    }

    NewOrderDraft withMaterialName(String newMaterialName) {
        return new NewOrderDraft(null, newMaterialName, amount, pricePerItem, enchantLevels());
    }

    NewOrderDraft withSelection(String newCustomItemId, String newMaterialName) {
        return new NewOrderDraft(newCustomItemId, newMaterialName, amount, pricePerItem, enchantLevels());
    }

    NewOrderDraft withAmount(int newAmount) {
        return new NewOrderDraft(customItemId, materialName, newAmount, pricePerItem, enchantLevels());
    }

    NewOrderDraft withPricePerItem(double newPricePerItem) {
        return new NewOrderDraft(customItemId, materialName, amount, newPricePerItem, enchantLevels());
    }

    NewOrderDraft withEnchantLevels(Map<String, Integer> newEnchantLevels) {
        return new NewOrderDraft(customItemId, materialName, amount, pricePerItem, new LinkedHashMap<>(newEnchantLevels));
    }
}

record PendingDeliveryState(
    UUID ownerId,
    String orderId,
    ItemStack[] submittedItems
) {
    PendingDeliveryState withSubmittedItems(ItemStack[] newSubmittedItems) {
        return new PendingDeliveryState(ownerId, orderId, newSubmittedItems);
    }
}

record AdminItemDraft(String existingId, String itemId, ItemStack template, boolean allowOrderEnchants) {
    AdminItemDraft {
        existingId = existingId == null || existingId.isBlank() ? null : existingId.trim().toLowerCase(Locale.ROOT);
        itemId = itemId == null || itemId.isBlank() ? null : itemId.trim().toLowerCase(Locale.ROOT);
        template = template == null ? null : template.clone();
    }

    static AdminItemDraft newDraft(String suggestedId) {
        return new AdminItemDraft(null, suggestedId, null, false);
    }

    static AdminItemDraft fromDefinition(CustomItemStore.CustomItemDefinition definition) {
        return new AdminItemDraft(definition.id(), definition.id(), definition.template().clone(), definition.allowOrderEnchants());
    }

    AdminItemDraft withTemplate(ItemStack newTemplate) {
        return new AdminItemDraft(existingId, itemId, newTemplate == null ? null : newTemplate.clone(), allowOrderEnchants);
    }

    AdminItemDraft withAllowOrderEnchants(boolean enabled) {
        return new AdminItemDraft(existingId, itemId, template, enabled);
    }

    AdminItemDraft withItemId(String newItemId) {
        return new AdminItemDraft(existingId, newItemId, template, allowOrderEnchants);
    }
}

final class OrdersMenuHolder implements InventoryHolder {
    private final MenuType menuType;
    private final int guiRevision;
    private final String title;
    private Inventory inventory;

    OrdersMenuHolder(MenuType menuType, int guiRevision, String title) {
        this.menuType = menuType;
        this.guiRevision = guiRevision;
        this.title = title == null ? "" : title;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    MenuType getMenuType() {
        return menuType;
    }

    int getGuiRevision() {
        return guiRevision;
    }

    String getTitle() {
        return title;
    }
}
