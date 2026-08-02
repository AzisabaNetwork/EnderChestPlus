package jp.azisaba.lgw.ecplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.MessageFormat;

public class Chat {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // メッセージをフォーマットして、&で色をつける
    public static String f(String text, Object... args) {
        return LEGACY_SERIALIZER.serialize(component(MessageFormat.format(text, args)));
    }

    // 色を消す
    public static String r(String text) {
        return LEGACY_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(text)).replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    public static Component component(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public static String legacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }
}
