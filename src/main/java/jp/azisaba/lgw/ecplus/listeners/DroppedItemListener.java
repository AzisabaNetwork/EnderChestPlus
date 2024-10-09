package jp.azisaba.lgw.ecplus.listeners;

import jp.azisaba.lgw.ecplus.DropItemContainer;
import jp.azisaba.lgw.ecplus.DropItemContainer.DropItem;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

@RequiredArgsConstructor
public class DroppedItemListener implements Listener {

    private final DropItemContainer dropItemContainer;

    @EventHandler
    public void onCloseInventory(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) {
            return;
        }


        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        String inventoryName = InventoryOpenListener.getPlayerOpenInventoryTitle(p);
        if (inv.getHolder() instanceof  Player){
            inventoryName = ((Player) inv.getHolder()).getName();
        }

        if (!inventoryName.startsWith(Chat.f("&aDropped Item &7-"))) {
            return;
        }

        String stripped = Chat.r(inventoryName);
        String id = stripped.substring(stripped.indexOf("-") + 2);
        DropItem itemData = dropItemContainer.getItemData(id);

        if (itemData == null) {
            return;
        }

        if (inv.first(itemData.getItemStack()) < 0) {
            dropItemContainer.deleteItemData(id);
            p.sendMessage(Chat.f("&a正常に受け取りが完了しました。"));
        } else {
            p.sendMessage(Chat.f("&cまだアイテムが残っています。先ほどのボタンをクリックして再度開けます。"));
        }
    }
}
