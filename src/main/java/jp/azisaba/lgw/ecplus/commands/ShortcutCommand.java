package jp.azisaba.lgw.ecplus.commands;

import jp.azisaba.lgw.ecplus.DropItemContainer;
import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryData;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class ShortcutCommand implements CommandExecutor {
    private final EnderChestPlus plugin;
    private final InventoryLoader loader;
    private final DropItemContainer dropItemContainer;
    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player p) {
            // エンダーチェストを開く
            if (!plugin.isAllowOpenEnderChest()) {
                p.sendMessage(Chat.f("&c現在エンダーチェストは無効化されています。運営が再度有効化するまでお待ちください。"));
                if (p.hasPermission("enderchestplus.command.enderchestplus")) {
                    p.sendMessage(Chat.f("&eあなたは運営なので、&c/ecp enable &eで解除することができます。\n他の運営がエンチェスのメンテナンスをしていないか確認してから実行してください。"));
                }
                return true;
            }

            if (loader.getLookingAt(p) != null) {
                loader.setLookingAt(p, null);
            }

            UUID uuid = p.getUniqueId();
            if (!openingPlayers.add(uuid)) {
                return true;
            }

            EnderChestPlus.newChain()
                    .asyncFirst(() -> {
                        try {
                            return loader.loadInventoryData(uuid);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to load inventory data for " + uuid + ": " + e.getMessage());
                            return loader.getInventoryData(uuid);
                        }
                    })
                    .syncLast(data -> {
                        try {
                            if (p.isOnline()) {
                                if (data == null) {
                                    p.sendMessage(Chat.f("&cインベントリデータのロードに失敗しました。時間をおいて再度お試しください。"));
                                    return;
                                }
                                Inventory inv = InventoryLoader.getMainInventory(data, 0);
                                p.openInventory(inv);
                            }
                        } finally {
                            openingPlayers.remove(uuid);
                        }
                    })
                    .execute();
            return true;
        }
        sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
        return false;
    }
}
