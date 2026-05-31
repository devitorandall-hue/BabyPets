package net.legacy.babypets.model;

/**
 * Five progression tiers for a pet, unlocked by accumulating total XP.
 *
 *  Tier 1 — Baby       (    0–99 XP) : follows the owner (base behaviour)
 *  Tier 2 — Scout      ( 100–299 XP) : mob radar — warns owner of nearby hostiles
 *  Tier 3 — Pack Mule  ( 300–599 XP) : small 3-slot inventory
 *  Tier 4 — Companion  ( 600–999 XP) : passive regen aura while the owner stands still
 *  Tier 5 — Elder      (1000+    XP) : wayfinder — remembers up to 3 saved locations
 */
public enum PetTier {

    BABY     (1, "Baby",      "§f",  0,    99),
    SCOUT    (2, "Scout",     "§a",  100,  299),
    PACK_MULE(3, "Pack Mule", "§6",  300,  599),
    COMPANION(4, "Companion", "§b",  600,  999),
    ELDER    (5, "Elder",     "§e",  1000, Integer.MAX_VALUE);

    private final int  number;
    private final String displayName;
    private final String color;      // legacy §-code prefix
    private final int  minXp;
    private final int  maxXp;

    PetTier(int number, String displayName, String color, int minXp, int maxXp) {
        this.number      = number;
        this.displayName = displayName;
        this.color       = color;
        this.minXp       = minXp;
        this.maxXp       = maxXp;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int    getTierNumber()  { return number; }
    public String getDisplayName() { return displayName; }
    public String getColor()       { return color; }
    public int    getMinXp()       { return minXp; }
    public boolean isMaxTier()     { return this == ELDER; }

    /** Colorised display string, e.g. "§ePack Mule". */
    public String getColoredName() { return color + displayName; }

    // ── XP maths ──────────────────────────────────────────────────────────────

    /** XP earned within this tier (0 = just entered the tier). */
    public int getXpWithinTier(int totalXp) {
        return Math.max(0, totalXp - minXp);
    }

    /** Total XP span of this tier (distance to the next tier). */
    public int getTierSpan() {
        if (isMaxTier()) return Integer.MAX_VALUE;
        return values()[ordinal() + 1].minXp - minXp;
    }

    /** XP still needed to advance to the next tier. */
    public int getXpToNextTier(int totalXp) {
        if (isMaxTier()) return Integer.MAX_VALUE;
        return values()[ordinal() + 1].minXp - totalXp;
    }

    public PetTier next() {
        return isMaxTier() ? this : values()[ordinal() + 1];
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Derive the correct tier for a given accumulated XP value. */
    public static PetTier fromTotalXp(int totalXp) {
        PetTier result = BABY;
        for (PetTier t : values()) {
            if (totalXp >= t.minXp) result = t;
        }
        return result;
    }
}
