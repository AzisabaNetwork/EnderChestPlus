package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryData {
    private static final int SERIALIZATION_MAGIC = 0x45435032; // ECP2
    private final Map<Integer, Inventory> inventories = new HashMap<>();
    private final UUID uuid;
    private final DatabaseManager database;
    private boolean loadedFromLegacyYaml;

    public InventoryData(UUID uuid, DatabaseManager database) {
        this(uuid, database, true);
    }

    private InventoryData(UUID uuid, DatabaseManager database, boolean load) {
        this.uuid = uuid;
        this.database = database;
        if (load) load();
    }

    public int addItemInEmptySlot(ItemStack item) {
        for (int i = 0; i < EnderChestPlus.MAX_MAIN_INVENTORY_PAGES * 54; i++) {
            Inventory inventory = inventories.get(i);
            if (inventory == null) continue;
            int slot = inventory.firstEmpty();
            if (slot >= 0) {
                inventory.setItem(slot, item);
                return i;
            }
        }
        return -1;
    }

    private void load() {
        try {
            byte[] bytes = database.load(uuid);
            if (bytes != null) {
                if (isCurrentFormat(bytes)) {
                    deserialize(bytes);
                } else if (loadLegacyYaml()) {
                    loadedFromLegacyYaml = save(false);
                } else {
                    throw new IOException("Unsupported legacy inventory data format");
                }
            } else if (loadLegacyYaml()) {
                loadedFromLegacyYaml = save(false);
            }
        } catch (SQLException | IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not load inventory data for " + uuid, e);
        }
        for (int i = 0; i < 18; i++) inventories.computeIfAbsent(i, this::createInventory);
    }

    private boolean loadLegacyYaml() throws IOException, InvalidConfigurationException {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (!file.isFile()) return false;
        String yaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        yaml = yaml.replaceAll("(?m)^\\s*internal:.*(?:\\R|$)", "");
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        for (String pageKey : config.getKeys(false)) {
            int page = positiveInt(pageKey);
            if (page < 0 || config.getConfigurationSection(pageKey) == null) continue;
            Inventory inventory = createInventory(page);
            for (String slotKey : config.getConfigurationSection(pageKey).getKeys(false)) {
                int slot = positiveInt(slotKey);
                ItemStack item = config.getItemStack(pageKey + "." + slotKey);
                if (slot >= 0 && slot < inventory.getSize() && item != null && item.getType() != Material.AIR) {
                    inventory.setItem(slot, item);
                }
            }
            inventories.put(page, inventory);
        }
        return true;
    }

    private void deserialize(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != SERIALIZATION_MAGIC) throw new IOException("Unsupported inventory data format");
            int pages = input.readInt();
            for (int p = 0; p < pages; p++) {
                int page = input.readInt();
                int size = input.readInt();
                Inventory inventory = createInventory(page);
                for (int slot = 0; slot < size; slot++) {
                    int itemLength = input.readInt();
                    ItemStack item = itemLength == 0 ? null : ItemStack.deserializeBytes(input.readNBytes(itemLength));
                    if (slot < inventory.getSize()) inventory.setItem(slot, item);
                }
                inventories.put(page, inventory);
            }
        }
    }

    public synchronized boolean save(boolean ignoredAsyncSave) {
        try {
            database.save(uuid, serialize());
            return true;
        } catch (SQLException | IOException e) {
            Bukkit.getLogger().severe("Could not save inventory data for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    private byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(SERIALIZATION_MAGIC);
            output.writeInt(inventories.size());
            for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeInt(entry.getValue().getSize());
                for (ItemStack item : entry.getValue().getContents()) {
                    byte[] itemBytes = item == null ? new byte[0] : item.serializeAsBytes();
                    output.writeInt(itemBytes.length);
                    output.write(itemBytes);
                }
            }
        }
        return bytes.toByteArray();
    }

    public Inventory getInventory(int num) { return inventories.get(num); }

    public boolean wasLoadedFromLegacyYaml() { return loadedFromLegacyYaml; }

    public void initializeInventory(int page) { inventories.put(page, createInventory(page)); }

    public InventoryData migrateAs(UUID targetUuid) {
        InventoryData data = new InventoryData(targetUuid, database, false);
        for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
            Inventory copy = data.createInventory(entry.getKey());
            copy.setContents(entry.getValue().getContents());
            data.inventories.put(entry.getKey(), copy);
        }
        return data;
    }

    private Inventory createInventory(int page) {
        return Bukkit.createInventory(null, 54,
                Chat.component(Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, page + 1)));
    }

    private static int positiveInt(String value) {
        try { int i = Integer.parseInt(value); return i < 0 ? -1 : i; }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static boolean isCurrentFormat(byte[] bytes) {
        if (bytes.length < Integer.BYTES) return false;
        return ((bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF)
                == SERIALIZATION_MAGIC;
    }
}
