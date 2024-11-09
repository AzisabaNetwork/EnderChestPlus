package jp.azisaba.lgw.ecplus.listeners;

import jp.azisaba.lgw.ecplus.DropItemContainer;
import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryData;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

@RequiredArgsConstructor
public class EnderChestListener implements Listener {

    private final EnderChestPlus plugin;
    private final InventoryLoader loader;
    private final DropItemContainer dropItemContainer;

    @SuppressWarnings("deprecation")
    @EventHandler
    public void clickInventory(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        ItemStack item = e.getCurrentItem();
        Inventory inv = e.getInventory();
        Inventory clickedInv = e.getClickedInventory();
        String invname = InventoryOpenListener.getPlayerOpenInventoryTitle(p);

        if (!invname.startsWith(EnderChestPlus.mainEnderChestTitle)) {
            return;
        }

        e.setCancelled(true);

        if (clickedInv == null || !clickedInv.equals(inv) || item == null) {
            return;
        }

        if (item.getData().getData() == (byte) 15) {
            String pageNumStr = Chat.r(item.getItemMeta().getDisplayName());
            pageNumStr = pageNumStr.substring(8, pageNumStr.indexOf("を購入する"));
            int pageNum = Integer.parseInt(pageNumStr);
            p.openInventory(InventoryLoader.getBuyInventory(pageNum - 1));
        } else {
            int invNum = -1;
            try {
                String title = Chat.r(item.getItemMeta().getDisplayName());
                title = title.substring(3, title.indexOf("を開く"));
                invNum = Integer.parseInt(title);
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

            UUID looking = loader.getLookingAt(p);
            InventoryData data;
            if (looking != null) {
                data = loader.getInventoryData(looking);
            } else {
                data = loader.getInventoryData(p);
            }
            p.openInventory(data.getInventory(invNum - 1));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
        }
    }

    @EventHandler
    public void backToMainInventory(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        Inventory clickedInv = e.getClickedInventory();
        String invname = InventoryOpenListener.getPlayerOpenInventoryTitle(p);

        if (invname.startsWith(EnderChestPlus.mainEnderChestTitle)) {
            return;
        }
        if (!invname.startsWith(EnderChestPlus.enderChestTitlePrefix)) {
            return;
        }
        if (e.getClick() != ClickType.MIDDLE) {
            return;
        }

        if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        String title = invname;
        int currentInventory = Integer.parseInt(title.substring(title.indexOf("Page") + 5)) - 1;
        int mainInventoryIndex = currentInventory / 54;

        if (clickedInv == null) {
            InventoryData data;
            UUID looking = loader.getLookingAt(p);
            if (looking != null) {
                data = loader.getInventoryData(looking);
            } else {
                data = loader.getInventoryData(p);
            }
            Inventory mainInv = InventoryLoader.getMainInventory(data, mainInventoryIndex);
            p.openInventory(mainInv);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
        }
    }

    @EventHandler
    public void switchMainInventoryPage(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        Inventory clickedInv = e.getClickedInventory();
        String invname = InventoryOpenListener.getPlayerOpenInventoryTitle(p);

        if (!invname.startsWith(EnderChestPlus.mainEnderChestTitle)) {
            return;
        }
        if (clickedInv != null) {
            return;
        }
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) {
            return;
        }

        if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        String invTitle = invname;
        int currentOpeningMainInventoryIndex = Integer.parseInt(invTitle.substring(invTitle.lastIndexOf(Chat.f("&a-")) + 6).trim()) - 1;

        if ((currentOpeningMainInventoryIndex == 0 && e.getClick() == ClickType.LEFT)
                || (currentOpeningMainInventoryIndex == EnderChestPlus.MAX_MAIN_INVENTORY_PAGES - 1 && e.getClick() == ClickType.RIGHT)) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        int nextPageIndex = currentOpeningMainInventoryIndex;
        if (e.getClick() == ClickType.LEFT) {
            nextPageIndex -= 1;
        } else {
            nextPageIndex += 1;
        }

        InventoryData data;
        UUID looking = loader.getLookingAt(p);
        if (looking != null) {
            data = loader.getInventoryData(looking);
        } else {
            data = loader.getInventoryData(p);
        }

        Inventory nextOpenMainInv = InventoryLoader.getMainInventory(data, nextPageIndex);
        if (nextOpenMainInv != null) {
            p.openInventory(nextOpenMainInv);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }

    @EventHandler
    public void nextOrBackInventory(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        Inventory clickedInv = e.getClickedInventory();
        String invname = InventoryOpenListener.getPlayerOpenInventoryTitle(p);

        if (invname.startsWith(EnderChestPlus.mainEnderChestTitle)) {
            return;
        }
        if (!invname.startsWith(EnderChestPlus.enderChestTitlePrefix)) {
            return;
        }
        if (clickedInv != null) {
            return;
        }
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) {
            return;
        }

        if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        String title = Chat.r(invname);
        int currentInventory = Integer.parseInt(title.substring(title.indexOf("Page") + 5, title.length())) - 1;
        int addNum = 1;
        if (e.getClick() == ClickType.LEFT) {
            addNum = -1;
        }

        InventoryData data;
        UUID looking = loader.getLookingAt(p);
        if (looking != null) {
            data = loader.getInventoryData(looking);
        } else {
            data = loader.getInventoryData(p);
        }

        int nextInvNum = currentInventory;
        Inventory nextInv = null;
        while (nextInvNum >= 0 && nextInvNum <= 54 * EnderChestPlus.MAX_MAIN_INVENTORY_PAGES
            && nextInv == null) {
            nextInvNum += addNum;
            nextInv = data.getInventory(nextInvNum);
        }
        if (nextInv == null) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        p.openInventory(nextInv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
    }

    @EventHandler
    public void onCloseInventory(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getPlayer();

        if (loader.getLookingAt(p) == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.getOpenInventory() == null) {
                    loader.setLookingAt(p, null);
                }
            }
        }.runTaskLater(plugin, 1);
    }

    private boolean canGetItem(Player p, ItemStack item) {
        if (p.getInventory().firstEmpty() >= 0) {
            return true;
        }

        ItemStack testItem = item.clone();
        testItem.setAmount(1);
        int itemAmount = item.getAmount();
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack slotItem = p.getInventory().getItem(i);
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                continue;
            }

            int slotItemAmount = slotItem.getAmount();
            slotItem = slotItem.clone();
            slotItem.setAmount(1);

            if (slotItem.equals(testItem)) {
                itemAmount -= slotItem.getMaxStackSize() - slotItemAmount;
            }
        }

        if (itemAmount <= 0) {
            return true;
        }

        item.setAmount(itemAmount);
        return false;
    }
}
