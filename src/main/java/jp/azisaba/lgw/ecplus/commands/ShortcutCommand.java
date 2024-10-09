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
@RequiredArgsConstructor
public class ShortcutCommand implements CommandExecutor {
    private final EnderChestPlus plugin ;
    private final InventoryLoader loader;
    private final DropItemContainer dropItemContainer;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {
            Player p = (Player) sender;
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

            InventoryData data = loader.getInventoryData(p);

            // nullの場合は読み込み待ち
            if (data == null) {
                p.sendMessage(Chat.f("&c現在プレイヤーデータのロード中です。しばらくお待ちください..."));
                return true;
            }

            Inventory inv = InventoryLoader.getMainInventory(data, 0);
            p.openInventory(inv);
            return true;
        }
        sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
        return false;
    }
}
