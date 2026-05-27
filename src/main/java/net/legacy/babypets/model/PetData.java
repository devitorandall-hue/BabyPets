package net.legacy.babypets.model;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetData {

    private final UUID ownerUuid;
    private final UUID petUuid;
    private final EntityType type;
    private String name;
    private String biome;
    private Location lastLocation;
    private boolean active;
    private boolean sitting;
    private int level;
    private int xp;
    private final Map<String, Integer> upgrades;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Full constructor including level, XP, and upgrades. */
    public PetData(UUID ownerUuid, UUID petUuid, EntityType type, String name, String biome,
                   Location lastLocation, boolean active, boolean sitting,
                   int level, int xp, Map<String, Integer> upgrades) {
        this.ownerUuid    = ownerUuid;
        this.petUuid      = petUuid;
        this.type         = type;
        this.name         = name;
        this.biome        = biome != null ? biome : "Plains";
        this.lastLocation = lastLocation;
        this.active       = active;
        this.sitting      = sitting;
        this.level        = Math.max(1, level);
        this.xp           = Math.max(0, xp);
        this.upgrades     = upgrades != null ? new HashMap<>(upgrades) : new HashMap<>();
    }

    /**
     * Backward-compatible constructor — sets level=1, xp=0, no upgrades.
     * Used when loading older data that predates the leveling system.
     */
    public PetData(UUID ownerUuid, UUID petUuid, EntityType type, String name, String biome,
                   Location lastLocation, boolean active, boolean sitting) {
        this(ownerUuid, petUuid, type, name, biome, lastLocation, active, sitting, 1, 0, null);
    }

    // ── Basic getters ─────────────────────────────────────────────────────────

    public UUID getOwnerUuid()        { return ownerUuid; }
    public UUID getPetUuid()          { return petUuid; }
    public EntityType getType()       { return type; }
    public String getName()           { return name; }
    public String getBiome()          { return biome; }
    public Location getLastLocation() { return lastLocation; }
    public boolean isActive()         { return active; }
    public boolean isSitting()        { return sitting; }

    // ── Level & XP ───────────────────────────────────────────────────────────

    public int getLevel() { return level; }
    public int getXp()    { return xp; }

    /**
     * XP required to advance from {@code currentLevel} to the next level.
     * Returns {@link Integer#MAX_VALUE} at level 30 (cap).
     */
    public static int xpToNextLevel(int currentLevel) {
        if (currentLevel >= 30) return Integer.MAX_VALUE;
        return currentLevel * 50;
    }

    /**
     * Adds XP and handles level-ups automatically.
     *
     * @return {@code true} if the pet leveled up, {@code false} otherwise.
     */
    public boolean addXp(int amount) {
        if (level >= 30) return false;
        xp += amount;
        int needed = xpToNextLevel(level);
        if (xp >= needed) {
            xp -= needed;
            level = Math.min(30, level + 1);
            return true;
        }
        return false;
    }

    // ── Upgrades ──────────────────────────────────────────────────────────────

    public Map<String, Integer> getUpgrades() { return upgrades; }

    public int getUpgradeLevel(PetUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade.name(), 0);
    }

    public void setUpgradeLevel(PetUpgrade upgrade, int level) {
        if (level <= 0) {
            upgrades.remove(upgrade.name());
        } else {
            upgrades.put(upgrade.name(), Math.min(level, upgrade.getMaxLevel()));
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setName(String name)           { this.name = name; }
    public void setBiome(String biome)         { this.biome = biome != null ? biome : "Plains"; }
    public void setLastLocation(Location loc)  { this.lastLocation = loc; }
    public void setActive(boolean active)      { this.active = active; }
    public void setSitting(boolean sitting)    { this.sitting = sitting; }
    public void setLevel(int level)            { this.level = Math.max(1, Math.min(30, level)); }
    public void setXp(int xp)                  { this.xp = Math.max(0, xp); }
}
