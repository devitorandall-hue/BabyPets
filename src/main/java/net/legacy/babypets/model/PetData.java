package net.legacy.babypets.model;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetData {

    private final UUID       ownerUuid;
    private final UUID       petUuid;
    private final EntityType type;
    private       String     name;
    private       String     biome;
    private       Location   lastLocation;
    private       boolean    active;
    private       boolean    sitting;

    // ── Progression ───────────────────────────────────────────────────────────

    /** Cumulative XP, never resets. Tier is derived from this value. */
    private int totalXp;

    /** Purchasable upgrades: upgrade name → current level (0 = not purchased). */
    private final Map<String, Integer> upgrades;

    // ── Tier perks ────────────────────────────────────────────────────────────

    /** Tier 3 (Pack Mule) — 3-slot satchel inventory. */
    private final ItemStack[] petInventory;

    /**
     * Tier 5 (Elder) — up to 3 saved waypoints.
     * Each element is either {@code null} (empty) or {@code "world,x,y,z"}.
     */
    private final String[] waypoints;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Full constructor used by the persistence layer. */
    public PetData(UUID ownerUuid, UUID petUuid, EntityType type, String name, String biome,
                   Location lastLocation, boolean active, boolean sitting,
                   int totalXp, Map<String, Integer> upgrades,
                   ItemStack[] petInventory, String[] waypoints) {
        this.ownerUuid    = ownerUuid;
        this.petUuid      = petUuid;
        this.type         = type;
        this.name         = name;
        this.biome        = biome != null ? biome : "Plains";
        this.lastLocation = lastLocation;
        this.active       = active;
        this.sitting      = sitting;
        this.totalXp      = Math.max(0, totalXp);
        this.upgrades     = upgrades != null ? new HashMap<>(upgrades) : new HashMap<>();
        this.petInventory = petInventory != null ? petInventory : new ItemStack[3];
        this.waypoints    = waypoints    != null ? waypoints    : new String[3];
    }

    /**
     * Legacy migration constructor.  Accepts the old {@code level} and {@code xp}
     * (per-level, resets on level-up) and converts them to cumulative {@code totalXp}.
     */
    public PetData(UUID ownerUuid, UUID petUuid, EntityType type, String name, String biome,
                   Location lastLocation, boolean active, boolean sitting,
                   int oldLevel, int oldXp, Map<String, Integer> upgrades) {
        this(ownerUuid, petUuid, type, name, biome, lastLocation, active, sitting,
             legacyToTotalXp(oldLevel, oldXp), upgrades, null, null);
    }

    /** Minimal constructor for freshly created pets (totalXp=0, no upgrades). */
    public PetData(UUID ownerUuid, UUID petUuid, EntityType type, String name, String biome,
                   Location lastLocation, boolean active, boolean sitting) {
        this(ownerUuid, petUuid, type, name, biome, lastLocation, active, sitting,
             0, null, null, null);
    }

    // ── Basic getters / setters ───────────────────────────────────────────────

    public UUID       getOwnerUuid()        { return ownerUuid; }
    public UUID       getPetUuid()          { return petUuid; }
    public EntityType getType()             { return type; }
    public String     getName()             { return name; }
    public String     getBiome()            { return biome; }
    public Location   getLastLocation()     { return lastLocation; }
    public boolean    isActive()            { return active; }
    public boolean    isSitting()           { return sitting; }

    public void setName(String name)          { this.name = name; }
    public void setBiome(String biome)        { this.biome = biome != null ? biome : "Plains"; }
    public void setLastLocation(Location loc) { this.lastLocation = loc; }
    public void setActive(boolean active)     { this.active = active; }
    public void setSitting(boolean sitting)   { this.sitting = sitting; }

    // ── XP & Tier ─────────────────────────────────────────────────────────────

    public int getTotalXp() { return totalXp; }

    /** Derive the pet's current tier from accumulated XP. */
    public PetTier getTier() { return PetTier.fromTotalXp(totalXp); }

    /**
     * Adds XP to this pet.
     *
     * @return {@code true} if the pet advanced to a new tier; {@code false} otherwise.
     */
    public boolean addXp(int amount) {
        PetTier before = getTier();
        totalXp += amount;
        return getTier() != before;
    }

    // ── Upgrades ──────────────────────────────────────────────────────────────

    public Map<String, Integer> getUpgrades() { return upgrades; }

    public int getUpgradeLevel(PetUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade.name(), 0);
    }

    public void setUpgradeLevel(PetUpgrade upgrade, int level) {
        if (level <= 0) upgrades.remove(upgrade.name());
        else            upgrades.put(upgrade.name(), Math.min(level, upgrade.getMaxLevel()));
    }

    // ── Tier-3 Pack Mule inventory ────────────────────────────────────────────

    /** Returns the 3-element backing array (elements may be null/AIR). */
    public ItemStack[] getPetInventory() { return petInventory; }

    // ── Tier-5 Elder waypoints ────────────────────────────────────────────────

    /** Returns the 3-element backing array. Each element is null or "world,x,y,z". */
    public String[] getWaypoints() { return waypoints; }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Converts the old level-based XP format to cumulative totalXp.
     *
     * <p>The old system used {@code xpToNextLevel(level) = level × 50}, resetting
     * on each level-up.  To reach level L from level 1 took
     * {@code 50 × (L-1)×L/2} total XP.</p>
     */
    public static int legacyToTotalXp(int level, int xp) {
        int base = 50 * (Math.max(1, level) - 1) * Math.max(1, level) / 2;
        return base + Math.max(0, xp);
    }
}
