package jp.azisaba.lgw.ecplus.listeners;

import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

@RequiredArgsConstructor
public class LoadInventoryDataListener implements Listener {

    private final InventoryLoader loader;

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // 非同期で事前読み込みを行う
        EnderChestPlus.newChain()
                .async(() -> loader.loadInventoryData(p))
                .execute();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (p.getOpenInventory() != null && InventoryOpenListener.getPlayerOpenInventoryTitle(p).startsWith(EnderChestPlus.enderChestTitlePrefix)) {
            ItemStack cursor = p.getOpenInventory().getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                Inventory top = p.getOpenInventory().getTopInventory();
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(cursor);
                if (!leftover.isEmpty() && top != null) {
                    leftover = top.addItem(cursor);
                }
                if (!leftover.isEmpty()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), cursor);
                }
                p.getOpenInventory().setCursor(null);
            }
            p.closeInventory();
        }

        // 鯖間移動時のデータ整合性のため同期的に保存してアンロード
        loader.saveAndUnload(uuid);
    }
}
