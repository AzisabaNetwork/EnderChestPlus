package jp.azisaba.lgw.ecplus.mythic;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** MythicMobs 5 item lookups, through reflection so the plugin does not need MythicMobs to compile. */
final class MythicItemBridge {
    private final Object itemManager;
    private final Method getType;
    private final Method getItemStack;

    private MythicItemBridge(Object itemManager, Method getType, Method getItemStack) {
        this.itemManager = itemManager;
        this.getType = getType;
        this.getItemStack = getItemStack;
    }

    static MythicItemBridge detect() throws ReflectiveOperationException {
        Class<?> coreClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object core = coreClass.getMethod("inst").invoke(null);
        Object manager = coreClass.getMethod("getItemManager").invoke(core);
        return new MythicItemBridge(manager,
                manager.getClass().getMethod("getMythicTypeFromItem", ItemStack.class),
                manager.getClass().getMethod("getItemStack", String.class));
    }

    /**
     * The item's Mythic ID. MythicMobs 5 itself moves a leftover 4.x {@code MYTHIC_TYPE} NBT tag into
     * {@code mythicmobs:type} (and the Prevent* flags) while answering, so this also migrates the item.
     */
    LegacyItemData read(ItemStack item) throws ReflectiveOperationException {
        Object value = getType.invoke(itemManager, item);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) return null;
        return new LegacyItemData((String) value);
    }

    /** A freshly generated item of the current definition, or null when MythicMobs has no such ID. */
    ItemStack currentItem(String id) {
        try {
            Object stack = getItemStack.invoke(itemManager, id);
            return stack instanceof ItemStack ? (ItemStack) stack : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
