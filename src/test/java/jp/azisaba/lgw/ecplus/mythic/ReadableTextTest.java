package jp.azisaba.lgw.ecplus.mythic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReadableTextTest {

    @Test
    void jsonAndLegacyTextGiveTheSameCanonicalForm() {
        String fromLegacy = CurrentLegacyDefinitionMatcher.readableText("§a§lコギト");
        assertEquals(fromLegacy, CurrentLegacyDefinitionMatcher.readableText("{\"text\":\"§a§lコギト\"}"));
        assertEquals(fromLegacy, CurrentLegacyDefinitionMatcher.readableText(
                "{\"text\":\"コギト\",\"color\":\"green\",\"bold\":true}"));
    }

    @Test
    void leadingResetDoesNotPreventAMatch() {
        assertEquals(CurrentLegacyDefinitionMatcher.readableText("§aName"),
                CurrentLegacyDefinitionMatcher.readableText("§r§aName"));
    }

    @Test
    void plainTextAndNullStayAsTheyAre() {
        assertEquals("plain", CurrentLegacyDefinitionMatcher.readableText("plain"));
        assertNull(CurrentLegacyDefinitionMatcher.readableText((String) null));
    }
}
