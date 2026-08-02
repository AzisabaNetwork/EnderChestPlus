package jp.azisaba.lgw.ecplus.utils;

import me.kbrewster.exceptions.APIException;
import me.kbrewster.exceptions.InvalidPlayerException;
import me.kbrewster.mojangapi.MojangAPI;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public final class UUIDUtils {

    private static final HashMap<String, UUID> UUID_CACHE = new HashMap<>();

    private UUIDUtils() {
    }

    public static UUID getUUID(String name) throws APIException, InvalidPlayerException, IOException {
        UUID cachedUuid = UUID_CACHE.get(name.toLowerCase());
        if (cachedUuid != null) {
            return cachedUuid;
        }

        UUID uuid = null;
        if (Bukkit.getPlayerExact(name) != null) {
            uuid = Bukkit.getPlayerExact(name).getUniqueId();
        }

        if (uuid == null) {
            uuid = MojangAPI.getUUID(name);
        }

        if (uuid != null) {
            UUID_CACHE.put(name.toLowerCase(), uuid);
        }
        return uuid;
    }
}
