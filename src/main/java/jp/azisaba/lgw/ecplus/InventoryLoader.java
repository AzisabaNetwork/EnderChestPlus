package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.listeners.InventoryOpenListener;
import jp.azisaba.lgw.ecplus.mythic.MythicItemRepairer;
import jp.azisaba.lgw.ecplus.utils.Chat;
import jp.azisaba.lgw.ecplus.utils.ItemHelper;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class InventoryLoader {

    private static ItemStack lowPane = null, midiumPane = null, highPane = null;
    private final EnderChestPlus plugin;
    private final DatabaseManager database;
    private final ConcurrentHashMap<UUID, InventoryData> invs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, UUID> adminLookingAt = new ConcurrentHashMap<>();

    public static Inventory getMainInventory(InventoryData data, int index) {
        if (index < 0 || EnderChestPlus.MAX_MAIN_INVENTORY_PAGES - 1 < index) {
            return null;
        }
        Inventory mainInv = Bukkit.createInventory(null, 9 * 6, Chat.component(EnderChestPlus.mainEnderChestTitle + Chat.f(" &a- &e{0}", index + 1)));

        for (int i = 0; i < mainInv.getSize(); i++) {
            Inventory inv = data.getInventory((index * 54) + i);
            if (inv != null) {
                double percentage = getPercentage(inv);
                ItemStack item = null;

                if (percentage < 0.333) {
                    item = getLowPane();
                } else if (percentage < 0.666) {
                    item = getMidiumPane();
                } else {
                    item = getHighPane();
                }

                ItemHelper.setDisplayName(item, Chat.f("&aページ&e{0}&aを開く", (index * 54) + i + 1));
                ItemHelper.setLore(item, getLore(inv, 5));
                mainInv.setItem(i, item);
            } else {
                ItemStack item = getBuyPane((index * 54) + i);
                mainInv.setItem(i, item);
            }
        }

        return mainInv;
    }

    public static Inventory getBuyInventory(int page) {
        Inventory inv = Bukkit.createInventory(null, 9 * 1, Chat.component(Chat.f("{0}&a - &cUnlock Page {1}", EnderChestPlus.enderChestTitlePrefix, page + 1)));
        ItemStack confirm = ItemHelper.createItem(Material.LIME_STAINED_GLASS_PANE, Chat.f("&a確定"));
        ItemStack cancel = ItemHelper.createItem(Material.RED_STAINED_GLASS_PANE, Chat.f("&cキャンセル"));
        ItemStack sign = ItemHelper.createItem(Material.OAK_SIGN, Chat.f("&aページ&e{0}&aを購入しますか？", page + 1));

        inv.setItem(0, cancel);
        inv.setItem(1, cancel);
        inv.setItem(2, cancel);
        inv.setItem(3, cancel);
        inv.setItem(4, sign);
        inv.setItem(5, confirm);
        inv.setItem(6, confirm);
        inv.setItem(7, confirm);
        inv.setItem(8, confirm);

        return inv;
    }

    public static ItemStack getBuyPane(int page) {
        ItemStack buyPane = ItemHelper.createItem(Material.BLACK_STAINED_GLASS_PANE, Chat.f("&eクリックでページ&a{0}&eを購入する", page + 1));

        List<String> lore = new ArrayList<>(Arrays.asList(Chat.f("&6解禁コスト&a:")));
        if (0 <= page && page < 18) {
            lore.add(Chat.f("&7  - &cなし"));
        } else if (18 <= page && page < 27) {
            lore.add(Chat.f("&7  - &a&l500$"));
        } else if (27 <= page && page < 36) {
            lore.add(Chat.f("&7  - &a&l1000$"));
        } else if (36 <= page && page < 45) {
            lore.add(Chat.f("&7  - &a&l1500$"));
        } else if (45 <= page && page < 54) {
            lore.add(Chat.f("&7  - &a&l2000$"));
        } else if (54 <= page && page < 81) {
            lore.add(Chat.f("&7  - &a&l2500$"));
        } else if (81 <= page) {
            lore.add(Chat.f("&7  - &a&l3000$"));
        }

        ItemHelper.setLore(buyPane, lore);

        return buyPane;
    }

    public static ItemStack getLowPane() {
        if (lowPane == null) {
            lowPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        }
        return lowPane.clone();
    }

    public static ItemStack getMidiumPane() {
        if (midiumPane == null) {
            midiumPane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        }
        return midiumPane.clone();
    }

    public static ItemStack getHighPane() {
        if (highPane == null) {
            highPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        }
        return highPane.clone();
    }

    private static double getPercentage(Inventory inv) {
        int total = inv.getSize();
        int empty = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                empty++;
            }
        }

        return (double) (total - empty) / (double) total;
    }

    private static List<String> getLore(Inventory inv, int lines) {
        List<String> lore = new ArrayList<>();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            String msg = Chat.f("&r");
            if (item.hasItemMeta() && ItemHelper.getDisplayName(item) != null) {
                msg += ItemHelper.getDisplayName(item);
            } else {
                msg += item.getType().toString();
            }

            if (item.getAmount() > 1) {
                msg += Chat.f("&7 x{0}", item.getAmount());
            }

            lore.add(msg);
        }

        if (lore.size() >= lines) {
            lore.set(lines - 1, Chat.f("&7(その他{0}アイテム)", lore.size() - (lines - 1)));
            lore = lore.subList(0, lines);
        }

        return lore;
    }

    public void loadInventoryData(Player p) {
        loadInventoryData(p.getUniqueId());
    }

    public boolean isInventoryOpen(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            String title = InventoryOpenListener.getPlayerOpenInventoryTitle(p);
            if (title.startsWith(EnderChestPlus.enderChestTitlePrefix)) {
                return true;
            }
        }
        for (Map.Entry<Player, UUID> entry : adminLookingAt.entrySet()) {
            if (uuid.equals(entry.getValue())) {
                Player admin = entry.getKey();
                if (admin.isOnline()) {
                    String title = InventoryOpenListener.getPlayerOpenInventoryTitle(admin);
                    if (title.startsWith(EnderChestPlus.enderChestTitlePrefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public InventoryData loadInventoryData(UUID uuid) {
        return loadInventoryData(uuid, true);
    }

    private InventoryData loadInventoryData(UUID uuid, boolean repairMythicItems) {
        if (isInventoryOpen(uuid)) {
            InventoryData current = invs.get(uuid);
            if (current != null) {
                return current;
            }
        }
        InventoryData data = new InventoryData(uuid, database);
        invs.put(uuid, data);
        if (repairMythicItems) scheduleMythicRepair(uuid, data);
        return data;
    }

    /** Runs the Mythic item repair over every page on the main thread (MythicMobs is not thread-safe). */
    private void scheduleMythicRepair(UUID uuid, InventoryData data) {
        MythicItemRepairer repairer = EnderChestPlus.getMythicRepairer();
        if (repairer == null || !plugin.isEnabled()
                || !plugin.getConfig().getBoolean("MythicItemCompat.RepairOnLoad", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (invs.get(uuid) != data) return; // replaced or unloaded meanwhile
            int changes = 0;
            for (Inventory inventory : data.getInventories()) changes += repairer.repairInventory(inventory);
            if (changes > 0) {
                MigrationLog.info("Repaired " + changes + " Mythic item field(s) in the ender chest of " + uuid + ".");
            }
        });
    }

    /**
     * Imports every UUID-named inventory YAML file left by versions that predate database storage.
     * Files already present in the database are left untouched unless force is true.
     *
     * @return the number of valid legacy inventory files processed
     */
    public int migrateLegacyYamlData() {
        return migrateLegacyYamlData(false);
    }

    public int migrateLegacyYamlData(boolean force) {
        File[] files = EnderChestPlus.getInventoryDataFile().listFiles((directory, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        int processed = 0;
        for (File file : files) {
            if (!plugin.isEnabled()) {
                break;
            }
            String filename = file.getName();
            try {
                UUID uuid = UUID.fromString(filename.substring(0, filename.length() - ".yml".length()));
                if (force) {
                    if (remigrate(uuid)) {
                        processed++;
                    }
                } else {
                    byte[] existing = database.load(uuid);
                    if (existing == null) {
                        loadInventoryData(uuid, false);
                        processed++;
                    }
                    if (Bukkit.getPlayer(uuid) == null && !isInventoryOpen(uuid)) {
                        invs.remove(uuid);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore non-UUID YAML files in the legacy inventory directory.
            } catch (Exception e) {
                MigrationLog.warn("Could not migrate legacy inventory " + filename + ": " + e.getMessage());
            }
        }
        return processed;
    }

    public boolean remigrate(UUID uuid) {
        return remigrate(uuid, uuid);
    }

    /**
     * Re-imports the legacy YAML of sourceUuid into the inventory of targetUuid.
     */
    public boolean remigrate(UUID uuid, UUID sourceUuid) {
        // The Mythic repair is scheduled only after the reload: it runs on the main thread and must
        // neither race with the reload nor repair the pages that the reload throws away.
        InventoryData data = loadInventoryData(uuid, false);
        try {
            boolean success = data.reloadFromLegacyYaml(sourceUuid);
            if (Bukkit.getPlayer(uuid) == null) {
                invs.remove(uuid, data);
            } else {
                scheduleMythicRepair(uuid, data);
            }
            return success;
        } catch (Exception e) {
            MigrationLog.warn("Could not remigrate inventory for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Restores only the StorageBoxes of one player's ender chest from the legacy YAML (see
     * {@link InventoryData#restoreStorageBoxesFromLegacyYaml}). Call from an async thread: the boxes are replaced
     * on the main thread and the result is saved before returning.
     *
     * @return the report lines; the first line is the summary
     */
    public List<String> restoreStorageBoxes(UUID uuid, boolean apply) throws Exception {
        if (apply && isInventoryOpen(uuid)) return List.of("エンダーチェストが開かれているためスキップしました");
        InventoryData data = invs.get(uuid);
        if (data == null) {
            if (database.load(uuid) == null) return List.of("エンダーチェストのデータがありません");
            data = loadInventoryData(uuid, false);
        }
        InventoryData target = data;
        List<String> report = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            if (apply && isInventoryOpen(uuid)) return List.of("エンダーチェストが開かれたためスキップしました");
            return target.restoreStorageBoxesFromLegacyYaml(apply);
        }).get(60, java.util.concurrent.TimeUnit.SECONDS);
        if (apply && InventoryData.restoredCount(report) > 0 && !target.save(false)) {
            List<String> failed = new ArrayList<>(report);
            failed.add(0, "保存に失敗しました");
            report = failed;
        }
        if (Bukkit.getPlayer(uuid) == null && !isInventoryOpen(uuid)) invs.remove(uuid, target);
        return report;
    }

    /** UUIDs of the legacy YAML files that have a row in the database (targets of a bulk StorageBox restore). */
    public List<UUID> legacyYamlOwners() throws java.sql.SQLException {
        File[] files = EnderChestPlus.getInventoryDataFile().listFiles((directory, name) -> name.endsWith(".yml"));
        List<UUID> owners = new ArrayList<>();
        if (files == null) return owners;
        for (File file : files) {
            String filename = file.getName();
            UUID uuid;
            try {
                uuid = UUID.fromString(filename.substring(0, filename.length() - ".yml".length()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (invs.containsKey(uuid) || database.load(uuid) != null) owners.add(uuid);
        }
        return owners;
    }

    public InventoryData getInventoryData(Player p) {
        return getInventoryData(p.getUniqueId());
    }

    public InventoryData getInventoryData(UUID uuid) {
        return invs.get(uuid);
    }

    public int saveAllInventoryData(boolean asyncSave) {

        if (invs.size() <= 0) {
            return 0;
        }

        int count = 0;

        for (Map.Entry<UUID, InventoryData> entry : new ArrayList<>(invs.entrySet())) {
            UUID uuid = entry.getKey();
            InventoryData data = entry.getValue();
            boolean success = data.save(asyncSave);

            if (success && Bukkit.getPlayer(uuid) == null && !isInventoryOpen(uuid)) {
                invs.remove(uuid, data);
            }

            count++;
        }

        return count;
    }

    public void unload(UUID uuid) {
        if (!isInventoryOpen(uuid) && Bukkit.getPlayer(uuid) == null) {
            invs.remove(uuid);
        }
    }

    public boolean saveAndUnload(UUID uuid) {
        InventoryData data = invs.get(uuid);
        if (data == null) {
            invs.remove(uuid);
            return false;
        }
        boolean saved = data.save(false);
        invs.remove(uuid);
        return saved;
    }

    public void setLookingAt(Player p, UUID uuid) {
        if (uuid == null) {
            adminLookingAt.remove(p);
            return;
        }

        adminLookingAt.put(p, uuid);
    }

    public UUID getLookingAt(Player p) {
        return adminLookingAt.get(p);
    }

    public boolean migrate(UUID from, UUID to) {
        InventoryData data = loadInventoryData(from);
        InventoryData migratedData = data.migrateAs(to);

        invs.put(to, migratedData);
        return true;
    }
}
