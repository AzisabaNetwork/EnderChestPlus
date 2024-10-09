package jp.azisaba.lgw.ecplus.commands;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryData;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import jp.azisaba.lgw.ecplus.listeners.InventoryOpenListener;
import jp.azisaba.lgw.ecplus.utils.Chat;
import jp.azisaba.lgw.ecplus.utils.UUIDUtils;
import lombok.RequiredArgsConstructor;
import me.kbrewster.exceptions.APIException;
import me.kbrewster.exceptions.InvalidPlayerException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

@RequiredArgsConstructor
public class EnderChestPlusCommand implements CommandExecutor {

    private final EnderChestPlus plugin;
    private final InventoryLoader loader;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player p = (Player) sender;

        if (args.length <= 0) {
            sendUsage(p, label);
            return true;
        }

        if (args[0].equals("save")) {
            EnderChestPlus.newChain()
                    .sync(() -> p.sendMessage(Chat.f("&e非同期でセーブしています...")))
                    .asyncFirst(() -> loader.saveAllInventoryData(false))
                    .asyncLast((count) -> {
                        plugin.getLogger().info(Chat.f("{0}人のエンダーチェストを保存しました。", count));
                        p.sendMessage(Chat.f("&a{0}人のエンダーチェストを保存しました。", count));
                    }).execute();
            return true;
        } else if (args[0].equalsIgnoreCase("enable")) {
            plugin.setAllowOpenEnderChest(true);
            p.sendMessage(Chat.f("&eエンダーチェストを&a開ける&eように設定しました"));
            return true;
        } else if (args[0].equalsIgnoreCase("disable")) {
            plugin.setAllowOpenEnderChest(false);
            p.sendMessage(Chat.f("&eエンダーチェストを&c開けない&eように設定しました"));
            return true;
        } else if (args[0].equalsIgnoreCase("openingPlayer")) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> {
                        String inv = InventoryOpenListener.getPlayerOpenInventoryTitle(player);
                        return inv.startsWith(EnderChestPlus.enderChestTitlePrefix);
                    }).map(Player::getName)
                    .collect(Collectors.toList());

            if (playerNames.isEmpty()) {
                p.sendMessage(Chat.f("&a開いているプレイヤーはいませんでした。"));
            } else {
                p.sendMessage(Chat.f("&a開いているプレイヤー: &e{0}", String.join(Chat.f("&7, &e"), playerNames)));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("open")) {
            if (!plugin.isAllowOpenEnderChest()) {
                p.sendMessage(Chat.f("&c現在エンダーチェストは無効化されています。運営が再度有効化するまでお待ちください。"));
                if (p.hasPermission("enderchestplus.command.enderchestplus")) {
                    p.sendMessage(Chat.f("&eあなたは運営なので、&c/ecp enable &eで解除することができます。\n他の運営がエンチェスのメンテナンスをしていないか確認してから実行してください。"));
                }
            }
            if (args.length <= 1) {
                p.sendMessage(Chat.f("&cUUIDかプレイヤー名を指定してください"));
                return true;
            }

            p.sendMessage(Chat.f("&a非同期でデータをロード中です。完了し次第開きます"));
            EnderChestPlus.newChain()
                .asyncFirst(() -> {
                    try {
                        return UUIDUtils.getUUID(args[1]);
                    } catch (APIException e) {
                        p.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(MojangAPIのレートリミット)"));
                    } catch (InvalidPlayerException e) {
                        p.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(そのMCIDのプレイヤーは存在しません)"));
                    } catch (Exception e) {
                        String className = e.getClass().getName();
                        if (className.contains(".")) {
                            className = className.substring(className.lastIndexOf(".") + 1);
                        }
                        p.sendMessage(Chat.f("&cUUIDの取得に失敗しました。({0})", className));
                    }
                    return null;
                }).abortIfNull()
                .storeAsData("uuid")
                .asyncLast(loader::loadInventoryData)
                .<UUID>returnData("uuid")
                .syncLast((uuid) -> {
                    if (p.isOnline()) {
                        InventoryData data = loader.getInventoryData(uuid);
                        loader.setLookingAt(p, uuid);
                        p.openInventory(InventoryLoader.getMainInventory(data, 0));
                    }
                }).execute();
            return true;
        } else if (args[0].equalsIgnoreCase("migrate")) {
            if (args.length <= 2) {
                sender.sendMessage(Chat.f("&cUsage: /" + label + " migrate <from> <to>"));
                return true;
            }

            EnderChestPlus.newChain()
                .async(() -> {
                    UUID from, to;
                    try {
                        from = UUIDUtils.getUUID(args[1]);
                        to = UUIDUtils.getUUID(args[2]);
                    } catch (APIException e) {
                        sender.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(MojangAPIのレートリミット)"));
                        return;
                    } catch (IOException e) {
                        sender.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(不明なエラー)"));
                        return;
                    } catch (InvalidPlayerException e) {
                        sender.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(不明なプレイヤー)"));
                        return;
                    }

                    if (from == null || to == null) {
                        sender.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(不明なプレイヤー)"));
                        return;
                    }

                    boolean isOpening = Stream.of(from, to)
                        .map(Bukkit::getPlayer)
                        .anyMatch(player -> {
                            if (player == null) {
                                return false;
                            }
                            String inv = InventoryOpenListener.getPlayerOpenInventoryTitle(player);
                            return inv
                                .startsWith(EnderChestPlus.enderChestTitlePrefix);
                        });

                    if (isOpening) {
                        sender.sendMessage(Chat.f("&c対象のプレイヤーがエンダーチェストを開いているため、移行を実行できません"));
                        return;
                    }

                    sender.sendMessage(Chat.f("&e移行しています..."));
                    plugin.getLoader().migrate(from, to);
                    sender.sendMessage(Chat.f("&a移行が完了しました！"));
                }).execute();
            return true;
        }
        sendUsage(p, label);
        return true;
    }

    private void sendUsage(Player p, String label) {
        p.sendMessage(Chat.f("&e/{0} open <Player/UUID> &7- &aECを開きます", label) + "\n"
            + Chat.f("&e/{0} save &7- &a非同期で全プレイヤーのECをセーブします", label) + "\n"
            + Chat.f("&e/{0} enable &7- &aエンダーチェストを有効化します", label) + "\n"
            + Chat.f("&e/{0} disable &7- &aエンダーチェストを無効化します", label) + "\n"
            + Chat.f("&e/{0} migrate &7- &aエンダーチェストの内容を移行します", label) + "\n"
            + Chat.f("&e/{0} openingPlayer &7- &aエンダーチェストを開いているプレイヤーを取得します", label) + "\n");
    }
}