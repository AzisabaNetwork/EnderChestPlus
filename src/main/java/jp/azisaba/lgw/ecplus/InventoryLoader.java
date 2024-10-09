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

@RequiredArgsConstructor
public class InventoryLoader {

    private static ItemStack lowPane = null, midiumPane = null, highPane = null;
    private final EnderChestPlus plugin;
    private final HashMap<UUID, InventoryData> invs = new HashMap<>();
    private final HashMap<Player, UUID> adminLookingAt = new HashMap<>();

    public static Inventory getMainInventory(InventoryData data, int index) {
        if (index < 0 || EnderChestPlus.MAX_MAIN_INVENTORY_PAGES - 1 < index) {
            return null;
        }
        Inventory mainInv = Bukkit.createInventory(null, 9 * 6, EnderChestPlus.mainEnderChestTitle + Chat.f(" &a- &e{0}", index + 1));

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
        Inventory inv = Bukkit.createInventory(null, 9 * 1, Chat.f("{0}&a - &cUnlock Page {1}", EnderChestPlus.enderChestTitlePrefix, page + 1));
        ItemStack confirm = ItemHelper.createItem(Material.LIME_STAINED_GLASS_PANE, 5, Chat.f("&a確定"));
        ItemStack cancel = ItemHelper.createItem(Material.RED_STAINED_GLASS_PANE, 14, Chat.f("&cキャンセル"));
        ItemStack sign = ItemHelper.createItem(Material.OAK_SIGN, 0, Chat.f("&aページ&e{0}&aを購入しますか？", page + 1));

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
        ItemStack buyPane = ItemHelper.createItem(Material.BLACK_STAINED_GLASS_PANE, 15, Chat.f("&eクリックでページ&a{0}&eを購入する", page + 1));

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
            lowPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1, (byte) 5);
        }
        return lowPane;
    }

    public static ItemStack getMidiumPane() {
        if (midiumPane == null) {
            midiumPane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE, 1, (byte) 4);
        }
        return midiumPane;
    }

    public static ItemStack getHighPane() {
        if (highPane == null) {
            highPane = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1, (byte) 14);
        }
        return highPane;
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
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                msg += item.getItemMeta().getDisplayName();
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

    public void loadInventoryData(UUID uuid) {
        if (!invs.containsKey(uuid)) {
            InventoryData data = new InventoryData(uuid);

            // 非同期で実行されていた場合に ConcurrentModificationException の発生を防ぐ
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                invs.put(uuid, data);
            }, 0L);
        }
    }

    public InventoryData getInventoryData(Player p) {
        return getInventoryData(p.getUniqueId());
    }

    public InventoryData getInventoryData(UUID uuid) {
        if (invs.containsKey(uuid)) {
            return invs.get(uuid);
        }
        return null;
    }

    public int saveAllInventoryData(boolean asyncSave) {

        if (invs.size() <= 0) {
            return 0;
        }

        int count = 0;

        for (UUID uuid : new ArrayList<>(invs.keySet())) {
            boolean success = invs.get(uuid).save(asyncSave);

            if (success && Bukkit.getPlayer(uuid) == null) {
                invs.remove(uuid);
            }

            count++;
        }

        return count;
    }

    public void setLookingAt(Player p, UUID uuid) {
        if (uuid == null) {
            if (adminLookingAt.containsKey(p)) {
                adminLookingAt.remove(p);
            }
            return;
        }

        adminLookingAt.put(p, uuid);
    }

    public UUID getLookingAt(Player p) {
        if (adminLookingAt.containsKey(p)) {
            return adminLookingAt.get(p);
        }
        return null;
    }

    public boolean migrate(UUID from, UUID to) {
        if (!invs.containsKey(from)) {
            loadInventoryData(from);
        }

        InventoryData data = invs.get(from);
        if (data == null) {
            return false;
        }
        InventoryData migratedData = data.migrateAs(to);

        invs.put(to, migratedData);
        return true;
    }
}
