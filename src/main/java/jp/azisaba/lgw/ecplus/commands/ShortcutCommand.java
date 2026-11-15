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

import java.util.Arrays;
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
            if (args.length > 0 && args[0].equalsIgnoreCase("rename")) {
                return renamePage(p, args);
            }

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

    private boolean renamePage(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Chat.f("&cUsage: /ec2 rename <ページ番号> [名前]"));
            return true;
        }

        int page;
        try {
            page = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            p.sendMessage(Chat.f("&cページ番号は数字で指定してください。"));
            return true;
        }
        final int pageIndex = page - 1;
        if (pageIndex < 0 || pageIndex >= 54 * EnderChestPlus.MAX_MAIN_INVENTORY_PAGES) {
            p.sendMessage(Chat.f("&cページ番号は1〜{0}の範囲で指定してください。", 54 * EnderChestPlus.MAX_MAIN_INVENTORY_PAGES));
            return true;
        }

        String name = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : null;

        EnderChestPlus.newChain()
                .asyncFirst(() -> loader.loadInventoryData(p.getUniqueId()))
                .syncLast(data -> {
                    if (data == null) {
                        p.sendMessage(Chat.f("&cデータが見つかりませんでした。再度お試しください。"));
                        return;
                    }
                    if (data.getInventory(pageIndex) == null) {
                        p.sendMessage(Chat.f("&cページ&e{0}&cはまだ購入されていません。", pageIndex + 1));
                        return;
                    }
                    String sanitized = InventoryData.sanitizePageName(name);
                    data.setPageName(pageIndex, sanitized);
                    EnderChestPlus.newChain().async(() -> data.save(false)).execute();
                    if (sanitized == null) {
                        p.sendMessage(Chat.f("&aページ&e{0}&aの名前を削除しました。", pageIndex + 1));
                    } else {
                        p.sendMessage(Chat.f("&aページ&e{0}&aの名前を&f{1}&aに設定しました。", pageIndex + 1, sanitized));
                    }
                })
                .execute();
        return true;
    }
}
