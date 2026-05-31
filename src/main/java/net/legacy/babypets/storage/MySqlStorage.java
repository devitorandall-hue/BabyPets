package net.legacy.babypets.storage;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MySqlStorage {

    private final BabyPetsPlugin plugin;
    private final String         table;
    private volatile boolean     available = true;

    public MySqlStorage(BabyPetsPlugin plugin) {
        this.plugin = plugin;
        this.table  = plugin.getConfig().getString("mysql.table", "babypets_pets");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::createTable);
    }

    public boolean isAvailable() { return available; }

    private Connection getConnection() throws SQLException {
        String  host     = plugin.getConfig().getString("mysql.host", "localhost");
        int     port     = plugin.getConfig().getInt("mysql.port", 3306);
        String  database = plugin.getConfig().getString("mysql.database", "babypets");
        String  username = plugin.getConfig().getString("mysql.username", "babypets");
        String  password = plugin.getConfig().getString("mysql.password", "");
        boolean useSsl   = plugin.getConfig().getBoolean("mysql.use-ssl", false);

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
                    pet_uuid      VARCHAR(36)   NOT NULL PRIMARY KEY,
                    owner_uuid    VARCHAR(36)   NOT NULL,
                    type          VARCHAR(64)   NOT NULL,
                    name          TEXT          NOT NULL,
                    biome         VARCHAR(64)   NOT NULL DEFAULT 'Plains',
                    world         VARCHAR(128),
                    x             DOUBLE,
                    y             DOUBLE,
                    z             DOUBLE,
                    active        BOOLEAN       NOT NULL DEFAULT FALSE,
                    sitting       BOOLEAN       NOT NULL DEFAULT FALSE,
                    level         INT           NOT NULL DEFAULT 1,
                    xp            INT           NOT NULL DEFAULT 0,
                    upgrades      VARCHAR(512)  NOT NULL DEFAULT '',
                    total_xp      INT           NOT NULL DEFAULT 0,
                    pet_inventory TEXT          NOT NULL DEFAULT '',
                    waypoints     VARCHAR(512)  NOT NULL DEFAULT '',
                    server_name   VARCHAR(64),
                    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_owner_uuid  (owner_uuid),
                    INDEX idx_server_name (server_name)
                );
                """.formatted(table);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(createSql);

            // Migrate older tables that may be missing newer columns
            for (String alter : new String[]{
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS biome         VARCHAR(64)  NOT NULL DEFAULT 'Plains'",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS level         INT          NOT NULL DEFAULT 1",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS xp            INT          NOT NULL DEFAULT 0",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS upgrades      VARCHAR(512) NOT NULL DEFAULT ''",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS total_xp      INT          NOT NULL DEFAULT 0",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS pet_inventory TEXT         NOT NULL DEFAULT ''",
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS waypoints     VARCHAR(512) NOT NULL DEFAULT ''"
            }) {
                stmt.executeUpdate(alter);
            }

            plugin.getLogger().info("BabyPets MySQL table is ready.");
        } catch (SQLException e) {
            available = false;
            plugin.getLogger().warning("[BabyPets] Could not connect to MySQL — falling back to YAML storage.");
            plugin.getLogger().warning("[BabyPets] Reason: " + e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public List<PetData> loadPets(UUID ownerUuid) {
        if (!available) return new ArrayList<>();
        List<PetData> pets = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM " + table + " WHERE owner_uuid=?")) {

            ps.setString(1, ownerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID       petUuid = UUID.fromString(rs.getString("pet_uuid"));
                    EntityType type    = EntityType.valueOf(rs.getString("type"));
                    String     name    = rs.getString("name");
                    String     biome   = rs.getString("biome");
                    boolean    active  = rs.getBoolean("active");
                    boolean    sitting = rs.getBoolean("sitting");

                    // ── XP: prefer total_xp; fall back to legacy level/xp ────
                    int totalXp = 0, level = 1, xp = 0;
                    Map<String, Integer> upgrades = new HashMap<>();
                    try {
                        totalXp  = rs.getInt("total_xp");
                        level    = rs.getInt("level");
                        xp       = rs.getInt("xp");
                        upgrades = deserializeUpgrades(rs.getString("upgrades"));
                    } catch (SQLException ignored) {}

                    // Migration: if total_xp not stored yet, convert from legacy
                    if (totalXp == 0 && level > 1) {
                        totalXp = PetData.legacyToTotalXp(level, xp);
                    }

                    // ── Pet inventory ─────────────────────────────────────────
                    ItemStack[] petInventory = new ItemStack[3];
                    try { petInventory = deserializeInventory(rs.getString("pet_inventory")); }
                    catch (SQLException ignored) {}

                    // ── Waypoints ─────────────────────────────────────────────
                    String[] waypoints = new String[3];
                    try { waypoints = deserializeWaypoints(rs.getString("waypoints")); }
                    catch (SQLException ignored) {}

                    // ── Location ──────────────────────────────────────────────
                    Location loc = null;
                    String worldName = rs.getString("world");
                    if (worldName != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                        }
                    }

                    pets.add(new PetData(ownerUuid, petUuid, type, name, biome, loc, active, sitting,
                            totalXp, upgrades, petInventory, waypoints));
                }
            }
        } catch (Exception e) {
            markUnavailable(e);
        }

        return pets;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void savePet(PetData data, String serverName) {
        if (!available) return;

        String sql = """
                INSERT INTO %s
                (pet_uuid, owner_uuid, type, name, biome, world, x, y, z, active, sitting,
                 level, xp, upgrades, total_xp, pet_inventory, waypoints, server_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    total_xp=VALUES(total_xp),
                    pet_inventory=VALUES(pet_inventory),
                    waypoints=VALUES(waypoints),
                    server_name=VALUES(server_name)
                """.formatted(table);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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
            ps.setInt    (12, 0);                                       // legacy level (keep for compat)
            ps.setInt    (13, 0);                                       // legacy xp (keep for compat)
            ps.setString (14, serializeUpgrades(data.getUpgrades()));
            ps.setInt    (15, data.getTotalXp());
            ps.setString (16, serializeInventory(data.getPetInventory()));
            ps.setString (17, serializeWaypoints(data.getWaypoints()));
            ps.setString (18, serverName);

            ps.executeUpdate();

        } catch (SQLException e) {
            markUnavailable(e);
        }
    }

    public void deletePet(UUID petUuid) {
        if (!available) return;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + table + " WHERE pet_uuid=?")) {
            ps.setString(1, petUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            markUnavailable(e);
        }
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

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

    private Map<String, Integer> deserializeUpgrades(String data) {
        Map<String, Integer> map = new HashMap<>();
        if (data == null || data.isEmpty()) return map;
        for (String part : data.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                try {
                    int val = Integer.parseInt(kv[1].trim());
                    if (val > 0) map.put(kv[0].trim(), val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * Serializes 3 ItemStacks to a Base64 string per slot, separated by {@code |}.
     * An empty/null slot is represented by an empty segment.
     */
    private String serializeInventory(ItemStack[] items) {
        if (items == null) return "||";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append('|');
            ItemStack item = (i < items.length) ? items[i] : null;
            if (item != null && item.getType() != Material.AIR) {
                try {
                    sb.append(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                } catch (Exception ignored) {}
            }
        }
        return sb.toString();
    }

    private ItemStack[] deserializeInventory(String data) {
        ItemStack[] items = new ItemStack[3];
        if (data == null || data.isEmpty()) return items;
        String[] parts = data.split("\\|", -1);
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            if (!parts[i].isEmpty()) {
                try {
                    items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
                } catch (Exception ignored) {}
            }
        }
        return items;
    }

    /**
     * Serializes 3 waypoint strings (each "world,x,y,z" or null) to a {@code |}-separated string.
     */
    private String serializeWaypoints(String[] waypoints) {
        if (waypoints == null) return "||";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append('|');
            if (waypoints.length > i && waypoints[i] != null) sb.append(waypoints[i]);
        }
        return sb.toString();
    }

    private String[] deserializeWaypoints(String data) {
        String[] waypoints = new String[3];
        if (data == null || data.isEmpty()) return waypoints;
        String[] parts = data.split("\\|", -1);
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            waypoints[i] = parts[i].isEmpty() ? null : parts[i];
        }
        return waypoints;
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
