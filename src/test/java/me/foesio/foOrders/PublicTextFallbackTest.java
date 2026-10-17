package me.foesio.foOrders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicTextFallbackTest {
    @Test
    void keepsOwnerSpacingWhenNoSpriteNeedsRemoval() {
        assertEquals(" ", PublicTextFallback.render(" "));
        assertEquals("§aEier  tekst ", PublicTextFallback.render("&aEier  tekst "));
        assertEquals("", PublicTextFallback.render(""));
    }

    @Test
    void stripsOnlyConfiguredSpritePresentationOnLegacyPath() {
        assertEquals("§aEier", PublicTextFallback.render(":diamond: &aEier"));
    }
}
