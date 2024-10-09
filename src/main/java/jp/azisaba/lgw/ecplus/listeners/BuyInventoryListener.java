package jp.azisaba.lgw.ecplus.listeners;

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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@RequiredArgsConstructor
public class BuyInventoryListener implements Listener {

    private final InventoryLoader loader;

    int money = 0;

    @EventHandler
    public void onClickedConfirmGUI(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }


        Player p = (Player) e.getWhoClicked();
        Inventory clicked = e.getClickedInventory();
        InventoryView opening = e.getView();
        ItemStack clickedItem = e.getCurrentItem();

        if (!opening.getTitle().startsWith(Chat.f("{0}&a - &cUnlock Page", EnderChestPlus.enderChestTitlePrefix))) {
            return;
        }

        e.setCancelled(true);


        int page;
        try {
            page = Integer.parseInt(Chat.r(opening.getTitle()).substring(Chat.r(opening.getTitle()).lastIndexOf(" ") + 1)) - 1;
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        if (clickedItem.getType() == Material.OAK_SIGN) {
            return;
        }
        int data = getData(clickedItem);
        int openMainInventoryIndex = page / 54;

        if (data == 5) {
            boolean success = costPlayer(p, page);

            if (success) {
                UUID looking = loader.getLookingAt(p);
                InventoryData data2;
                if (looking != null) {
                    data2 = loader.getInventoryData(looking);
                } else {
                    data2 = loader.getInventoryData(p);
                }
                data2.initializeInventory(page);
                p.openInventory(InventoryLoader.getMainInventory(data2, openMainInventoryIndex));

                p.sendMessage(Chat.f("&a購入に成功しました！ 現在の所持金:{0}$",EnderChestPlus.getEconomy().getBalance(p)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 2, 1);
            } else {
                p.sendMessage(Chat.f("&c購入するためのお金が{0}$足りません！",money-EnderChestPlus.getEconomy().getBalance(p)));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                p.closeInventory();
            }
        } else if (data == 14) {
            UUID looking = loader.getLookingAt(p);
            InventoryData data2;
            if (looking != null) {
                data2 = loader.getInventoryData(looking);
            } else {
                data2 = loader.getInventoryData(p);
            }
            p.openInventory(InventoryLoader.getMainInventory(data2, openMainInventoryIndex));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
        }
        return;
    }

    @SuppressWarnings("deprecation")
    private int getData(ItemStack item) {
        return item.getData().getData();
    }

    private boolean costPlayer(Player p, int page) {
        int line = page / 9 + 1;
        return costPlayerByLine(p, line);
    }

    private boolean costPlayerByLine(Player p, int line) {
        if (line <= 2) {
            return true;
        }

        if (line == 3) {
            money = 500;
        } else if (line == 4) {
            money = 1000;
        } else if (line == 5) {
            money = 1500;
        } else if (line == 6) {
            money = 2000;
        } else if (line <= 9) {
            money = 2500;
        } else {
            money = 3000;
        }

        if (EnderChestPlus.getEconomy().has(p,money)){
            EnderChestPlus.getEconomy().withdrawPlayer(p,money);
            return true;
        }
        return false;
    }
}
