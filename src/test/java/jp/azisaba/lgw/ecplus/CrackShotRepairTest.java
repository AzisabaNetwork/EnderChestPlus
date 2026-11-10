package jp.azisaba.lgw.ecplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrackShotRepairTest {

    @Test
    void equivalentColorCodesGiveTheSameName() {
        // configured name vs. the name after a 1.15.2 -> 1.21 round trip (redundant codes dropped/reordered)
        assertEquals(CrackShotRepair.pureName("§l§6AK-47§r"), CrackShotRepair.pureName("§6AK-47"));
        assertEquals("AK-47", CrackShotRepair.pureName("§6§lAK-47 §7«30»"));
    }

    @Test
    void removesAmmoAndAttachments() {
        assertEquals("M4A1", CrackShotRepair.pureName("§eM4A1 ▶ §7Scope §8«12»"));
        assertEquals("Knife", CrackShotRepair.pureName("§fKnife"));
    }
}
