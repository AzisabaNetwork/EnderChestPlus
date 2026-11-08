package jp.azisaba.lgw.ecplus.listeners;

import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.mythic.MythicItemRepairer;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** Repairs Mythic items on an ender chest page when it is opened, and items put into it when it is closed. */
@RequiredArgsConstructor
public class MythicRepairListener implements Listener {
    private final EnderChestPlus plugin;
    private final MythicItemRepairer repairer;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        repairIfPage(event.getView());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        repairIfPage(event.getView());
    }

    private void repairIfPage(InventoryView view) {
        if (!plugin.getConfig().getBoolean("MythicItemCompat.RepairOnOpen", true)) return;
        Inventory top = view.getTopInventory();
        if (top.getHolder() != null || top.getSize() != 54) return;
        String title = Chat.legacy(view.title());
        // pages only: not the main (page list) screen
        if (!title.startsWith(EnderChestPlus.enderChestTitlePrefix) || title.startsWith(EnderChestPlus.mainEnderChestTitle)) {
            return;
        }
        repairer.repairInventory(top);
    }
}
