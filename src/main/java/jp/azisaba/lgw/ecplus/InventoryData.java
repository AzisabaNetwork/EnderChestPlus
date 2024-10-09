package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class InventoryData {

    private final HashMap<Integer, Inventory> inventories = new HashMap<>();
    private UUID uuid = null;

    public InventoryData(Player p) {
        this(p.getUniqueId(), true);
    }

    public InventoryData(UUID uuid) {
        this(uuid, true);
    }

    private InventoryData(UUID uuid, boolean load) {
        this.uuid = uuid;
        if (load) {
            load();
        }
    }

    public int addItemInEmptySlot(ItemStack item) {

        int page = -1;

        for (int i = 0; i < EnderChestPlus.MAX_MAIN_INVENTORY_PAGES * 54; i++) {
            if (!inventories.containsKey(i)) {
                continue;
            }

            Inventory inv = inventories.get(i);

            int slot = inv.firstEmpty();
            if (slot < 0) {
                continue;
            }

            inv.setItem(slot, item);
            page = i;
            break;
        }

        return page;
    }

    private void load() {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid.toString() + ".yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
        if (conf.getConfigurationSection("") != null) {

            for (String key : conf.getConfigurationSection("").getKeys(false)) {
                int keyInt = isPositive(key);
                if (keyInt < 0) {
                    continue;
                }

                if (conf.getConfigurationSection(key) == null) {
                    continue;
                }

                Inventory inv = Bukkit.createInventory(null, 9 * 6, Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, keyInt + 1));
                for (String key2 : conf.getConfigurationSection(key).getKeys(false)) {
                    ItemStack item = conf.getItemStack(key + "." + key2, null);
                    if (item == null) {
                        continue;
                    }

                    int key2Int = isPositive(key2);
                    if (key2Int < 0) {
                        continue;
                    }

                    inv.setItem(key2Int, item);
                }

                inventories.put(keyInt, inv);
            }
        }

        for (int i = 0; i < 18; i++) {

            if (inventories.containsKey(i)) {
                continue;
            }

            Inventory inv = Bukkit.createInventory(null, 6 * 9, Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, i + 1));
            inventories.put(i, inv);
        }
    }

    public boolean save(boolean asyncSave) {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid.toString() + ".yml");
        YamlConfiguration conf = new YamlConfiguration();

        for (int invNum : inventories.keySet()) {
            Inventory inv = inventories.get(invNum);
            boolean empty = true;

            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                conf.set(invNum + "." + i, item);
                empty = false;
            }

            if (empty) {
                conf.set(invNum + ".0", new ItemStack(Material.AIR));
            }
        }

        if (asyncSave) {

            new Thread() {
                @Override
                public void run() {
                    try {
                        conf.save(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

        } else {
            try {
                conf.save(file);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public Inventory getInventory(int num) {
        return inventories.get(num);
    }

    public void initializeInventory(int page) {
        inventories.put(page,
                Bukkit.createInventory(null, 9 * 6, Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, page + 1)));
    }

    public InventoryData migrateAs(UUID uuid) {
        InventoryData data = new InventoryData(uuid, false);
        for (int i = 0; i < EnderChestPlus.MAX_MAIN_INVENTORY_PAGES * 54; i++) {
            Inventory inv = inventories.getOrDefault(i, null);
            if (inv == null) {
                continue;
            }
            Inventory newInv = Bukkit.createInventory(null, inv.getSize());

            for (int index = 0; index < inv.getSize(); index++) {
                ItemStack item = inv.getItem(index);
                newInv.setItem(index, item);
            }

            data.inventories.put(i, newInv);
        }
        return data;
    }

    private int isPositive(String str) {
        try {
            int i = Integer.parseInt(str);
            if (i < 0) {
                return -1;
            }
            return i;
        } catch (Exception e) {
            return -1;
        }
    }
}
