package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryData {
    private final Map<Integer, Inventory> inventories = new HashMap<>();
    private final UUID uuid;
    private final DatabaseManager database;

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
                deserialize(bytes);
            } else if (loadLegacyYaml()) {
                save(false);
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Could not load inventory data for " + uuid, e);
        }
        for (int i = 0; i < 18; i++) inventories.computeIfAbsent(i, this::createInventory);
    }

    private boolean loadLegacyYaml() {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (!file.isFile()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
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

    private void deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int pages = input.readInt();
            for (int p = 0; p < pages; p++) {
                int page = input.readInt();
                int size = input.readInt();
                Inventory inventory = createInventory(page);
                for (int slot = 0; slot < size; slot++) {
                    ItemStack item = (ItemStack) input.readObject();
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
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(inventories.size());
            for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeInt(entry.getValue().getSize());
                for (ItemStack item : entry.getValue().getContents()) output.writeObject(item);
            }
        }
        return bytes.toByteArray();
    }

    public Inventory getInventory(int num) { return inventories.get(num); }

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
                Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, page + 1));
    }

    private static int positiveInt(String value) {
        try { int i = Integer.parseInt(value); return i < 0 ? -1 : i; }
        catch (NumberFormatException ignored) { return -1; }
    }
}
