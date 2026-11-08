package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyStorageBoxRepairTest {

    private static Nbt.CompoundTag box(String type, String id, long amount) {
        Nbt.CompoundTag tag = new Nbt.CompoundTag();
        tag.putString("storageBoxType", type);
        tag.putLong("storageBoxAmount", amount);
        tag.putString("randomUUID", id);
        Nbt.CompoundTag inner = new Nbt.CompoundTag();
        inner.putString("MYTHIC_TYPE", "SomeMythicItem");
        tag.put("storageBoxTag", inner);
        return tag;
    }

    private static String base64(Nbt.CompoundTag tag) throws Exception {
        return Base64.getEncoder().encodeToString(Nbt.writeCompressed(tag));
    }

    @Test
    void indexesTopLevelAndShulkerBoxesByRandomUuid() throws Exception {
        Nbt.CompoundTag nestedItem = new Nbt.CompoundTag();
        nestedItem.putString("id", "minecraft:stick");
        nestedItem.putByte("Slot", (byte) 3);
        nestedItem.put("tag", box("PAPER", "nested-id", 5));
        Nbt.ListTag items = new Nbt.ListTag();
        items.add(nestedItem);
        Nbt.CompoundTag blockEntity = new Nbt.CompoundTag();
        blockEntity.put("Items", items);
        Nbt.CompoundTag shulker = new Nbt.CompoundTag();
        shulker.put("BlockEntityTag", blockEntity);

        String yaml = "'0':\n"
                + "  '1':\n"
                + "    ==: org.bukkit.inventory.ItemStack\n"
                + "    type: STICK\n"
                + "    meta:\n"
                + "      ==: ItemMeta\n"
                + "      internal: " + base64(box("DIAMOND_SWORD", "top-id", 12)) + "\n"
                + "  '2':\n"
                + "    ==: org.bukkit.inventory.ItemStack\n"
                + "    type: SHULKER_BOX\n"
                + "    meta:\n"
                + "      ==: ItemMeta\n"
                + "      internal: " + base64(shulker) + "\n";

        Map<String, Nbt.CompoundTag> boxes = LegacyStorageBoxRepair.indexLegacyBoxes(InventoryData.parseRawYaml(yaml));

        assertEquals(2, boxes.size());
        assertEquals("DIAMOND_SWORD", boxes.get("top-id").getString("storageBoxType"));
        assertEquals(12L, boxes.get("top-id").getNumber("storageBoxAmount"));
        assertNotNull(boxes.get("top-id").getCompound("storageBoxTag"));
        assertEquals("PAPER", boxes.get("nested-id").getString("storageBoxType"));
    }

    @Test
    void translatesRenamedLegacyMaterials() {
        assertEquals("SHORT_GRASS", LegacyStorageBoxRepair.modernMaterialName("GRASS"));
        assertEquals("DIRT_PATH", LegacyStorageBoxRepair.modernMaterialName("grass_path"));
        assertEquals("DIAMOND", LegacyStorageBoxRepair.modernMaterialName("DIAMOND"));
        assertEquals("null", LegacyStorageBoxRepair.modernMaterialName("null").toLowerCase());
    }
}
