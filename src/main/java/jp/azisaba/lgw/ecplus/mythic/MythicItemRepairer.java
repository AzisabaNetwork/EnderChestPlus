package jp.azisaba.lgw.ecplus.mythic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import jp.azisaba.lgw.ecplus.MigrationLog;
import java.util.logging.Logger;

/**
 * MythicItemIdCompat's item repair, applied to EnderChestPlus pages (which live in our database and are
 * therefore out of reach of MythicItemIdCompat):
 * <ol>
 *     <li>an item still carrying the MythicMobs 4 {@code MYTHIC_TYPE} tag gets its {@code mythicmobs:type};</li>
 *     <li>an item that lost both is identified from the 1.15.2 {@code Items} YAML (material, name, lore, head
 *     skin, checked against model / unbreakable / colour) and gets its ID, CustomModelData and skin back.</li>
 * </ol>
 * Shulker boxes and bundles are handled recursively. Must be called on the main thread.
 */
public final class MythicItemRepairer {
    public static final NamespacedKey TYPE = new NamespacedKey("mythicmobs", "type");
    private static final int MAX_DEPTH = 8;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final MythicItemBridge bridge;
    private volatile CurrentLegacyDefinitionMatcher matcher;
    private volatile String matcherError = "未読込";
    private volatile MmidExclusions exclusions = MmidExclusions.NONE;
    private volatile boolean logRepairs;
    private long tagConversions;
    private long idRepairs;
    private long modelRepairs;
    private long skinRepairs;
    private long errors;

    private MythicItemRepairer(JavaPlugin plugin, MythicItemBridge bridge) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.bridge = bridge;
    }

    /** The repairer, or null when MythicMobs 5 is not installed or the feature is disabled. */
    public static MythicItemRepairer create(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("MythicItemCompat.Enabled", true)) return null;
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            plugin.getLogger().info("MythicMobs is not installed; Mythic item repair is disabled.");
            return null;
        }
        try {
            return new MythicItemRepairer(plugin, MythicItemBridge.detect());
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "MythicMobs 5 API was not found; Mythic item repair is disabled.", e);
            return null;
        }
    }

    /** (Re)reads the exclusions and the legacy Items definitions. Call after MythicMobs has loaded its items. */
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        logRepairs = config.getBoolean("MythicItemCompat.LogRepairs", false);
        exclusions = MmidExclusions.parse(config.getStringList("MythicItemCompat.ExcludedMmids"));
        File plugins = plugin.getDataFolder().getParentFile();
        File[] candidates = {
                new File(plugin.getDataFolder(), "legacy-mythic-items"),
                new File(plugins, "MythicItemIdCompat/legacy-mythic-items"),
                new File(plugins, "SearchableInfiniteShop/legacy-mythic-items"),
        };
        File directory = null;
        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                directory = candidate;
                break;
            }
        }
        if (directory == null) {
            matcher = null;
            matcherError = "legacy-mythic-items フォルダがありません";
            logger.warning("Legacy Mythic item definitions not found; only MYTHIC_TYPE tags will be converted. "
                    + "Copy the 1.15.2 MythicMobs/Items contents to " + candidates[0].getPath() + ".");
            return;
        }
        try {
            matcher = CurrentLegacyDefinitionMatcher.load(directory, bridge::currentItem);
            matcherError = null;
            logger.info("Legacy Mythic item definitions loaded from " + directory.getPath() + ": " + matcher.summary());
        } catch (IOException e) {
            matcher = null;
            matcherError = e.getMessage();
            logger.warning("Could not load legacy Mythic item definitions: " + e.getMessage());
        }
    }

    /** @return the number of changes made to the inventory's items */
    public int repairInventory(Inventory inventory) {
        return repairInventory(inventory, 0);
    }

    private int repairInventory(Inventory inventory, int depth) {
        int changes = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            int itemChanges = repair(item, depth);
            if (itemChanges > 0) inventory.setItem(slot, item);
            changes += itemChanges;
        }
        return changes;
    }

    /** Repairs the item in place. @return the number of changes */
    public int repair(ItemStack item) {
        return repair(item, 0);
    }

    private int repair(ItemStack item, int depth) {
        if (item == null || item.getType().isAir() || depth > MAX_DEPTH) return 0;
        int changes = 0;
        try {
            changes += convertLegacyTag(item);
            changes += repairFromDefinition(item);
            changes += repairNestedContainer(item, depth);
            changes += repairBundle(item, depth);
        } catch (ReflectiveOperationException | RuntimeException e) {
            errors++;
            if (errors <= 10) logger.log(Level.WARNING, "Could not repair a Mythic item", e);
        }
        return changes;
    }

    private static String mythicId(ItemMeta meta) {
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(TYPE, PersistentDataType.STRING);
        return id == null || id.isBlank() ? null : id;
    }

    /** Part 1: MYTHIC_TYPE (left in custom_data by the 1.15 -> 1.21 upgrade) -> mythicmobs:type. */
    private int convertLegacyTag(ItemStack item) throws ReflectiveOperationException {
        if (mythicId(item.getItemMeta()) != null) return 0;
        // MythicMobs writes the ID onto the item it reads, so read a copy and keep it only when not excluded
        ItemStack copy = item.clone();
        LegacyItemData legacy = bridge.read(copy);
        if (legacy == null || exclusions.isExcluded(legacy.mythicType)) return 0;
        if (mythicId(copy.getItemMeta()) == null) return 0;
        item.setItemMeta(copy.getItemMeta());
        tagConversions++;
        if (logRepairs) MigrationLog.info("Mythic ID restored from MYTHIC_TYPE: " + legacy.mythicType);
        return 1;
    }

    /** Part 2: ID, CustomModelData and head skin from the old Items definitions. */
    private int repairFromDefinition(ItemStack item) {
        CurrentLegacyDefinitionMatcher matcher = this.matcher;
        if (matcher == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        String id = mythicId(meta);
        boolean needsId = id == null;
        if (needsId) id = matcher.match(item);
        if (id == null || exclusions.isExcluded(id)) return 0;
        ItemStack current = bridge.currentItem(id);
        if (current == null) return 0;

        Integer model = null;
        if (!meta.hasCustomModelData()) {
            ItemMeta standard = current.getItemMeta();
            if (standard != null && standard.hasCustomModelData()) model = standard.getCustomModelData();
            if (model == null) model = matcher.modelFor(id);
        }
        boolean missingSkin = CurrentLegacyDefinitionMatcher.isSkinlessHead(item);
        boolean canRestoreSkin = missingSkin && current.getType() == Material.PLAYER_HEAD
                && (current.getItemMeta() instanceof SkullMeta && !CurrentLegacyDefinitionMatcher.isSkinlessHead(current)
                    || matcher.headSkinFor(id) != null);
        if (!needsId && model == null && !canRestoreSkin) return 0;

        List<String> fixes = new ArrayList<>();
        if (needsId) {
            meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, id);
            idRepairs++;
            fixes.add("id");
        }
        if (model != null) {
            meta.setCustomModelData(model);
            modelRepairs++;
            fixes.add("model " + model);
        }
        if (canRestoreSkin && restoreHeadProfile((SkullMeta) meta, current, matcher.headSkinFor(id), id)) {
            skinRepairs++;
            fixes.add("skin");
        }
        item.setItemMeta(meta);
        if (logRepairs) MigrationLog.info("Mythic item " + id + " repaired: " + String.join(", ", fixes));
        return fixes.size();
    }

    private int repairNestedContainer(ItemStack item, int depth) {
        if (!(item.getItemMeta() instanceof BlockStateMeta blockMeta) || !blockMeta.hasBlockState()) return 0;
        BlockState state = blockMeta.getBlockState();
        if (!(state instanceof Container container)) return 0;
        int changes = repairInventory(container.getInventory(), depth + 1);
        if (changes > 0) {
            blockMeta.setBlockState(state);
            item.setItemMeta(blockMeta);
        }
        return changes;
    }

    private int repairBundle(ItemStack item, int depth) {
        if (!(item.getItemMeta() instanceof BundleMeta bundle) || !bundle.hasItems()) return 0;
        List<ItemStack> items = new ArrayList<>(bundle.getItems());
        int changes = 0;
        for (ItemStack nested : items) changes += repair(nested, depth + 1);
        if (changes > 0) {
            bundle.setItems(items);
            item.setItemMeta(bundle);
        }
        return changes;
    }

    private static boolean restoreHeadProfile(SkullMeta target, ItemStack current, String oldSkinUrl, String id) {
        PlayerProfile profile = null;
        if (current.getItemMeta() instanceof SkullMeta skull && !CurrentLegacyDefinitionMatcher.isSkinlessHead(current))
            profile = skull.getOwnerProfile();
        if (profile == null && oldSkinUrl != null) {
            try {
                profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)));
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(oldSkinUrl));
                profile.setTextures(textures);
            } catch (MalformedURLException | IllegalArgumentException ignored) {
                return false;
            }
        }
        if (profile == null) return false;
        target.setOwnerProfile(profile);
        return true;
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add("旧タグからID付与 " + tagConversions + " / 旧定義からID " + idRepairs + "・Model " + modelRepairs
                + "・スキン " + skinRepairs + " / エラー " + errors);
        lines.add("除外MMID: " + exclusions.summary());
        CurrentLegacyDefinitionMatcher current = matcher;
        lines.add("旧定義: " + (current != null ? current.summary() : "読み込めていません（" + matcherError + "）"));
        return lines;
    }
}
