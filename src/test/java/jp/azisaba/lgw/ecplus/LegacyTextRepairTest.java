package jp.azisaba.lgw.ecplus;

import com.google.gson.JsonParser;
import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyTextRepairTest {

    @Test
    void cleansJunkFromColorName() {
        String fixed = LegacyTextRepair.repairJson("{\"extra\":[{\"color\":\"Red\\\"\",\"text\":\"a\"}],\"text\":\"\"}");
        assertEquals("red", JsonParser.parseString(fixed).getAsJsonObject()
                .getAsJsonArray("extra").get(0).getAsJsonObject().get("color").getAsString());
    }

    @Test
    void removesResetAndUnknownColors() {
        String fixed = LegacyTextRepair.repairJson("[\"\",{\"color\":\"reset\\\"\",\"text\":\"  \"},{\"color\":\"rainbow\",\"text\":\"x\"}]");
        var array = JsonParser.parseString(fixed).getAsJsonArray();
        assertNull(array.get(1).getAsJsonObject().get("color"));
        assertNull(array.get(2).getAsJsonObject().get("color"));
        assertEquals("  ", array.get(1).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void leavesValidTextUntouched() {
        assertNull(LegacyTextRepair.repairJson("{\"color\":\"dark_green\",\"text\":\"ようこそ\"}"));
        assertNull(LegacyTextRepair.repairJson("{\"color\":\"#12abEF\",\"text\":\"x\"}"));
        assertNull(LegacyTextRepair.repairJson("plain text"));
    }

    @Test
    void repairsBookPagesInsideShulkerBox() {
        Nbt.CompoundTag bookTag = new Nbt.CompoundTag();
        Nbt.ListTag pages = new Nbt.ListTag();
        pages.add(new Nbt.StringTag("[\"\",{\"color\":\"reset\\\"\",\"text\":\"本\"}]"));
        pages.add(new Nbt.StringTag("{\"text\":\"ok\"}"));
        bookTag.put("pages", pages);
        Nbt.CompoundTag book = new Nbt.CompoundTag();
        book.putString("id", "minecraft:written_book");
        book.put("tag", bookTag);
        Nbt.ListTag items = new Nbt.ListTag();
        items.add(book);
        Nbt.CompoundTag blockEntity = new Nbt.CompoundTag();
        blockEntity.put("Items", items);
        Nbt.CompoundTag shulker = new Nbt.CompoundTag();
        shulker.put("BlockEntityTag", blockEntity);

        assertEquals(1, LegacyTextRepair.repairTag(shulker));
        String page = ((Nbt.StringTag) pages.getElements().get(0)).getValue();
        assertEquals("[\"\",{\"text\":\"本\"}]", page);
    }
}
