package jp.azisaba.lgw.ecplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageNameSanitizeTest {

    @Test
    void sanitizesPageNames() {
        assertNull(InventoryData.sanitizePageName(null));
        assertNull(InventoryData.sanitizePageName("   "));
        assertNull(InventoryData.sanitizePageName("&e&r"));

        assertEquals("鉱石", InventoryData.sanitizePageName("  鉱石  "));
        assertEquals("武器", InventoryData.sanitizePageName("&e武器&r"));
        assertEquals("防具", InventoryData.sanitizePageName("§b防具§r"));
        assertEquals("AB", InventoryData.sanitizePageName("A\nB"));
        assertEquals("123456789012345678901234", InventoryData.sanitizePageName("123456789012345678901234567890"));
    }
}
