package db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Central place for JDBC connections. Connection settings live in an external
 * config.properties file (outside the compiled classes), searched in this order:
 *   1. -Dconfig=/path/to/config.properties
 *   2. ./config.properties (current working directory)
 *   3. config.properties next to the bin/ folder or jar the app was started from
 */
public final class DBConnection {

    private static final Properties PROPS = new Properties();
    private static Path configPath;

    private DBConnection() {}

    public static synchronized void load() {
        PROPS.clear();
        configPath = locateConfig();
        if (configPath != null && Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                PROPS.load(in);
            } catch (IOException e) {
                System.err.println("Could not read " + configPath + ": " + e.getMessage());
            }
        }
    }

    private static Path locateConfig() {
        String explicit = System.getProperty("config");
        if (explicit != null && !explicit.isBlank()) return Paths.get(explicit).toAbsolutePath();

        Path cwd = Paths.get("config.properties").toAbsolutePath();
        if (Files.exists(cwd)) return cwd;

        try {
            Path codeLocation = Paths.get(DBConnection.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path parent = codeLocation.getParent();
            if (parent != null) {
                Path candidate = parent.resolve("config.properties");
                if (Files.exists(candidate)) return candidate;
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // fall through
        }
        return cwd; // will be created on first save
    }

    public static Path getConfigPath() { return configPath; }

    public static String get(String key, String def) {
        String v = PROPS.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static boolean getBool(String key, boolean def) {
        String v = PROPS.getProperty(key);
        return (v == null || v.isBlank()) ? def : Boolean.parseBoolean(v.trim());
    }

    public static String url()      { return get("db.url", "jdbc:mysql://localhost:3306/employee_attendance_db"); }
    public static String user()     { return get("db.user", "root"); }
    public static String password() { String p = PROPS.getProperty("db.password"); return p == null ? "" : p; }

    /** "Today" for default query parameters; configurable so demos match the sample data. */
    public static LocalDate referenceDate() {
        String v = get("app.referenceDate", "");
        if (!v.isEmpty()) {
            try { return LocalDate.parse(v); } catch (Exception ignored) { /* use real date */ }
        }
        return LocalDate.now();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url(), user(), password());
    }

    /** Opens and closes a connection with the given settings; throws if it fails. */
    public static void testConnection(String url, String user, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.isValid(3);
        }
    }

    /** Persists new connection settings to config.properties and reloads. */
    public static synchronized void saveDbSettings(String url, String user, String password) throws IOException {
        PROPS.setProperty("db.url", url);
        PROPS.setProperty("db.user", user);
        PROPS.setProperty("db.password", password);
        Path target = configPath != null ? configPath : Paths.get("config.properties").toAbsolutePath();
        // Rewrite only the three db.* lines so comments and other settings survive.
        List<String> lines = Files.exists(target) ? Files.readAllLines(target) : new ArrayList<>();
        boolean u = false, us = false, p = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.startsWith("db.url="))      { lines.set(i, "db.url=" + url); u = true; }
            else if (l.startsWith("db.user=")) { lines.set(i, "db.user=" + user); us = true; }
            else if (l.startsWith("db.password=")) { lines.set(i, "db.password=" + password); p = true; }
        }
        if (!u) lines.add("db.url=" + url);
        if (!us) lines.add("db.user=" + user);
        if (!p) lines.add("db.password=" + password);
        try (OutputStream out = Files.newOutputStream(target)) {
            out.write((String.join(System.lineSeparator(), lines) + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8));
        }
        configPath = target;
        load();
        SchemaInfo.clearCache();
    }
}
