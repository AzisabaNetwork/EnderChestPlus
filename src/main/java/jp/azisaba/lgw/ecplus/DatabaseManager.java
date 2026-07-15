package jp.azisaba.lgw.ecplus;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final String table;

    public DatabaseManager(PluginConfig config) throws SQLException {
        if (!config.mysqlTablePrefix.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("MySQL.TablePrefix may only contain letters, numbers and underscores");
        }
        table = config.mysqlTablePrefix + "inventories";
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + config.mysqlHost + ":" + config.mysqlPort + "/" + config.mysqlDatabase
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                + "&useSSL=" + config.mysqlUseSsl
                + "&verifyServerCertificate=" + config.mysqlVerifyServerCertificate
                + "&allowPublicKeyRetrieval=true");
        hikari.setUsername(config.mysqlUsername);
        hikari.setPassword(config.mysqlPassword);
        hikari.setMaximumPoolSize(Math.max(1, config.mysqlPoolSize));
        hikari.setMinimumIdle(1);
        hikari.setPoolName("EnderChestPlus-MySQL");
        hikari.setConnectionTimeout(Math.max(250, config.mysqlConnectionTimeoutMillis));
        dataSource = new HikariDataSource(hikari);
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`player_uuid` CHAR(36) NOT NULL, `inventory_data` LONGBLOB NOT NULL, "
                + "`updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), "
                + "PRIMARY KEY (`player_uuid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    public byte[] load(UUID uuid) throws SQLException {
        String sql = "SELECT `inventory_data` FROM `" + table + "` WHERE `player_uuid` = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getBytes(1) : null;
            }
        }
    }

    public void save(UUID uuid, byte[] data) throws SQLException {
        String sql = "INSERT INTO `" + table + "` (`player_uuid`, `inventory_data`) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE `inventory_data` = VALUES(`inventory_data`)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setBytes(2, data);
            statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
