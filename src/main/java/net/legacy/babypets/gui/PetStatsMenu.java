package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetTier;
import net.legacy.babypets.model.PetUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 54-slot GUI showing pet tier, XP progress, and purchasable upgrades.
 *
 * Layout (each row is 9 slots):
 *   Row 0:  border  border  border  border [PET_INFO] border  border  border  border
 *   Row 1:  border  [xp1]   [xp2]   [xp3]  [xp4]    [xp5]   [xp6]   [xp7]  border
 *   Row 2:  border  border [XP_BOOST] border [REGEN] border  [SPEED] border  border
 *   Row 3:  border  border  border [STRENGTH] border [RESIST] border  border  border
 *   Row 4:  border  border [SATCHEL] border [WAYPTS] border  border  border  border
 *   Row 5:  border  border  border  border  [CLOSE]  border  border  border  border
 */
public class PetStatsMenu implements InventoryHolder {

    // ── Slot constants ────────────────────────────────────────────────────────

    public static final int   SLOT_PET_INFO  = 4;
    public static final int[] XP_BAR_SLOTS   = {10, 11, 12, 13, 14, 15, 16};
    public static final int   SLOT_SATCHEL   = 38;
    public static final int   SLOT_WAYPOINTS = 40;
    public static final int   SLOT_CLOSE     = 49;

    /** Ordered mapping of inventory slot → PetUpgrade. */
    public static final Map<Integer, PetUpgrade> SLOT_TO_UPGRADE = new LinkedHashMap<>();
    static {
        SLOT_TO_UPGRADE.put(20, PetUpgrade.XP_BOOST);
        SLOT_TO_UPGRADE.put(22, PetUpgrade.REGENERATION);
        SLOT_TO_UPGRADE.put(24, PetUpgrade.SPEED);
        SLOT_TO_UPGRADE.put(30, PetUpgrade.STRENGTH);
        SLOT_TO_UPGRADE.put(32, PetUpgrade.RESISTANCE);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final BabyPetsPlugin plugin;
    private final PetData        petData;
    private final Inventory      inventory;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PetStatsMenu(BabyPetsPlugin plugin, PetData petData) {
        this.plugin  = plugin;
        this.petData = petData;
        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        this.inventory = Bukkit.createInventory(this, 54, "§5✦ Pet: §f" + cleanName);
        populate();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public PetData getPetData()  { return petData; }
    public void open(Player p)   { p.openInventory(inventory); }
    public void refresh()        { populate(); }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Returns the PetUpgrade mapped to the given slot, or null if none. */
    public static PetUpgrade getUpgradeForSlot(int slot) {
        return SLOT_TO_UPGRADE.get(slot);
    }

    /**
     * Minimum PetTier required to purchase upgrade level {@code upgradeLevel}.
     * 1→BABY, 2→SCOUT, 3→PACK_MULE, 4→COMPANION, 5→ELDER.
     */
    public static PetTier getMinTierForUpgrade(int upgradeLevel) {
        return switch (upgradeLevel) {
            case 2  -> PetTier.SCOUT;
            case 3  -> PetTier.PACK_MULE;
            case 4  -> PetTier.COMPANION;
            case 5  -> PetTier.ELDER;
            default -> PetTier.BABY;
        };
    }

    // ── GUI population ────────────────────────────────────────────────────────

    private void populate() {
        ItemStack border = makeBorder();
        for (int i = 0; i < 54; i++) inventory.setItem(i, border);

        inventory.setItem(SLOT_PET_INFO, buildPetInfoItem());
        buildXpBar();

        for (Map.Entry<Integer, PetUpgrade> entry : SLOT_TO_UPGRADE.entrySet()) {
            inventory.setItem(entry.getKey(), buildUpgradeItem(entry.getValue()));
        }

        inventory.setItem(SLOT_SATCHEL,   buildSatchelButton());
        inventory.setItem(SLOT_WAYPOINTS, buildWaypointsButton());
        inventory.setItem(SLOT_CLOSE,     buildCloseItem());
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) { meta.setDisplayName("§0"); pane.setItemMeta(meta); }
        return pane;
    }

    private ItemStack buildPetInfoItem() {
        ItemStack item = new ItemStack(getSpawnEgg(petData.getType()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PetTier tier = petData.getTier();
        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        meta.setDisplayName("§d§l" + cleanName);

        List<String> lore = new ArrayList<>();
        lore.add("§8───────────────────────");
        lore.add("§7Type:   §d" + prettify(petData.getType().name()));
        lore.add("§7Biome:  §b" + petData.getBiome());
        lore.add("§7Tier:   " + tier.getColoredName() + " §8(Tier " + tier.getTierNumber() + ")");
        lore.add("§7Status: " + (petData.isSitting() ? "§eSitting" : "§aFollowing"));
        lore.add("§8───────────────────────");
        lore.add("§7Total XP: §f" + petData.getTotalXp());
        if (!tier.isMaxTier()) {
            lore.add("§7Next tier in: §f" + tier.getXpToNextTier(petData.getTotalXp()) + " XP");
        } else {
            lore.add("§6✦ MAX TIER REACHED!");
        }
        lore.add("");
        lore.add("§7Kill mobs to earn pet XP.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void buildXpBar() {
        PetTier tier    = petData.getTier();
        int totalXp     = petData.getTotalXp();
        int xpWithin    = tier.getXpWithinTier(totalXp);
        int span        = tier.isMaxTier() ? 1 : tier.getTierSpan();
        boolean maxTier = tier.isMaxTier();

        int filled = maxTier
                ? XP_BAR_SLOTS.length
                : (span > 0 ? (int) Math.min(XP_BAR_SLOTS.length, (double) xpWithin / span * XP_BAR_SLOTS.length) : 0);

        for (int i = 0; i < XP_BAR_SLOTS.length; i++) {
            Material mat;
            String   name;

            if (maxTier) {
                mat  = Material.YELLOW_STAINED_GLASS_PANE;
                name = "§6§lMAX TIER ✦  " + tier.getColoredName();
            } else if (i < filled) {
                mat  = Material.LIME_STAINED_GLASS_PANE;
                name = "§a" + xpWithin + " §7/ §a" + span + " XP  »  " + tier.next().getColoredName();
            } else {
                mat  = Material.GRAY_STAINED_GLASS_PANE;
                name = "§7" + xpWithin + " §8/ §7" + span + " XP  »  " + tier.next().getColoredName();
            }

            ItemStack pane = new ItemStack(mat);
            ItemMeta  meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                pane.setItemMeta(meta);
            }
            inventory.setItem(XP_BAR_SLOTS[i], pane);
        }
    }

    private ItemStack buildUpgradeItem(PetUpgrade upgrade) {
        int     currentLevel = petData.getUpgradeLevel(upgrade);
        PetTier petTier      = petData.getTier();

        ItemStack item = new ItemStack(upgrade.getIcon());
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String levelTag = currentLevel >= upgrade.getMaxLevel()
                ? "§6MAX"
                : "§e" + currentLevel + "§7/§e5";
        meta.setDisplayName("§e§l" + upgrade.getDisplayName() + " §8[" + levelTag + "§8]");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + upgrade.getDescription());
        lore.add("§8───────────────────────");
        lore.add(currentLevel > 0
                ? "§a▸ Active:  §f" + upgrade.getLevelDescription(currentLevel)
                : "§c▸ Not purchased yet");
        lore.add("");

        if (currentLevel >= upgrade.getMaxLevel()) {
            lore.add("§6✦ MAXED OUT!");
        } else {
            int nextLevel    = currentLevel + 1;
            PetTier required = getMinTierForUpgrade(nextLevel);
            int cost         = upgrade.getUpgradeCost(currentLevel);

            lore.add("§b▸ Next:    §f" + upgrade.getLevelDescription(nextLevel));
            lore.add("§7▸ Cost:    §e" + cost + " §7XP levels");

            if (petTier.getTierNumber() >= required.getTierNumber()) {
                lore.add("§a▸ Click to upgrade!");
            } else {
                lore.add("§c▸ Requires " + required.getColoredName() + " §ctier");
                lore.add("§c   (your pet is " + petTier.getColoredName() + "§c)");
            }
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSatchelButton() {
        boolean unlocked = petData.getTier().getTierNumber() >= PetTier.PACK_MULE.getTierNumber();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        if (unlocked) {
            meta.setDisplayName("§6§l⬡ Open Satchel");
            meta.setLore(List.of(
                    "§7Your pet carries a 3-slot satchel.",
                    "§7Click to open it!",
                    "",
                    "§6Pack Mule §8— Tier 3 perk"
            ));
        } else {
            meta.setDisplayName("§8⬡ Satchel §8(Locked)");
            meta.setLore(List.of(
                    "§7Reach " + PetTier.PACK_MULE.getColoredName() + " §7tier",
                    "§7to unlock a 3-slot satchel.",
                    "",
                    "§8Requires Tier 3 — " + PetTier.PACK_MULE.getColoredName()
            ));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildWaypointsButton() {
        boolean unlocked = petData.getTier() == PetTier.ELDER;
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        if (unlocked) {
            meta.setDisplayName("§e§l✦ Waypoints");
            meta.setLore(List.of(
                    "§7Your pet remembers up to 3 locations.",
                    "§7Click to manage waypoints!",
                    "",
                    "§eElder §8— Tier 5 perk"
            ));
        } else {
            meta.setDisplayName("§8✦ Waypoints §8(Locked)");
            meta.setLore(List.of(
                    "§7Reach " + PetTier.ELDER.getColoredName() + " §7tier",
                    "§7to unlock waypoint navigation.",
                    "",
                    "§8Requires Tier 5 — " + PetTier.ELDER.getColoredName()
            ));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§c§lClose"); item.setItemMeta(meta); }
        return item;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private Material getSpawnEgg(EntityType type) {
        try { return Material.valueOf(type.name() + "_SPAWN_EGG"); }
        catch (IllegalArgumentException e) { return Material.BONE; }
    }

    private String prettify(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase().replace('_', ' ');
    }
}
