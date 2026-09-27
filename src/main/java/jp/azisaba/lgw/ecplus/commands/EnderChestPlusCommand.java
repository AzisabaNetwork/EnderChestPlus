package jp.azisaba.lgw.ecplus.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class EnderChestPlusCommand implements TabExecutor {

    private final EnderChestPlus plugin;
    private final InventoryLoader loader;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length <= 0) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            EnderChestPlus.newChain()
                    .sync(() -> sender.sendMessage(Chat.f("&e非同期でセーブしています...")))
                    .asyncFirst(() -> loader.saveAllInventoryData(false))
                    .asyncLast((count) -> {
                        plugin.getLogger().info(Chat.f("{0}人のエンダーチェストを保存しました。", count));
                        sender.sendMessage(Chat.f("&a{0}人のエンダーチェストを保存しました。", count));
                    }).execute();
            return true;
        } else if (args[0].equalsIgnoreCase("enable")) {
            plugin.setAllowOpenEnderChest(true);
            sender.sendMessage(Chat.f("&eエンダーチェストを&a開ける&eように設定しました"));
            return true;
        } else if (args[0].equalsIgnoreCase("disable")) {
            plugin.setAllowOpenEnderChest(false);
            sender.sendMessage(Chat.f("&eエンダーチェストを&c開けない&eように設定しました"));
            return true;
        } else if (args[0].equalsIgnoreCase("openingPlayer")) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> {
                        String inv = InventoryOpenListener.getPlayerOpenInventoryTitle(player);
                        return inv.startsWith(EnderChestPlus.enderChestTitlePrefix);
                    }).map(Player::getName)
                    .collect(Collectors.toList());

            if (playerNames.isEmpty()) {
                sender.sendMessage(Chat.f("&a開いているプレイヤーはいませんでした。"));
            } else {
                sender.sendMessage(Chat.f("&a開いているプレイヤー: &e{0}", String.join(Chat.f("&7, &e"), playerNames)));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Chat.f("&cこのコマンドはプレイヤーのみ実行できます。"));
                return true;
            }
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
        } else if (args[0].equalsIgnoreCase("remigrate")) {
            if (args.length <= 1) {
                sender.sendMessage(Chat.f("&cUsage: /" + label + " remigrate <Player/UUID|all>"));
                return true;
            }

            if (args[1].equalsIgnoreCase("all")) {
                List<Player> openingPlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(player -> {
                            String inv = InventoryOpenListener.getPlayerOpenInventoryTitle(player);
                            return inv.startsWith(EnderChestPlus.enderChestTitlePrefix);
                        })
                        .collect(Collectors.toList());

                if (!openingPlayers.isEmpty()) {
                    sender.sendMessage(Chat.f("&cエンダーチェストを開いているプレイヤーがいるため、一括再インポートを実行できません。"));
                    sender.sendMessage(Chat.f("&c開いているプレイヤー: &e{0}", openingPlayers.stream().map(Player::getName).collect(Collectors.joining(", "))));
                    return true;
                }

                EnderChestPlus.newChain()
                        .sync(() -> sender.sendMessage(Chat.f("&e全プレイヤーのレガシーYAMLデータを再インポートしています...")))
                        .asyncFirst(() -> loader.migrateLegacyYamlData(true))
                        .syncLast((count) -> {
                            sender.sendMessage(Chat.f("&a{0}件のレガシーデータを再インポートしてMySQLに保存しました。", count));
                            plugin.getLogger().info(Chat.f("{0}件のレガシーデータを再インポートしました。", count));
                        }).execute();
                return true;
            }

            EnderChestPlus.newChain()
                    .async(() -> {
                        UUID uuid = null;
                        try {
                            uuid = UUID.fromString(args[1]);
                        } catch (IllegalArgumentException ignored) {
                        }
                        if (uuid == null) {
                            try {
                                uuid = UUIDUtils.getUUID(args[1]);
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
                        }

                        if (uuid == null) {
                            sender.sendMessage(Chat.f("&cUUIDの取得に失敗しました。(不明なプレイヤー)"));
                            return;
                        }

                        Player targetPlayer = Bukkit.getPlayer(uuid);
                        if (targetPlayer != null) {
                            String inv = InventoryOpenListener.getPlayerOpenInventoryTitle(targetPlayer);
                            if (inv.startsWith(EnderChestPlus.enderChestTitlePrefix)) {
                                sender.sendMessage(Chat.f("&c対象のプレイヤーがエンダーチェストを開いているため、再インポートを実行できません"));
                                return;
                            }
                        }

                        sender.sendMessage(Chat.f("&eレガシーYAMLデータを再インポートしています..."));
                        boolean success = loader.remigrate(uuid);
                        if (success) {
                            sender.sendMessage(Chat.f("&a再インポートが完了しました！(UUID: {0})", uuid));
                        } else {
                            sender.sendMessage(Chat.f("&c再インポートに失敗しました。対象のレガシーYAMLファイルが存在しない可能性があります。(UUID: {0})", uuid));
                        }
                    }).execute();
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("open", "save", "enable", "disable", "migrate", "remigrate", "openingPlayer")
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("migrate")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("remigrate")) {
                List<String> options = new ArrayList<>();
                options.add("all");
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                return options.stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Chat.f("&e/{0} open <Player/UUID> &7- &aECを開きます", label) + "\n"
            + Chat.f("&e/{0} save &7- &a非同期で全プレイヤーのECをセーブします", label) + "\n"
            + Chat.f("&e/{0} enable &7- &aエンダーチェストを有効化します", label) + "\n"
            + Chat.f("&e/{0} disable &7- &aエンダーチェストを無効化します", label) + "\n"
            + Chat.f("&e/{0} migrate <from> <to> &7- &aエンダーチェストの内容を移行します", label) + "\n"
            + Chat.f("&e/{0} remigrate <Player/UUID|all> &7- &aレガシーYAMLからECデータを再インポートします", label) + "\n"
            + Chat.f("&e/{0} openingPlayer &7- &aエンダーチェストを開いているプレイヤーを取得します", label) + "\n");
    }
}