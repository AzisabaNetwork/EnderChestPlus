package jp.azisaba.lgw.ecplus.listeners;

import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryData;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import java.util.UUID;

/**
 * Applies a page name typed into the anvil GUI opened by {@link InventoryLoader#openRenameAnvil}.
 */
@RequiredArgsConstructor
public class PageRenameListener implements Listener {

    private final InventoryLoader loader;

    @EventHandler
    public void onClickAnvil(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        InventoryLoader.PageRenameSession session = loader.getRenameSession(p);
        if (session == null) {
            return;
        }
        if (!(e.getView() instanceof AnvilView view)) {
            return;
        }

        e.setCancelled(true);

        if (e.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        UUID targetUuid = session.targetUuid();
        int page = session.page();
        String name = InventoryData.sanitizePageName(view.getRenameText());

        loader.finishRename(p);
        p.closeInventory();

        boolean admin = !targetUuid.equals(p.getUniqueId());
        EnderChestPlus.newChain()
                .asyncFirst(() -> {
                    InventoryData data = loader.getInventoryData(targetUuid);
                    return data != null ? data : loader.loadInventoryData(targetUuid);
                })
                .syncLast(data -> {
                    if (data == null) {
                        p.sendMessage(Chat.f("&cデータが見つかりませんでした。再度お試しください。"));
                        return;
                    }
                    data.setPageName(page, name);
                    if (name == null) {
                        p.sendMessage(Chat.f("&aページ&e{0}&aの名前を削除しました。", page + 1));
                    } else {
                        p.sendMessage(Chat.f("&aページ&e{0}&aの名前を&f{1}&aに設定しました。", page + 1, name));
                    }
                    EnderChestPlus.newChain().async(() -> data.save(false)).execute();
                    if (admin) {
                        loader.setLookingAt(p, targetUuid);
                    }
                    p.openInventory(InventoryLoader.getMainInventory(data, page / 54));
                })
                .execute();
    }

    @EventHandler
    public void onCloseAnvil(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        if (!(e.getView().getTopInventory() instanceof AnvilInventory)) {
            return;
        }
        loader.finishRename(p);
    }
}
