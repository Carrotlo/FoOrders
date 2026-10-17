package me.foesio.foOrders;

import me.foesio.core.dialog.DialogIcons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicItemMetaTemplateTest {
    @Test
    void paperKeepsConfiguredTokensForViewerRenderingWithoutAddingRemovedText() {
        AtomicReference<String> name = new AtomicReference<>();
        AtomicReference<List<String>> lore = new AtomicReference<>();
        ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
            new Class<?>[]{ItemMeta.class}, (proxy, method, args) -> {
                if (method.getName().equals("setDisplayName")) {
                    name.set((String) args[0]);
                }
                if (method.getName().equals("setLore")) {
                    lore.set((List<String>) args[0]);
                }
                return null;
            });

        OrdersMenuItemSupport.setPublicDisplayName(meta, ":diamond: Egendefinert");
        OrdersMenuItemSupport.setPublicLore(meta, List.of(" ", "Eiertekst"));
        assertEquals(":diamond: Egendefinert", name.get());
        assertEquals(List.of(" ", "Eiertekst"), lore.get());

        OrdersMenuItemSupport.setPublicDisplayName(meta, "Egendefinert");
        OrdersMenuItemSupport.setPublicLore(meta, List.of());
        assertEquals("Egendefinert", name.get());
        assertEquals(List.of(), lore.get());
    }

    @Test
    void ownerRemovedLoreAndSpriteRemainAbsentAtTheCoreItemMetaSetter() {
        AtomicReference<Component> name = new AtomicReference<>();
        AtomicReference<List<? extends Component>> lore = new AtomicReference<>();
        ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
            new Class<?>[]{ItemMeta.class}, (proxy, method, args) -> {
                if (method.getName().equals("displayName") && args != null && args.length == 1) {
                    name.set((Component) args[0]);
                }
                if (method.getName().equals("lore") && args != null && args.length == 1) {
                    lore.set((List<? extends Component>) args[0]);
                }
                if (method.getName().equals("hasDisplayName")) {
                    return name.get() != null;
                }
                if (method.getName().equals("getDisplayName")) {
                    return name.get() == null ? null : LegacyComponentSerializer.legacySection().serialize(name.get());
                }
                if (method.getName().equals("getLore")) {
                    return lore.get() == null ? null : lore.get().stream()
                        .map(LegacyComponentSerializer.legacySection()::serialize).toList();
                }
                if (method.getName().equals("displayName") && (args == null || args.length == 0)) {
                    return name.get();
                }
                if (method.getName().equals("lore") && (args == null || args.length == 0)) {
                    return lore.get();
                }
                return null;
            });

        assertTrue(DialogIcons.applyItemMetaTemplate(meta, ":diamond: Owner", List.of("Owner lore")));
        assertEquals(":diamond: Owner", LegacyComponentSerializer.legacySection().serialize(name.get()));
        assertEquals(1, lore.get().size());

        assertTrue(DialogIcons.applyItemMetaTemplate(meta, "Owner", List.of()));
        assertEquals("Owner", LegacyComponentSerializer.legacySection().serialize(name.get()));
        assertEquals(List.of(), lore.get());

        ItemStack rendered = DialogIcons.forViewer(null, new StubItemStack(meta));
        assertTrue(rendered instanceof StubItemStack);
        assertEquals("Owner", LegacyComponentSerializer.legacySection().serialize(name.get()));
        assertEquals(List.of(), lore.get());
    }

    private static final class StubItemStack extends ItemStack {
        private ItemMeta meta;

        private StubItemStack(ItemMeta meta) {
            this.meta = meta;
        }

        @Override
        public ItemStack clone() {
            return new StubItemStack(meta);
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta;
        }

        @Override
        public boolean setItemMeta(ItemMeta meta) {
            this.meta = meta;
            return true;
        }
    }
}
