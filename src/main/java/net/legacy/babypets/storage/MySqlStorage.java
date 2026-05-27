package net.legacy.babypets.storage;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MySqlStorage {

    private final BabyPetsPlugin plugin;
    private final String table;
    private volatile boolean available = true;

    public MySqlStorage(BabyPetsPlugin plugin) {
        this.plugin = plugin;
        this.table = plugin.getConfig().getString("mysql.table", "babypets_pets");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::createTable);
    }

    public boolean isAvailable() {
        return available;
    }

    private Connection getConnection() throws SQLException {
        String host     = plugin.getConfig().getString("mysql.host", "localhost");
        int    port     = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "babypets");
        String username = plugin.getConfig().getString("mysql.username", "babypets");
        String password = plugin.getConfig().getString("mysql.password", "");
        boolean useSsl  = plugin.getConfig().getBoolean("mysql.use-ssl", false);

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&autoReconnect=true"
                + "&characterEncoding=utf8"
                + "&connectTimeout=3000"
                + "&socketTimeout=10000";

        return DriverManager.getConnection(url, username, password);
    }

    private void createTable() {
        String createSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    pet_uuid    VARCHAR(36)   NOT NULL PRIMARY KEY,
                    owner_uuid  VARCHAR(36)   NOT NULL,
                    type        VARCHAR(64)   NOT NULL,
                    name        TEXT          NOT NULL,
                    biome       VARCHAR(64)   NOT NULL DEFAULT 'Plains',
                    world       VARCHAR(128),
                    x           DOUBLE,
                    y           DOUBLE,
                    z           DOUBLE,
                    active      BOOLEAN       NOT NULL DEFAULT FALSE,
                    sitting     BOOLEAN       NOT NULL DEFAULT FALSE,
                    level       INT           NOT NULL DEFAULT 1,
                    xp          INT           NOT NULL DEFAULT 0,
                    upgrades    VARCHAR(512)  NOT NULL DEFAULT '',
                    server_name VARCHAR(64),
                    updated_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_owner_uuid  (owner_uuid),
                    INDEX idx_server_name (server_name)
                );
                """.formatted(table);

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(createSql);

            // Migrate older tables that are missing columns
            for (String alter : new String[]{
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS biome    VARCHAR(64)  NOT NULL DEFAULT 'Plains'",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS level    INT          NOT NULL DEFAULT 1",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS xp       INT          NOT NULL DEFAULT 0",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS upgrades VARCHAR(512) NOT NULL DEFAULT ''"
            }) {
                statement.executeUpdate(alter);
            }

            plugin.getLogger().info("BabyPets MySQL table is ready.");
        } catch (SQLException e) {
            available = false;
            plugin.getLogger().warning("[BabyPets] Could not connect to MySQL — falling back to YAML storage.");
            plugin.getLogger().warning("[BabyPets] Reason: " + e.getMessage());
        }
    }

    public List<PetData> loadPets(UUID ownerUuid) {
        if (!available) return new ArrayList<>();
        List<PetData> pets = new ArrayList<>();

        String sql = "SELECT * FROM " + table + " WHERE owner_uuid=?";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, ownerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID       petUuid = UUID.fromString(rs.getString("pet_uuid"));
                    EntityType type    = EntityType.valueOf(rs.getString("type"));
                    String     name    = rs.getString("name");
                    String     biome   = rs.getString("biome");
                    boolean    active  = rs.getBoolean("active");
                    boolean    sitting = rs.getBoolean("sitting");

                    // Level, XP, upgrades — null-safe for very old rows
                    int level = 1;
                    int xp    = 0;
                    Map<String, Integer> upgrades = new HashMap<>();
                    try {
                        level    = rs.getInt("level");
                        xp       = rs.getInt("xp");
                        upgrades = deserializeUpgrades(rs.getString("upgrades"));
                    } catch (SQLException ignored) {
                        // columns may not exist yet on a freshly-migrated server
                    }

                    Location loc = null;
                    String worldName = rs.getString("world");

                    if (worldName != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            loc = new Location(world,
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"));
                        }
                    }

                    pets.add(new PetData(ownerUuid, petUuid, type, name, biome,
                            loc, active, sitting, level, xp, upgrades));
                }
            }

        } catch (Exception e) {
            markUnavailable(e);
        }

        return pets;
    }

    public void savePet(PetData data, String serverName) {
        if (!available) return;

        String sql = """
                INSERT INTO %s
                (pet_uuid, owner_uuid, type, name, biome, world, x, y, z, active, sitting, level, xp, upgrades, server_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid=VALUES(owner_uuid),
                    type=VALUES(type),
                    name=VALUES(name),
                    biome=VALUES(biome),
                    world=VALUES(world),
                    x=VALUES(x),
                    y=VALUES(y),
                    z=VALUES(z),
                    active=VALUES(active),
                    sitting=VALUES(sitting),
                    level=VALUES(level),
                    xp=VALUES(xp),
                    upgrades=VALUES(upgrades),
                    server_name=VALUES(server_name)
                """.formatted(table);

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1,  data.getPetUuid().toString());
            ps.setString(2,  data.getOwnerUuid().toString());
            ps.setString(3,  data.getType().name());
            ps.setString(4,  data.getName());
            ps.setString(5,  data.getBiome());

            Location loc = data.getLastLocation();
            if (loc != null && loc.getWorld() != null) {
                ps.setString(6, loc.getWorld().getName());
                ps.setDouble(7, loc.getX());
                ps.setDouble(8, loc.getY());
                ps.setDouble(9, loc.getZ());
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setDouble(7, 0);
                ps.setDouble(8, 0);
                ps.setDouble(9, 0);
            }

            ps.setBoolean(10, data.isActive());
            ps.setBoolean(11, data.isSitting());
            ps.setInt    (12, data.getLevel());
            ps.setInt    (13, data.getXp());
            ps.setString (14, serializeUpgrades(data.getUpgrades()));
            ps.setString (15, serverName);

            ps.executeUpdate();

        } catch (SQLException e) {
            markUnavailable(e);
        }
    }

    public void deletePet(UUID petUuid) {
        if (!available) return;
        String sql = "DELETE FROM " + table + " WHERE pet_uuid=?";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, petUuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            markUnavailable(e);
        }
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    /** Converts the upgrades map to a compact {@code KEY:VALUE,...} string. */
    private String serializeUpgrades(Map<String, Integer> upgrades) {
        if (upgrades == null || upgrades.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
            if (entry.getValue() > 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(entry.getKey()).append(':').append(entry.getValue());
            }
        }
        return sb.toString();
    }

    /** Parses a {@code KEY:VALUE,...} string back into an upgrades map. */
    private Map<String, Integer> deserializeUpgrades(String data) {
        Map<String, Integer> map = new HashMap<>();
        if (data == null || data.isEmpty()) return map;
        for (String part : data.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                try {
                    int val = Integer.parseInt(kv[1].trim());
                    if (val > 0) map.put(kv[0].trim(), val);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private void markUnavailable(Exception e) {
        if (available) {
            available = false;
            plugin.getLogger().warning("[BabyPets] Lost MySQL connection — falling back to YAML storage.");
            plugin.getLogger().warning("[BabyPets] Reason: " + e.getMessage());
        }
    }
}
