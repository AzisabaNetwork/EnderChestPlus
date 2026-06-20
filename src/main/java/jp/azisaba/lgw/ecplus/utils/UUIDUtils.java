package jp.azisaba.lgw.ecplus.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プレイヤー名をUUIDに変換するメソッド。 <br>
 * 投票したときに送られてくるデータがプレイヤー名なので、それをUUIDに保存し、正確に投票報酬を割り振るために作成されました。
 *
 * @author siloneco
 */
public class UUIDUtils {

    // 調べたUUIDを保存しておくHashMap。何度も問い合わせるとエラーになるため
    private static final HashMap<String, UUID> uuidCache = new HashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Pattern UUID_JSON_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"");

    /**
     * プレイヤー名からUUIDを取得します。優先度は以下の通りです <br>
     * <br>
     * 1. キャッシュに保存してある場合はそこから取得 <br>
     * 2. プレイヤーがオンラインの場合はそのプレイヤーから取得 <br>
     * 3. オフラインプレイヤーとしてサーバーにキャッシュされている場合はそこから取得 <br>
     * 4. MojangAPIに問い合わせて取得
     *
     * @param name UUIDを取得したいプレイヤー名
     * @return そのプレイヤーのUUID、取得できなければnull
     * @throws IOException ネットワークエラーなどが発生した場合に発生
     */
    public static UUID getUUID(String name) throws IOException {
        // キャッシュを確認（大文字小文字を区別しない）
        UUID cached = uuidCache.get(name.toLowerCase());
        if (cached != null) {
            return cached;
        }

        UUID uuid = null;

        // プレイヤーがオンラインの場合はそのプレイヤーからUUIDを取得
        if (Bukkit.getPlayerExact(name) != null) {
            uuid = Bukkit.getPlayerExact(name).getUniqueId();
        }

        // オフラインプレイヤーのキャッシュから検索
        if (uuid == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);
            if (offlinePlayer != null) {
                uuid = offlinePlayer.getUniqueId();
            }
        }

        // まだ取得できていない場合はMojangAPIを使用して取得
        if (uuid == null) {
            uuid = fetchUUIDFromMojangAPI(name);
        }

        // 成功した場合はキャッシュに保存
        if (uuid != null) {
            uuidCache.put(name.toLowerCase(), uuid);
        }

        return uuid;
    }

    /**
     * MojangAPIに問い合わせてUUIDを取得します。
     *
     * @param name プレイヤー名
     * @return UUID、見つからないかエラーの場合はnull
     * @throws IOException ネットワークエラーが発生した場合
     */
    private static UUID fetchUUIDFromMojangAPI(String name) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_API_URL + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher matcher = UUID_JSON_PATTERN.matcher(body);
                if (matcher.find()) {
                    String rawUuid = matcher.group(1);
                    // ハイフンを挿入: 12345678-1234-1234-1234-123456789abc
                    String uuidStr = rawUuid.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                            "$1-$2-$3-$4-$5"
                    );
                    return UUID.fromString(uuidStr);
                }
            } else if (response.statusCode() == 429) {
                throw new IOException("Mojang API rate limited");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Failed to fetch UUID from Mojang API", e);
        }

        return null;
    }
}
