package jp.azisaba.lgw.ecplus;

import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainFactory;
import jp.azisaba.lgw.ecplus.commands.EnderChestPlusCommand;
import jp.azisaba.lgw.ecplus.commands.ReceiveDroppedCommand;
import jp.azisaba.lgw.ecplus.commands.ShortcutCommand;
import jp.azisaba.lgw.ecplus.listeners.*;
import jp.azisaba.lgw.ecplus.tasks.AutoSaveTask;
import jp.azisaba.lgw.ecplus.utils.Chat;
import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;

public class EnderChestPlus extends JavaPlugin {

    public static final String enderChestTitlePrefix = Chat.f("&cEnderChest&b+");
    public static final String mainEnderChestTitle = Chat.f("{0} &a- &eMain", enderChestTitlePrefix);
    public static final int MAX_MAIN_INVENTORY_PAGES = 5;
    private static PluginConfig config;
    @Getter
    private static File inventoryDataFile;
    private static TaskChainFactory taskChainFactory;
    private AutoSaveTask saveTask;
    @Getter
    private DropItemContainer dropItemContainer = null;
    @Getter
    private InventoryLoader loader = null;
    @Getter
    @Setter
    private boolean allowOpenEnderChest = true;

    public static PluginConfig getPluginConfig() {
        return config;
    }

    public static <T> TaskChain<T> newChain() {
        return taskChainFactory.newChain();
    }

    public static <T> TaskChain<T> newSharedChain(String name) {
        return taskChainFactory.newSharedChain(name);
    }
    @Getter
    private static Economy economy = null;

    @Override
    public void onEnable() {
        taskChainFactory = BukkitTaskChainFactory.create(this);

        inventoryDataFile = new File(getDataFolder(), "Inventories");
        loader = new InventoryLoader(this);

        dropItemContainer = new DropItemContainer(this);
        dropItemContainer.load();
        saveTask = new AutoSaveTask(this, loader);
        saveTask.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);

        EnderChestPlus.config = new PluginConfig(this);
        EnderChestPlus.config.loadConfig();

        Bukkit.getOnlinePlayers().forEach(p -> newSharedChain("loadInventory")
                .async(() -> loader.loadInventoryData(p))
                .execute());

        Bukkit.getPluginManager().registerEvents(new EnderChestListener(this, loader, dropItemContainer), this);
        Bukkit.getPluginManager().registerEvents(new LoadInventoryDataListener(loader), this);
        Bukkit.getPluginManager().registerEvents(new BuyInventoryListener(loader), this);
        Bukkit.getPluginManager().registerEvents(new DroppedItemListener(dropItemContainer), this);
        Bukkit.getPluginManager().registerEvents(new InventoryOpenListener(), this);

        Bukkit.getPluginCommand("enderchestplus").setExecutor(new EnderChestPlusCommand(this, loader));
        Bukkit.getPluginCommand("enderchestplus").setPermissionMessage(Chat.f("{0}&c権限がありません！", config.chatPrefix));
        Bukkit.getPluginCommand("receivedropped").setExecutor(new ReceiveDroppedCommand(dropItemContainer));
        Bukkit.getPluginCommand("receivedropped").setPermissionMessage(Chat.f("{0}&c権限がありません！", config.chatPrefix));
        Bukkit.getPluginCommand("ec2").setExecutor(new ShortcutCommand(this, loader, dropItemContainer));

        Bukkit.getLogger().info(getName() + " enabled.");

        //VaultAPI初期化
        if (!setupEconomy()) {
            getLogger().severe("Vaultのセットアップに失敗しました。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {

        saveTask.cancel();

        dropItemContainer.save();

        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory() == null) {
                return;
            }
            if (player.getOpenInventory().getTopInventory() == null) {
                return;
            }
            if (InventoryOpenListener.getPlayerOpenInventoryTitle(player).startsWith(enderChestTitlePrefix)) {
                ItemStack item = player.getOpenInventory().getCursor();
                if (item != null) {
                    boolean success = false;
                    HashMap<Integer, ItemStack> slot = player.getInventory().addItem(item);

                    if (!slot.isEmpty()) {
                        slot = player.getOpenInventory().getTopInventory().addItem(item);
                        Bukkit.getLogger().info(slot.toString());

                        if (slot.isEmpty()) {
                            success = true;
                        }
                    } else {
                        success = true;
                    }

                    if (success) {
                        player.getOpenInventory().setCursor(null);
                    } else {
                        player.sendMessage(Chat.f("&cインベントリにもエンダーチェストにもアイテムが入らなかったため、地面にドロップしました。"));
                    }
                }
                player.closeInventory();
            }
        });

        loader.saveAllInventoryData(false);

        Bukkit.getLogger().info(getName() + " disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }


    public void reloadPluginConfig() {

        reloadConfig();

        EnderChestPlus.config = new PluginConfig(this);
        EnderChestPlus.config.loadConfig();
    }
}
