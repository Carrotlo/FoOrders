package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoOrdersPaperItemSelectionDialogServiceTest {
    @Test
    void removingPickerControlsClearsTheirHiddenStateAndResetsPagination() {
        ItemSelectState inventory = new ItemSelectState();
        inventory.page = 5;
        inventory.sortIndex = 1;
        inventory.filterIndex = 4;
        inventory.search = "diamond";
        inventory.clearUnavailableControls(false, true, false);
        assertEquals(1, inventory.page);
        assertEquals(0, inventory.sortIndex);
        assertEquals(4, inventory.filterIndex);
        assertEquals("", inventory.search);

        FoOrdersPaperItemSelectionDialogService.PickerState nativeState =
            new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", 4, 1, 4);
        assertEquals(new FoOrdersPaperItemSelectionDialogService.PickerState("", 0, 0, 4),
            FoOrdersPaperItemSelectionDialogService.clearUnavailableControls(nativeState,
                List.of("choices", "filter")));
        assertEquals(nativeState,
            FoOrdersPaperItemSelectionDialogService.clearUnavailableControls(nativeState,
                List.of("choices", "sort", "filter", "search")));
    }

    @Test
    void nativeCycleOptionIsNotReprocessedAsAnOwnerToken() {
        String rendered = FoOrdersPaperItemSelectionDialogService.substitute(
            "{theme}Sort: {option}", Map.of("option", "{theme} %page%"),
            raw -> raw.replace("{theme}", "GREEN ").replace("%page%", "99"));
        assertEquals("GREEN Sort: {theme} %page%", rendered);
    }

    @Test
    void nativePickerFallbackPreservesSearchAndCyclesButResetsInventoryPage() {
        ItemSelectState inventory = new ItemSelectState();
        inventory.search = "old query";
        inventory.sortIndex = 0;
        inventory.filterIndex = 0;
        inventory.page = 7;

        FoOrdersPaperItemSelectionDialogService.copyToInventoryState(
            inventory, "SÖK_DIAMOND", 1, 4);

        assertEquals("SÖK_DIAMOND", inventory.search);
        assertEquals(1, inventory.sortIndex);
        assertEquals(4, inventory.filterIndex);
        assertEquals(1, inventory.page);
    }

    @Test
    void nativePickerClampsStalePageAfterChoicesShrink() {
        FoOrdersPaperItemSelectionDialogService.PickerState stale =
            new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", 7, 1, 2);

        assertEquals(new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", 1, 1, 2),
            FoOrdersPaperItemSelectionDialogService.clampPage(stale, 13, 9));
        assertEquals(new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", 0, 1, 2),
            FoOrdersPaperItemSelectionDialogService.clampPage(stale, 0, 9));
        assertEquals(new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", 0, 1, 2),
            FoOrdersPaperItemSelectionDialogService.clampPage(
                new FoOrdersPaperItemSelectionDialogService.PickerState("diamond", -4, 1, 2), 40, 9));
    }
}
