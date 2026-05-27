package net.legacy.babypets.model;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/**
 * Represents a purchasable upgrade for a pet.
 * Each upgrade has 5 levels; costs are paid in player XP levels.
 */
public enum PetUpgrade {

    XP_BOOST("XP Boost", Material.EXPERIENCE_BOTTLE,
            "Earn bonus player XP from mob kills while this pet follows you.",
            new String[]{
                    "§7+10% bonus XP",
                    "§7+20% bonus XP",
                    "§7+35% bonus XP",
                    "§7+50% bonus XP",
                    "§7+75% bonus XP"
            },
            new int[]{3, 7, 12, 20, 30}),

    REGENERATION("Regeneration", Material.GOLDEN_APPLE,
            "Grants Regeneration to the owner while this pet follows.",
            new String[]{
                    "§7Regeneration I",
                    "§7Regeneration I (enhanced)",
                    "§7Regeneration II",
                    "§7Regeneration II (enhanced)",
                    "§7Regeneration III"
            },
            new int[]{3, 7, 12, 20, 30}),

    SPEED("Speed Boost", Material.SUGAR,
            "Grants Speed to the owner while this pet follows.",
            new String[]{
                    "§7Speed I",
                    "§7Speed I (enhanced)",
                    "§7Speed II",
                    "§7Speed II (enhanced)",
                    "§7Speed III"
            },
            new int[]{3, 7, 12, 20, 30}),

    STRENGTH("Strength", Material.BLAZE_POWDER,
            "Grants Strength to the owner while this pet follows.",
            new String[]{
                    "§7Strength I",
                    "§7Strength I (enhanced)",
                    "§7Strength II",
                    "§7Strength II (enhanced)",
                    "§7Strength III"
            },
            new int[]{3, 7, 12, 20, 30}),

    RESISTANCE("Resistance", Material.IRON_INGOT,
            "Grants Resistance to the owner while this pet follows.",
            new String[]{
                    "§7Resistance I",
                    "§7Resistance I (enhanced)",
                    "§7Resistance II",
                    "§7Resistance II (enhanced)",
                    "§7Resistance III"
            },
            new int[]{3, 7, 12, 20, 30});

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String displayName;
    private final Material icon;
    private final String description;
    private final String[] levelDescriptions;
    /** costs[i] = XP levels needed to upgrade from level i → i+1. */
    private final int[] costs;

    PetUpgrade(String displayName, Material icon, String description,
               String[] levelDescriptions, int[] costs) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.levelDescriptions = levelDescriptions;
        this.costs = costs;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getDisplayName()  { return displayName; }
    public Material getIcon()       { return icon; }
    public String getDescription()  { return description; }
    public int getMaxLevel()        { return 5; }

    /**
     * XP levels required to upgrade from {@code currentLevel} to {@code currentLevel + 1}.
     * Returns -1 if already at max level.
     */
    public int getUpgradeCost(int currentLevel) {
        if (currentLevel >= getMaxLevel()) return -1;
        return costs[currentLevel];
    }

    /** Human-readable effect description for the given upgrade level (1-5). */
    public String getLevelDescription(int level) {
        if (level <= 0) return "§cNot purchased";
        if (level > levelDescriptions.length) return "§6MAX";
        return levelDescriptions[level - 1];
    }

    /**
     * Returns the Bukkit PotionEffectType for buff upgrades.
     * Returns {@code null} for {@link #XP_BOOST}.
     */
    public PotionEffectType getPotionType() {
        return switch (this) {
            case REGENERATION -> PotionEffectType.REGENERATION;
            case SPEED        -> PotionEffectType.SPEED;
            case STRENGTH     -> PotionEffectType.STRENGTH;
            case RESISTANCE   -> PotionEffectType.RESISTANCE;
            default           -> null;
        };
    }

    /**
     * Potion amplifier (0 = Level I, 1 = Level II, 2 = Level III)
     * for the given upgrade level.
     */
    public int getPotionAmplifier(int upgradeLevel) {
        if (upgradeLevel <= 2) return 0;
        if (upgradeLevel <= 4) return 1;
        return 2;
    }

    /** XP multiplier bonus applied to mob drops for the {@link #XP_BOOST} upgrade. */
    public double getXpBoostMultiplier(int upgradeLevel) {
        return switch (upgradeLevel) {
            case 1 -> 0.10;
            case 2 -> 0.20;
            case 3 -> 0.35;
            case 4 -> 0.50;
            case 5 -> 0.75;
            default -> 0.0;
        };
    }
}
