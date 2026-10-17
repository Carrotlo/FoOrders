package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersMenuClaimPreviewTest {
    @Test
    void configuredLineBudgetAndTranslatedTemplatesReachFinalLore() {
        List<Integer> stacks = List.of(64, 64, 32, 16);
        assertEquals(List.of("64 voorwerp", "3 overige"),
            OrdersMenuDeliverySupport.claimPreviewLines(stacks, 2,
                amount -> amount + " voorwerp", count -> count + " overige"));
        assertEquals(List.of("64 voorwerp", "64 voorwerp", "32 voorwerp", "16 voorwerp"),
            OrdersMenuDeliverySupport.claimPreviewLines(stacks, 4,
                amount -> amount + " voorwerp", count -> count + " overige"));
        assertEquals(List.of(), OrdersMenuDeliverySupport.claimPreviewLines(stacks, 0,
            amount -> amount + " voorwerp", count -> count + " overige"));
    }

    @Test
    void removedStackAndMoreLinesDoNotReappearAsBlankOrEnglishLore() {
        List<Integer> stacks = List.of(64, 32, 16);
        assertEquals(List.of("64 voorwerp", "32 voorwerp"),
            OrdersMenuDeliverySupport.claimPreviewLines(stacks, 2,
                amount -> amount + " voorwerp", count -> ""));
        assertEquals(List.of("3 overige"),
            OrdersMenuDeliverySupport.claimPreviewLines(stacks, 2,
                amount -> "", count -> count + " overige"));
        assertEquals(List.of(), OrdersMenuDeliverySupport.claimPreviewLines(stacks, 2,
            amount -> "", count -> ""));
    }

    @Test
    void claimTransferCountUsesSessionAndOutstandingAmount() {
        assertEquals(64, OrdersMenuDeliverySupport.claimAmountForClick(64, 64));
        assertEquals(5, OrdersMenuDeliverySupport.claimAmountForClick(64, 5));
        assertEquals(0, OrdersMenuDeliverySupport.claimAmountForClick(0, 64));
        assertEquals(0, OrdersMenuDeliverySupport.claimAmountForClick(64, 0));
    }
}
