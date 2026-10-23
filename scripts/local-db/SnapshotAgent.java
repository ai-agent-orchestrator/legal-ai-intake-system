import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;

/** Local maintenance only: exports the already-running H2 without a new HTTP endpoint. */
public class SnapshotAgent {
    private static Connection held;
    public static void agentmain(String argument, Instrumentation instrumentation) {
        Path directory = null;
        try {
            String[] args = argument.split("\\|", 3);
            if (args[0].equals("release")) { if (held != null) { held.close(); held = null; } return; }
            directory = Path.of(args[1]).toAbsolutePath().normalize();
            if (!directory.toString().replace('\\','/').contains("/backend/guardrail-ready-policy-checker/.local/")) throw new IllegalArgumentException("Private backup directory required");
            Files.createDirectories(directory);
            Path script = directory.resolve("rows.sql");
            if (Files.exists(script)) throw new IllegalStateException("Backup already exists");
            Class<?> driverClass = Arrays.stream(instrumentation.getAllLoadedClasses()).filter(c -> c.getName().equals("org.h2.Driver")).findFirst().orElseThrow();
            Driver driver = (Driver) driverClass.getConstructor().newInstance();
            Properties login = new Properties(); login.setProperty("user", "sa"); login.setProperty("password", "");
            Connection connection = driver.connect("jdbc:h2:mem:observability_practice", login);
            boolean freeze = args[0].equals("freeze");
            try {
                if (freeze) connection.createStatement().execute("SET EXCLUSIVE 1");
                Properties manifest = snapshot(connection);
                try (Writer writer = Files.newBufferedWriter(script)) {
                    for (String table : manifest.getProperty("tables").split(",")) {
                        List<String> columns = List.of(manifest.getProperty(table+".columns").split(","));
                        String prefix = "INSERT INTO \""+table+"\" ("+columns.stream().map(c->"\""+c+"\"").reduce((a,b)->a+","+b).orElseThrow()+") VALUES (";
                        try (ResultSet rows = connection.createStatement().executeQuery("SELECT * FROM \""+table+"\"")) {
                            while (rows.next()) {
                                List<String> values = new ArrayList<>();
                                for (int i=1; i<=columns.size(); i++) {
                                    Object value = rows.getObject(i);
                                    values.add(value == null ? "NULL" : value instanceof Blob blob ? "X'"+HexFormat.of().formatHex(blob.getBytes(1,Math.toIntExact(blob.length())))+"'" : value instanceof byte[] bytes ? "X'"+HexFormat.of().formatHex(bytes)+"'" : value instanceof Number || value instanceof Boolean ? value.toString() : "'"+rows.getString(i).replace("'","''")+"'");
                                }
                                writer.write(prefix+String.join(",",values)+");\n");
                            }
                        }
                    }
                }
                manifest.setProperty("format", "data-only SQL; restore into a freshly initialized schema, never into the live database");
                manifest.setProperty("frozen", Boolean.toString(freeze));
                try (Writer writer = Files.newBufferedWriter(directory.resolve("manifest.properties"))) { manifest.store(writer, "Local private H2 backup; contains hashes, never row values"); }
                if (freeze) {
                    held = connection;
                    Thread timeout = new Thread(() -> { try { Thread.sleep(180000); if (held == connection) { connection.close(); held = null; } } catch (Exception ignored) {} }, "local-backup-freeze-timeout");
                    timeout.setDaemon(true); timeout.start();
                }
            } finally { if (!freeze || held != connection) connection.close(); }
        } catch (Throwable error) {
            if (directory != null) try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(directory.resolve("failed.txt")))) { error.printStackTrace(writer); } catch (IOException ignored) {}
        }
    }
    public static Properties snapshot(Connection connection) throws Exception {
        Properties result = new Properties(); List<String> tables = new ArrayList<>();
        try (ResultSet rows = connection.createStatement().executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='public' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")) { while (rows.next()) tables.add(rows.getString(1)); }
        result.setProperty("tables", String.join(",", tables));
        for (String table : tables) {
            List<String> columns = new ArrayList<>();
            try (ResultSet rows = connection.createStatement().executeQuery("SELECT * FROM \"" + table + "\" WHERE 1=0")) { ResultSetMetaData meta = rows.getMetaData(); for (int i=1; i<=meta.getColumnCount(); i++) columns.add(meta.getColumnName(i)); }
            result.setProperty(table + ".columns", String.join(",", columns));
            String[] digest = digest(connection, table, columns, '"');
            result.setProperty(table + ".count", digest[0]); result.setProperty(table + ".sha256", digest[1]);
        }
        return result;
    }
    public static String[] digest(Connection connection, String table, List<String> columns, char quote) throws Exception {
        String q = Character.toString(quote);
        if (!table.matches("[a-zA-Z0-9_]+") || columns.stream().anyMatch(c -> !c.matches("[a-zA-Z0-9_]+"))) throw new IllegalArgumentException("Unexpected identifier");
        List<String> rowHashes = new ArrayList<>();
        try (ResultSet rows = connection.createStatement().executeQuery("SELECT " + columns.stream().map(c -> q+c+q).reduce((a,b)->a+","+b).orElseThrow() + " FROM " + q+table+q)) {
            while (rows.next()) {
                MessageDigest row = MessageDigest.getInstance("SHA-256");
                for (int i=1; i<=columns.size(); i++) {
                    Object value = rows.getObject(i);
                    byte[] bytes = value == null ? null : value instanceof Blob blob ? blob.getBytes(1, Math.toIntExact(blob.length())) : value instanceof byte[] binary ? binary : rows.getString(i).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    row.update(java.nio.ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array()); if (bytes != null) row.update(bytes);
                }
                rowHashes.add(HexFormat.of().formatHex(row.digest()));
            }
        }
        Collections.sort(rowHashes); MessageDigest all = MessageDigest.getInstance("SHA-256");
        for (String row : rowHashes) all.update(row.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return new String[]{Integer.toString(rowHashes.size()), HexFormat.of().formatHex(all.digest())};
    }
}
