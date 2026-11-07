package jp.azisaba.lgw.ecplus;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyRawYamlTest {
    @Test
    void readsInternalFromRawYaml() {
        String yaml = "'0':\n" +
                "  '0':\n" +
                "    ==: org.bukkit.inventory.ItemStack\n" +
                "    v: 2230\n" +
                "    type: SMOOTH_STONE\n" +
                "    meta:\n" +
                "      ==: ItemMeta\n" +
                "      meta-type: UNSPECIFIC\n" +
                "      internal: H4sIAAAAAAAA/+NiYGBhECguyS9KTE91yq9wzM0vzSthgIBQDgauosS8lPzc0FBPFwaVpMTE1KQ0IwNdc6M0S10TI6MkXQsjwyRdIDZJtEg2NjRITOJg4EOYFlJZkMrAE+zr7x/iER8c4u/nysggimRZaUm+c35OTmpyCSMDAK/oliaKAAAA\n";
        Map<?, ?> raw = InventoryData.parseRawYaml(yaml);
        assertEquals("H4sIAAAAAAAA/+NiYGBhECguyS9KTE91yq9wzM0vzSthgIBQDgauosS8lPzc0FBPFwaVpMTE1KQ0IwNdc6M0S10TI6MkXQsjwyRdIDZJtEg2NjRITOJg4EOYFlJZkMrAE+zr7x/iER8c4u/nysggimRZaUm+c35OTmpyCSMDAK/oliaKAAAA",
                InventoryData.rawString(raw, "0.0.meta.internal"));
    }
}
