package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPdcRepairTest {

    @Test
    void readsValuesLike115() {
        assertEquals(1, ((Nbt.IntTag) LegacyPdcRepair.legacyValue("1i")).getValue());
        assertEquals(-5, ((Nbt.IntTag) LegacyPdcRepair.legacyValue("-5I")).getValue());
        assertEquals(1, ((Nbt.ByteTag) LegacyPdcRepair.legacyValue("1b")).getValue());
        assertEquals(3, ((Nbt.ShortTag) LegacyPdcRepair.legacyValue("3s")).getValue());
        assertEquals(123456789012L, ((Nbt.LongTag) LegacyPdcRepair.legacyValue("123456789012L")).getValue());
        assertEquals(1.5, ((Nbt.DoubleTag) LegacyPdcRepair.legacyValue("1.5d")).getValue());
        assertEquals(1.5f, ((Nbt.FloatTag) LegacyPdcRepair.legacyValue("1.5f")).getValue());
        assertEquals(1, ((Nbt.ByteTag) LegacyPdcRepair.legacyValue("true")).getValue());
        // 1.15.2 turned plain numbers back into strings
        assertEquals("123", ((Nbt.StringTag) LegacyPdcRepair.legacyValue("123")).getValue());
        assertEquals("i", ((Nbt.StringTag) LegacyPdcRepair.legacyValue("i")).getValue());
        assertEquals("SWORD", ((Nbt.StringTag) LegacyPdcRepair.legacyValue("SWORD")).getValue());
        assertEquals("", ((Nbt.StringTag) LegacyPdcRepair.legacyValue("")).getValue());
        assertEquals("99999999999i", ((Nbt.StringTag) LegacyPdcRepair.legacyValue("99999999999i")).getValue());
    }

    /** What Paper 1.21.11 made of the sanitized YAML of a 1.15.2 BuildTool and a Mythic item. */
    @Test
    void repairsQuoteWrappedValues() {
        Nbt.CompoundTag pdc = new Nbt.CompoundTag();
        pdc.putString("minecraft:build_tool_item", "\"1i\"");
        pdc.putString("mythicmobs:type", "\"SWORD\"");
        pdc.putString("custom:empty", "\"\"");
        pdc.putString("custom:quoted", "\"\"hi\"\"");
        pdc.putInt("modern:int", 7);
        pdc.putString("modern:string", "plain");
        Nbt.CompoundTag nested = new Nbt.CompoundTag();
        nested.putString("inner:value", "\"2i\"");
        pdc.put("custom:nested", nested);

        assertTrue(LegacyPdcRepair.repairPdc(pdc));
        assertEquals(1, ((Nbt.IntTag) pdc.get("minecraft:build_tool_item")).getValue());
        assertEquals("SWORD", pdc.getString("mythicmobs:type"));
        assertEquals("", pdc.getString("custom:empty"));
        assertEquals("\"hi\"", pdc.getString("custom:quoted"));
        assertEquals(7, ((Nbt.IntTag) pdc.get("modern:int")).getValue());
        assertEquals("plain", pdc.getString("modern:string"));
        assertEquals(2, ((Nbt.IntTag) nested.get("inner:value")).getValue());

        // a value that really had quotes keeps them after one pass: that is why InventoryData runs it only once
        pdc.remove("custom:quoted");
        assertFalse(LegacyPdcRepair.repairPdc(pdc));
    }

    @Test
    void repairsNestedItemsInTree() {
        Nbt.CompoundTag pdc = new Nbt.CompoundTag();
        pdc.putString("minecraft:build_tool_item", "\"1i\"");
        Nbt.CompoundTag customData = new Nbt.CompoundTag();
        customData.put("PublicBukkitValues", pdc);
        Nbt.CompoundTag components = new Nbt.CompoundTag();
        components.put("minecraft:custom_data", customData);
        Nbt.CompoundTag nestedItem = new Nbt.CompoundTag();
        nestedItem.putString("id", "minecraft:blaze_rod");
        nestedItem.put("components", components);
        Nbt.CompoundTag slot = new Nbt.CompoundTag();
        slot.putInt("slot", 0);
        slot.put("item", nestedItem);
        Nbt.ListTag container = new Nbt.ListTag();
        container.add(slot);
        Nbt.CompoundTag shulkerComponents = new Nbt.CompoundTag();
        shulkerComponents.put("minecraft:container", container);
        Nbt.CompoundTag shulker = new Nbt.CompoundTag();
        shulker.putString("id", "minecraft:shulker_box");
        shulker.put("components", shulkerComponents);

        assertTrue(LegacyPdcRepair.repairTree(shulker));
        assertInstanceOf(Nbt.IntTag.class, pdc.get("minecraft:build_tool_item"));
    }

    @Test
    void sanitizerWrapsEveryValueOnce() {
        String yaml = "meta:\n" +
                "  PublicBukkitValues:\n" +
                "    minecraft:build_tool_item: 1i\n" +
                "    custom:quoted: '\"hi\"'\n";
        String sanitized = InventoryData.sanitizeLegacyYaml(yaml);
        assertTrue(sanitized.contains("minecraft:build_tool_item: '\"1i\"'"));
        assertTrue(sanitized.contains("custom:quoted: '\"\"hi\"\"'"));

        // the raw values of the sanitized YAML, as used when an item is rebuilt from raw NBT
        Map<?, ?> raw = InventoryData.parseRawYaml(sanitized);
        Nbt.CompoundTag pdc = (Nbt.CompoundTag) LegacyPdcRepair.fromRawYaml(
                ((Map<?, ?>) ((Map<?, ?>) raw.get("meta")).get("PublicBukkitValues")));
        assertEquals(1, ((Nbt.IntTag) pdc.get("minecraft:build_tool_item")).getValue());
        assertEquals("\"hi\"", pdc.getString("custom:quoted"));
    }
}
