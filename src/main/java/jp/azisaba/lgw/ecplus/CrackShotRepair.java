package jp.azisaba.lgw.ecplus;

import net.azisaba.crackshot.api.CrackShotApi;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Gives CrackShot weapons from 1.15.2 the {@code crackshot:weapon_id} tag through CrackShot's API
 * ({@link CrackShotApi#tagLegacyWeapon}). CrackShot finds the weapon by its name without color codes, and only when
 * exactly one weapon has that name and the item's material matches its {@code Item_Type}.
 */
final class CrackShotRepair {
    private CrackShotRepair() {}

    /**
     * Tags every untagged CrackShot weapon of the inventory, including those inside shulker boxes.
     *
     * @return the number of tagged weapons (0 when CrackShot is not enabled)
     */
    static int repairInventory(Inventory inventory, Consumer<String> log) {
        CrackShotApi api = api();
        return api == null ? 0 : repairInventory(api, inventory, log);
    }

    private static int repairInventory(CrackShotApi api, Inventory inventory, Consumer<String> log) {
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()
                    && blockStateMeta.getBlockState() instanceof Container container) {
                int nested = repairInventory(api, container.getInventory(), log);
                if (nested > 0) {
                    blockStateMeta.setBlockState(container);
                    item.setItemMeta(blockStateMeta);
                    inventory.setItem(slot, item);
                    repaired += nested;
                }
                continue;
            }
            String weaponId = api.tagLegacyWeapon(item);
            if (weaponId != null) {
                inventory.setItem(slot, item);
                log.accept("CrackShot weapon '" + weaponId + "' tagged");
                repaired++;
            }
        }
        return repaired;
    }

    /** CrackShot's API, or null when CrackShot is not enabled (or is a version without the API). */
    private static CrackShotApi api() {
        Plugin crackShot = Bukkit.getPluginManager().getPlugin("CrackShot");
        if (crackShot == null || !crackShot.isEnabled()) return null;
        try {
            return CrackShotApi.getOrNull();
        } catch (LinkageError e) {
            return null;
        }
    }
}
