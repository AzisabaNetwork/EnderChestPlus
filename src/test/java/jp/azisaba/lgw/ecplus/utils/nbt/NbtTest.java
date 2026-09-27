package jp.azisaba.lgw.ecplus.utils.nbt;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class NbtTest {

    @Test
    public void testReadWriteCompressed() throws IOException {
        Nbt.CompoundTag root = new Nbt.CompoundTag();
        Nbt.CompoundTag blockEntityTag = new Nbt.CompoundTag();
        blockEntityTag.putString("id", "minecraft:shulker_box");

        Nbt.ListTag items = new Nbt.ListTag();

        Nbt.CompoundTag item0 = new Nbt.CompoundTag();
        item0.putByte("Slot", (byte) 0);
        item0.putString("id", "minecraft:diamond");
        item0.putByte("Count", (byte) 64);
        items.add(item0);

        Nbt.CompoundTag item1 = new Nbt.CompoundTag();
        item1.putByte("Slot", (byte) 1);
        item1.putString("id", "minecraft:iron_ingot");
        item1.putByte("Count", (byte) 32);
        items.add(item1);

        blockEntityTag.put("Items", items);
        root.put("BlockEntityTag", blockEntityTag);

        byte[] compressed = Nbt.writeCompressed(root);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);

        Nbt.CompoundTag readRoot = Nbt.readCompressed(compressed);
        assertNotNull(readRoot);
        Nbt.CompoundTag readBlockEntity = readRoot.getCompound("BlockEntityTag");
        assertNotNull(readBlockEntity);
        assertEquals("minecraft:shulker_box", readBlockEntity.getString("id"));

        Nbt.ListTag readItems = readBlockEntity.getList("Items");
        assertNotNull(readItems);
        assertEquals(2, readItems.size());

        Nbt.CompoundTag readItem0 = (Nbt.CompoundTag) readItems.getElements().get(0);
        assertEquals(0, readItem0.getNumber("Slot").intValue());
        assertEquals("minecraft:diamond", readItem0.getString("id"));
        assertEquals(64, readItem0.getNumber("Count").intValue());

        Nbt.CompoundTag readItem1 = (Nbt.CompoundTag) readItems.getElements().get(1);
        assertEquals(1, readItem1.getNumber("Slot").intValue());
        assertEquals("minecraft:iron_ingot", readItem1.getString("id"));
        assertEquals(32, readItem1.getNumber("Count").intValue());
    }

    @Test
    public void testStandaloneItemExtraction() throws IOException {
        Nbt.CompoundTag item = new Nbt.CompoundTag();
        item.putByte("Slot", (byte) 5);
        item.putString("id", "minecraft:diamond");
        item.putByte("Count", (byte) 64);

        Nbt.CompoundTag tag = new Nbt.CompoundTag();
        tag.putInt("CustomModelData", 12345);
        item.put("tag", tag);

        Nbt.CompoundTag standalone = new Nbt.CompoundTag();
        standalone.putInt("DataVersion", 3465);
        for (java.util.Map.Entry<String, Nbt.Tag> entry : item.getTags().entrySet()) {
            if (!entry.getKey().equals("Slot")) {
                standalone.put(entry.getKey(), entry.getValue().copy());
            }
        }

        assertEquals(3465, standalone.getNumber("DataVersion").intValue());
        assertEquals("minecraft:diamond", standalone.getString("id"));
        assertEquals(64, standalone.getNumber("Count").intValue());
        assertNull(standalone.get("Slot"));
        assertNotNull(standalone.getCompound("tag"));
        assertEquals(12345, standalone.getCompound("tag").getNumber("CustomModelData").intValue());

        byte[] bytes = Nbt.writeCompressed(standalone);
        Nbt.CompoundTag read = Nbt.readCompressed(bytes);
        assertEquals(3465, read.getNumber("DataVersion").intValue());
        assertEquals(64, read.getNumber("Count").intValue());
    }

    @Test
    public void testBase64LegacyPayload() throws IOException {
        Nbt.CompoundTag root = new Nbt.CompoundTag();
        Nbt.CompoundTag blockEntityTag = new Nbt.CompoundTag();
        blockEntityTag.putString("id", "minecraft:shulker_box");
        Nbt.ListTag items = new Nbt.ListTag();

        Nbt.CompoundTag item = new Nbt.CompoundTag();
        item.putByte("Slot", (byte) 0);
        item.putString("id", "minecraft:diamond");
        item.putByte("Count", (byte) 64);
        items.add(item);
        blockEntityTag.put("Items", items);
        root.put("BlockEntityTag", blockEntityTag);

        byte[] rawBytes = Nbt.writeCompressed(root);
        String base64 = java.util.Base64.getEncoder().encodeToString(rawBytes);

        // Verify decoding
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        Nbt.CompoundTag parsed = Nbt.readCompressed(decoded);
        assertNotNull(parsed.getCompound("BlockEntityTag"));
        Nbt.ListTag parsedItems = parsed.getCompound("BlockEntityTag").getList("Items");
        assertEquals(1, parsedItems.size());
        Nbt.CompoundTag parsedItem = (Nbt.CompoundTag) parsedItems.getElements().get(0);
        assertEquals(64, parsedItem.getNumber("Count").intValue());
    }

    @Test
    public void testCustomNbtAndPdcPreservation() throws IOException {
        // Simulate a legacy Shulker Box containing a MythicMobs & Soulbound custom item
        Nbt.CompoundTag root = new Nbt.CompoundTag();
        Nbt.CompoundTag blockEntityTag = new Nbt.CompoundTag();
        blockEntityTag.putString("id", "minecraft:shulker_box");
        Nbt.ListTag items = new Nbt.ListTag();

        Nbt.CompoundTag item = new Nbt.CompoundTag();
        item.putByte("Slot", (byte) 0);
        item.putString("id", "minecraft:diamond_sword");
        item.putByte("Count", (byte) 1);

        Nbt.CompoundTag tag = new Nbt.CompoundTag();
        // Bukkit PDC (PersistentDataContainer)
        Nbt.CompoundTag pbv = new Nbt.CompoundTag();
        pbv.putString("mythicmobs:type", "SKELETON_KING_BLADE");
        pbv.putString("soulbound:owner", "4ed4b1f8-2e92-4eca-aed6-339bffc7c3f7");
        pbv.putByte("soulbound:bound", (byte) 1);
        tag.put("PublicBukkitValues", pbv);

        // Direct custom NBT tags
        tag.putString("MYTHIC_TYPE", "SKELETON_KING_BLADE");
        Nbt.CompoundTag soulboundTag = new Nbt.CompoundTag();
        soulboundTag.putString("Owner", "4ed4b1f8-2e92-4eca-aed6-339bffc7c3f7");
        tag.put("Soulbound", soulboundTag);

        item.put("tag", tag);
        items.add(item);
        blockEntityTag.put("Items", items);
        root.put("BlockEntityTag", blockEntityTag);

        // Encode to Base64 (legacy internal format)
        byte[] rawBytes = Nbt.writeCompressed(root);
        String base64 = java.util.Base64.getEncoder().encodeToString(rawBytes);

        // Decode and build standalone item as fixLegacyContainerItem does
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        Nbt.CompoundTag parsedRoot = Nbt.readCompressed(decoded);
        Nbt.ListTag parsedItems = parsedRoot.getCompound("BlockEntityTag").getList("Items");
        Nbt.CompoundTag parsedItem = (Nbt.CompoundTag) parsedItems.getElements().get(0);

        Nbt.CompoundTag standalone = new Nbt.CompoundTag();
        standalone.putInt("DataVersion", 3465);
        for (java.util.Map.Entry<String, Nbt.Tag> entry : parsedItem.getTags().entrySet()) {
            if (!entry.getKey().equals("Slot")) {
                standalone.put(entry.getKey(), entry.getValue().copy());
            }
        }

        byte[] standaloneBytes = Nbt.writeCompressed(standalone);
        Nbt.CompoundTag verified = Nbt.readCompressed(standaloneBytes);

        // Verify that all PDC and custom NBT tags are completely preserved
        assertEquals(3465, verified.getNumber("DataVersion").intValue());
        assertEquals("minecraft:diamond_sword", verified.getString("id"));
        assertEquals(1, verified.getNumber("Count").intValue());

        Nbt.CompoundTag verifiedTag = verified.getCompound("tag");
        assertNotNull(verifiedTag);

        Nbt.CompoundTag verifiedPbv = verifiedTag.getCompound("PublicBukkitValues");
        assertNotNull(verifiedPbv);
        assertEquals("SKELETON_KING_BLADE", verifiedPbv.getString("mythicmobs:type"));
        assertEquals("4ed4b1f8-2e92-4eca-aed6-339bffc7c3f7", verifiedPbv.getString("soulbound:owner"));
        assertEquals(1, verifiedPbv.getNumber("soulbound:bound").intValue());

        assertEquals("SKELETON_KING_BLADE", verifiedTag.getString("MYTHIC_TYPE"));
        Nbt.CompoundTag verifiedSoulbound = verifiedTag.getCompound("Soulbound");
        assertNotNull(verifiedSoulbound);
        assertEquals("4ed4b1f8-2e92-4eca-aed6-339bffc7c3f7", verifiedSoulbound.getString("Owner"));
    }

    @Test
    public void testSanitizeLegacyYaml() {
        String input = "meta:\n" +
                "  ==: ItemMeta\n" +
                "  PublicBukkitValues:\n" +
                "    \"custom:empty1\": \"\"\n" +
                "    \"custom:empty2\": ''\n" +
                "    \"custom:empty3\": \n" +
                "    'custom:empty4': \"\"\n" +
                "    custom:empty5: \"\"\n" +
                "    \"mythicmobs:type\": \"SWORD\"\n";

        String sanitized = jp.azisaba.lgw.ecplus.InventoryData.sanitizeLegacyYaml(input);
        assertTrue(sanitized.contains("\"custom:empty1\": '\"\"'"));
        assertTrue(sanitized.contains("\"custom:empty2\": '\"\"'"));
        assertTrue(sanitized.contains("\"custom:empty3\": '\"\"'"));
        assertTrue(sanitized.contains("'custom:empty4': '\"\"'"));
        assertTrue(sanitized.contains("custom:empty5: '\"\"'"));
        assertTrue(sanitized.contains("\"mythicmobs:type\": \"SWORD\""));
    }
}
