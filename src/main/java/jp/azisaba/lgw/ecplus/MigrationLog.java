package jp.azisaba.lgw.ecplus;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Log for the legacy data migration and repairs. Routine messages go only to
 * plugins/EnderChestPlus/logs/migration-yyyy-MM-dd.log; warnings go to the file and the console.
 */
public final class MigrationLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Logger console = Logger.getLogger("EnderChestPlus");
    private static File file;
    private static BufferedWriter writer;

    private MigrationLog() {
    }

    public static synchronized void init(JavaPlugin plugin) {
        close();
        console = plugin.getLogger();
        File directory = new File(plugin.getDataFolder(), "logs");
        file = new File(directory, "migration-" + LocalDate.now() + ".log");
        try {
            Files.createDirectories(directory.toPath());
            writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            writer = null;
            console.warning("Could not open the migration log " + file.getPath() + "; logging to the console instead: " + e.getMessage());
        }
    }

    public static synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }

    /** Path of the log file, for console hints. */
    public static synchronized String path() {
        return file == null ? "(console)" : file.getPath();
    }

    /** Routine progress: written to the file only. */
    public static void info(String message) {
        if (!write("INFO", message)) console.info(message);
    }

    /** Something an admin should look at: written to the file and the console. */
    public static void warn(String message) {
        write("WARN", message);
        console.warning(message);
    }

    private static synchronized boolean write(String level, String message) {
        if (writer == null) return false;
        try {
            writer.write("[" + LocalDateTime.now().format(TIME) + " " + level + "] " + message);
            writer.newLine();
            writer.flush();
            return true;
        } catch (IOException e) {
            close();
            console.warning("Could not write the migration log; logging to the console instead: " + e.getMessage());
            return false;
        }
    }
}
