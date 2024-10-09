package jp.azisaba.lgw.ecplus.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryOpenListener implements Listener {

    private static final ConcurrentHashMap<Player, InventoryOpenEvent> playerInventoryEvents = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        playerInventoryEvents.put(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        playerInventoryEvents.remove(player);
    }

    public static String getPlayerOpenInventoryTitle(Player player) {
        InventoryOpenEvent event = playerInventoryEvents.get(player);
        if (event != null) {
            return event.getView().getTitle();
        } else {
            return "No inventory open";
        }
    }
}
