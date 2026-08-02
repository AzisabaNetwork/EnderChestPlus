package jp.azisaba.lgw.ecplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ItemHelper {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public static ItemStack create(Material type) {
        return new ItemStack(type);
    }

    public static ItemStack create(Material type, String title, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(toComponent(title));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(ItemHelper::toComponent).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(toComponent(displayName));

        if (lore == null || lore.length == 0) {
            meta.lore(List.of());
        } else {
            meta.lore(Arrays.stream(lore).map(ItemHelper::toComponent).toList());
        }

        item.setItemMeta(meta);
        return item;
    }

    public static void addHideEnchant(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    public static void setDisplayName(ItemStack item, String displayName) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(toComponent(displayName));
        item.setItemMeta(meta);
    }

    public static void setLore(ItemStack item, List<String> args) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(args.stream().map(ItemHelper::toComponent).toList());
        item.setItemMeta(meta);
    }

    public static String getDisplayName(ItemStack item) {
        Component displayName = item.getItemMeta().displayName();
        return displayName == null ? null : LEGACY_SERIALIZER.serialize(displayName);
    }

    private static Component toComponent(String text) {
        return LEGACY_SERIALIZER.deserialize(text == null ? "" : text);
    }
}
