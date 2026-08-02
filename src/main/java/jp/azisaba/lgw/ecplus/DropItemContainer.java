package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

@RequiredArgsConstructor
public class DropItemContainer {

    private final EnderChestPlus plugin;

    private final HashMap<String, DropItem> items = new HashMap<>();
    private final HashMap<String, Inventory> inventories = new HashMap<>();

    public String addItem(Player p, ItemStack item) {

        String id = RandomStringUtils.randomAlphabetic(8);
        while (items.containsKey(id)) {
            id = RandomStringUtils.randomAlphabetic(8);
        }

        DropItem itemData = new DropItem(p.getUniqueId(), item);

        items.put(id, itemData);

        return id;

    }

    public Inventory getInventory(String id) {
        DropItem data = getItemData(id);

        if (data == null) {
            return null;
        }

        if (inventories.containsKey(id)) {
            return inventories.get(id);
        }

        Inventory inv = Bukkit.createInventory(null, 9 * 3, Chat.component(Chat.f("&aDropped Item &7- &e{0}", id)));
        inv.setItem(13, data.getItemStack());

        inventories.put(id, inv);

        return inv;
    }

    public DropItem getItemData(String id) {
        return items.getOrDefault(id, null);
    }

    public void deleteItemData(String id) {
        if (items.containsKey(id)) {
            items.remove(id);
        }
        if (inventories.containsKey(id)) {
            inventories.remove(id);
        }
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "DroppedItems.yml");

        YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);

        if (conf.getConfigurationSection("") == null) {
            return;
        }

        for (String key : conf.getConfigurationSection("").getKeys(false)) {
            String id = key;
            ItemStack item = conf.getItemStack(key + ".Item");
            UUID uuid = UUID.fromString(conf.getString(key + ".Owner"));

            items.put(id, new DropItem(uuid, item));
        }
    }

    public void save() {
        File file = new File(plugin.getDataFolder(), "DroppedItems.yml");

        YamlConfiguration conf = new YamlConfiguration();

        for (String id : items.keySet()) {
            conf.set(id + ".Owner", items.get(id).getOwner().toString());
            conf.set(id + ".Item", items.get(id).getItemStack());
        }

        try {
            conf.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Getter
    @RequiredArgsConstructor
    public class DropItem {

        private final UUID owner;
        private final ItemStack itemStack;
    }
}
