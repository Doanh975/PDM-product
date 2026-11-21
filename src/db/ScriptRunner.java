package db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates the database by running the team's SQL scripts (01_schema_and_data.sql, then
 * 01b_triggers.sql) through JDBC, so a new machine does not need DBeaver or the mysql CLI.
 * Understands "DELIMITER $$" blocks like the mysql client does.
 */
public final class ScriptRunner {

    public static final String[] SCRIPTS = {"01_schema_and_data.sql", "01b_triggers.sql"};

    private ScriptRunner() {}

    /** Server URL without the database name, e.g. jdbc:mysql://localhost:3306/?useSSL=false */
    public static String serverUrl(String dbUrl) {
        return dbUrl.replaceFirst("^(jdbc:mysql://[^/?]+)/[^?]*", "$1/");
    }

    /** Drops and recreates employee_attendance_db (the scripts do that themselves) and loads the sample data. */
    public static void installDatabase(String dbUrl, String user, String password, Consumer<String> log)
            throws SQLException, IOException {
        try (Connection c = DriverManager.getConnection(serverUrl(dbUrl), user, password);
             Statement st = c.createStatement()) {
            for (String name : SCRIPTS) {
                List<String> statements = split(read(name));
                log.accept("Running " + name + " (" + statements.size() + " statements)...");
                for (String sql : statements) {
                    try {
                        st.execute(sql);
                    } catch (SQLException e) {
                        if (e.getErrorCode() == 1419 || e.getErrorCode() == 1227 || e.getErrorCode() == 1142) {
                            throw new SQLException("The data was loaded, but this MySQL user may not create triggers. "
                                    + "Install again while signed in to MySQL as root (or another admin account), "
                                    + "or run sql/" + name + " in DBeaver / MySQL Workbench.", e.getSQLState(), 99001, e);
                        }
                        throw e;
                    }
                }
            }
        }
        SchemaInfo.clearCache();
        log.accept("Database is ready.");
    }

    /** Looks for sql/<name> next to config.properties, then in the working directory, then inside the jar. */
    static String read(String name) throws IOException {
        List<Path> candidates = new ArrayList<>();
        Path cfg = DBConnection.getConfigPath();
        if (cfg != null && cfg.getParent() != null) candidates.add(cfg.getParent().resolve("sql").resolve(name));
        candidates.add(Path.of("sql", name));
        for (Path p : candidates) {
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        try (InputStream in = ScriptRunner.class.getResourceAsStream("/sql/" + name)) {
            if (in == null) throw new IOException("SQL script " + name + " was not found (looked in sql/ and inside the app).");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Splits a script into statements, honoring DELIMITER changes and skipping comment-only lines. */
    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        String delimiter = ";";
        StringBuilder cur = new StringBuilder();
        for (String raw : script.split("\\R")) {
            String line = raw.strip();
            if (cur.length() == 0 && (line.isEmpty() || line.startsWith("--") || line.startsWith("#"))) continue;
            if (line.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = line.substring(10).strip();
                continue;
            }
            cur.append(raw).append('\n');
            if (line.endsWith(delimiter)) {
                String stmt = cur.toString().strip();
                stmt = stmt.substring(0, stmt.length() - delimiter.length()).strip();
                if (!stmt.isEmpty()) out.add(stmt);
                cur.setLength(0);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().strip());
        return out;
    }
}
