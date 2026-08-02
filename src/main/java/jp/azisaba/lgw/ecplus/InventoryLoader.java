package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import jp.azisaba.lgw.ecplus.utils.ItemHelper;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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

    public InventoryData loadInventoryData(UUID uuid) {
        return invs.computeIfAbsent(uuid, key -> new InventoryData(key, database));
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

            if (success && Bukkit.getPlayer(uuid) == null) {
                invs.remove(uuid, data);
            }

            count++;
        }

        return count;
    }

    public boolean saveAndUnload(UUID uuid) {
        InventoryData data = invs.get(uuid);
        if (data == null) return false;
        boolean saved = data.save(false);
        if (saved) invs.remove(uuid, data);
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
