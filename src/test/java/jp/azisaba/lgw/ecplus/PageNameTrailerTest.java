package jp.azisaba.lgw.ecplus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageNameTrailerTest {

    @Test
    void roundTripsPageNames() throws Exception {
        Map<Integer, String> names = new HashMap<>();
        names.put(0, "鉱石");
        names.put(17, "武器");
        names.put(269, "エンダー");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            InventoryData.writeNamesTrailer(out, names);
        }

        Map<Integer, String> read = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            InventoryData.readNamesTrailer(in, read);
        }
        assertEquals(names, read);
    }

    @Test
    void skipsMissingTrailer() throws Exception {
        Map<Integer, String> read = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]))) {
            InventoryData.readNamesTrailer(in, read);
        }
        assertTrue(read.isEmpty());
    }
}
