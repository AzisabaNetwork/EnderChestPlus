package jp.azisaba.lgw.ecplus.mythic;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the MythicMobs 4 definition of an item whose ID was lost before the move to 1.21, by
 * comparing it with the old {@code Items} YAML: material, display name, lore and head skin.
 * <p>
 * The material is taken from the item the current MythicMobs generates for that ID, so old numeric
 * IDs ({@code Id: 397} + {@code Data: 3}) and materials renamed since 1.15 still match. Definitions
 * whose ID no longer exists in MythicMobs are skipped: they could not be repaired anyway.
 * <p>
 * A match is then checked against what the definition fixes and nothing else would set: an item
 * with another custom model, a different leather colour, or not unbreakable when the definition is,
 * is not that Mythic item even if its name and lore are the same.
 */
final class CurrentLegacyDefinitionMatcher {
    private static final Pattern SKIN_URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final Map<Signature, String> ids = new HashMap<>();
    private final Map<Signature, String> plainIds = new HashMap<>();
    private final Map<Signature, String> skinlessHeadIds = new HashMap<>();
    private final Map<String, Traits> traitsById = new HashMap<>();
    private final Map<String, String> headSkinsById = new HashMap<>();
    private final Set<Signature> ambiguous = new HashSet<>();
    private final Set<Signature> plainAmbiguous = new HashSet<>();
    private final Set<Signature> skinlessHeadAmbiguous = new HashSet<>();
    private int files;
    private int invalidFiles;
    private int missingCurrent;
    private int rejected;

    /**
     * @param currentItem the item the current MythicMobs generates for an ID, or null if it has none
     */
    static CurrentLegacyDefinitionMatcher load(File directory, Function<String, ItemStack> currentItem)
            throws IOException {
        if (!directory.isDirectory()) throw new IOException("Legacy Items directory not found: " + directory);
        CurrentLegacyDefinitionMatcher result = new CurrentLegacyDefinitionMatcher();
        try (Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))::iterator) {
                result.files++;
                Object document;
                try {
                    document = new Yaml().load(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                } catch (RuntimeException invalidYaml) {
                    result.invalidFiles++;
                    continue;
                }
                if (!(document instanceof Map)) continue;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) document).entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) continue;
                    result.add((String) entry.getKey(), (Map<?, ?>) entry.getValue(), currentItem);
                }
            }
        }
        for (Signature signature : result.ambiguous) result.ids.remove(signature);
        for (Signature signature : result.plainAmbiguous) result.plainIds.remove(signature);
        for (Signature signature : result.skinlessHeadAmbiguous) result.skinlessHeadIds.remove(signature);
        if (result.files == 0 || result.ids.isEmpty())
            throw new IOException("No usable legacy MM definitions in " + directory);
        return result;
    }

    private void add(String id, Map<?, ?> config, Function<String, ItemStack> currentItem) {
        if (config.get("Id") == null) return;
        ItemStack current = currentItem.apply(id);
        if (current == null || current.getType().isAir()) {
            missingCurrent++;
            return;
        }
        traitsById.put(id, Traits.fromDefinition(config, current));
        Signature signature = Signature.fromDefinition(config, current.getType());
        if (signature == null) return;
        if (signature.skinUrl != null) headSkinsById.put(id, signature.skinUrl);
        String previous = ids.putIfAbsent(signature, id);
        if (previous != null && !previous.equals(id)) ambiguous.add(signature);
        Signature plain = signature.plain();
        String oldPlain = plainIds.putIfAbsent(plain, id);
        if (oldPlain != null && !oldPlain.equals(id)) plainAmbiguous.add(plain);
        if (signature.skinUrl != null && (signature.name != null || !signature.lore.isEmpty())) {
            Signature withoutSkin = plain.withoutSkin();
            String previousHead = skinlessHeadIds.putIfAbsent(withoutSkin, id);
            if (previousHead != null && !previousHead.equals(id)) skinlessHeadAmbiguous.add(withoutSkin);
        }
    }

    String match(ItemStack item) {
        Signature signature = Signature.fromItem(item);
        if (signature == null) return null;
        String id = ids.get(signature);
        if (id == null) id = plainIds.get(signature.plain());
        if (id == null && signature.material == Material.PLAYER_HEAD && signature.skinUrl == null
                && (signature.name != null || !signature.lore.isEmpty())) {
            id = skinlessHeadIds.get(signature.plain().withoutSkin());
        }
        if (id == null) return null;
        Traits traits = traitsById.get(id);
        if (traits != null && !traits.allows(item)) {
            rejected++;
            return null;
        }
        return id;
    }

    Integer modelFor(String id) {
        Traits traits = traitsById.get(id);
        return traits == null ? null : traits.legacyModel;
    }

    String headSkinFor(String id) { return headSkinsById.get(id); }

    static boolean isSkinlessHead(ItemStack item) {
        return item != null && item.getType() == Material.PLAYER_HEAD
                && item.getItemMeta() instanceof SkullMeta skull && Signature.itemSkinUrl(skull) == null;
    }

    static String readableText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return value;
        try {
            return BaseComponent.toLegacyText(ComponentSerializer.parse(value));
        } catch (RuntimeException invalidComponent) {
            return value;
        }
    }

    String summary() {
        return "読込ファイル " + files + "件（構文エラー " + invalidFiles + "件）、一意な旧定義 "
                + ids.size() + "件、同じ見た目の重複 " + ambiguous.size() + "件、現行MMに無いID "
                + missingCurrent + "件、特徴の不一致で除外 " + rejected + "件";
    }

    private static Integer integer(Object raw) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw == null) return null;
        try { return Integer.parseInt(String.valueOf(raw).trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** What a definition fixes on every item it generates, used to rule out look-alikes. */
    private static final class Traits {
        /** Model of the old definition (restored onto items that lost it). */
        final Integer legacyModel;
        /** Every model an item of this ID may legitimately carry (old and current definition). */
        final Set<Integer> models = new HashSet<>();
        final boolean unbreakable;
        final Color color;

        private Traits(Integer legacyModel, boolean unbreakable, Color color) {
            this.legacyModel = legacyModel;
            this.unbreakable = unbreakable;
            this.color = color;
        }

        static Traits fromDefinition(Map<?, ?> config, ItemStack current) {
            Integer model = integer(config.containsKey("Model") ? config.get("Model") : config.get("CustomModelData"));
            Map<?, ?> options = config.get("Options") instanceof Map ? (Map<?, ?>) config.get("Options")
                    : Collections.emptyMap();
            Traits traits = new Traits(model, Boolean.parseBoolean(String.valueOf(options.get("Unbreakable"))),
                    color(options.get("Color")));
            if (model != null) traits.models.add(model);
            ItemMeta meta = current.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) traits.models.add(meta.getCustomModelData());
            return traits;
        }

        boolean allows(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;
            if (meta.hasCustomModelData() && !models.contains(meta.getCustomModelData())) return false;
            if (unbreakable && !meta.isUnbreakable()) return false;
            return color == null || !(meta instanceof LeatherArmorMeta leather) || color.equals(leather.getColor());
        }

        private static Color color(Object raw) {
            if (raw == null) return null;
            String[] parts = String.valueOf(raw).split(",");
            if (parts.length != 3) return null;
            try {
                return Color.fromRGB(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static final class Signature {
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final String skinUrl;

        private Signature(Material material, String name, List<String> lore, String skinUrl) {
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.skinUrl = skinUrl;
        }

        static Signature fromDefinition(Map<?, ?> config, Material material) {
            String name = colored(config.get("Display"));
            List<String> lore = coloredList(config.get("Lore"));
            if (name == null && lore.isEmpty() && material != Material.PLAYER_HEAD) return null;
            String skinUrl = null;
            if (material == Material.PLAYER_HEAD) {
                if (!(config.get("Options") instanceof Map)) return null;
                skinUrl = decodeSkinUrl(((Map<?, ?>) config.get("Options")).get("SkinTexture"));
                if (skinUrl == null) return null;
            }
            return new Signature(material, name, lore, skinUrl);
        }

        static Signature fromItem(ItemStack item) {
            if (item == null || !item.hasItemMeta()) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String name = meta.hasDisplayName() ? readableText(meta.getDisplayName()) : null;
            List<String> lore = null;
            if (meta.hasLore()) {
                lore = new ArrayList<>();
                for (String line : meta.getLore()) lore.add(readableText(line));
            }
            if (name == null && (lore == null || lore.isEmpty()) && item.getType() != Material.PLAYER_HEAD) return null;
            String skinUrl = null;
            if (item.getType() == Material.PLAYER_HEAD) {
                if (!(meta instanceof SkullMeta skull)) return null;
                skinUrl = itemSkinUrl(skull);
                if (skinUrl == null && name == null && (lore == null || lore.isEmpty())) return null;
            }
            return new Signature(item.getType(), name, lore == null ? Collections.emptyList() : lore, skinUrl);
        }

        private static String colored(Object value) {
            return value == null ? null : ChatColor.translateAlternateColorCodes('&', String.valueOf(value));
        }

        private static List<String> coloredList(Object value) {
            if (!(value instanceof List)) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (Object line : (List<?>) value) result.add(colored(line));
            return result;
        }

        private static String decodeSkinUrl(Object encoded) {
            if (encoded == null) return null;
            try {
                String json = new String(Base64.getDecoder().decode(String.valueOf(encoded)), StandardCharsets.UTF_8);
                Matcher matcher = SKIN_URL.matcher(json);
                return matcher.find() ? matcher.group(1) : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        static String itemSkinUrl(SkullMeta meta) {
            PlayerProfile profile = meta.getOwnerProfile();
            if (profile == null) return null;
            URL skin = profile.getTextures().getSkin();
            return skin == null ? null : skin.toString();
        }

        private Signature plain() {
            List<String> plainLore = new ArrayList<>(lore.size());
            for (String line : lore) plainLore.add(strip(line));
            return new Signature(material, strip(name), plainLore, skinUrl);
        }

        private Signature withoutSkin() { return new Signature(material, name, lore, null); }

        private static String strip(String value) {
            return value == null ? null : ChatColor.stripColor(value).trim();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Signature that)) return false;
            return material == that.material && Objects.equals(name, that.name)
                    && Objects.equals(lore, that.lore) && Objects.equals(skinUrl, that.skinUrl);
        }

        @Override public int hashCode() { return Objects.hash(material, name, lore, skinUrl); }
    }
}
