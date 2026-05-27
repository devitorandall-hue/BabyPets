package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
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
 * 54-slot GUI showing pet statistics and purchasable upgrades.
 *
 * Layout (each row is 9 slots):
 *   Row 0:  border  border  border  border [PET_INFO] border  border  border  border
 *   Row 1:  border  [xp1]   [xp2]   [xp3]  [xp4]    [xp5]   [xp6]   [xp7]  border
 *   Row 2:  border  border [XP_BOOST] border [REGEN] border  [SPEED] border  border
 *   Row 3:  border  border  border [STRENGTH] border [RESIST] border  border  border
 *   Row 4:  border  border  border  border  border   border  border  border  border
 *   Row 5:  border  border  border  border  [CLOSE]  border  border  border  border
 */
public class PetStatsMenu implements InventoryHolder {

    // ── Slot constants ────────────────────────────────────────────────────────

    public static final int SLOT_PET_INFO = 4;
    public static final int[] XP_BAR_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int SLOT_CLOSE = 49;

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
    private final PetData petData;
    private final Inventory inventory;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PetStatsMenu(BabyPetsPlugin plugin, PetData petData) {
        this.plugin  = plugin;
        this.petData = petData;
        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        this.inventory = Bukkit.createInventory(this, 54, "§5✦ Pet: §f" + cleanName);
        populate();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public PetData getPetData() { return petData; }

    public void open(Player player) { player.openInventory(inventory); }

    /** Refreshes all items in the inventory to reflect the latest PetData state. */
    public void refresh() { populate(); }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Returns the PetUpgrade mapped to the given slot, or null if none. */
    public static PetUpgrade getUpgradeForSlot(int slot) {
        return SLOT_TO_UPGRADE.get(slot);
    }

    /** Minimum pet level required to purchase upgrade level {@code upgradeLevel}. */
    public static int getMinPetLevelForUpgrade(int upgradeLevel) {
        return switch (upgradeLevel) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 20;
            default -> 1;
        };
    }

    // ── GUI population ────────────────────────────────────────────────────────

    private void populate() {
        // Fill everything with border panes first
        ItemStack border = makeBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        // Pet info card
        inventory.setItem(SLOT_PET_INFO, buildPetInfoItem());

        // XP progress bar
        buildXpBar();

        // Upgrade items
        for (Map.Entry<Integer, PetUpgrade> entry : SLOT_TO_UPGRADE.entrySet()) {
            inventory.setItem(entry.getKey(), buildUpgradeItem(entry.getValue()));
        }

        // Close button
        inventory.setItem(SLOT_CLOSE, buildCloseItem());
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§0");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack buildPetInfoItem() {
        ItemStack item = new ItemStack(getSpawnEgg(petData.getType()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        meta.setDisplayName("§d§l" + cleanName);

        List<String> lore = new ArrayList<>();
        lore.add("§8───────────────────────");
        lore.add("§7Type:    §d" + prettify(petData.getType().name()));
        lore.add("§7Biome:   §b" + petData.getBiome());
        lore.add("§7Level:   §e" + petData.getLevel() + " §8/ §e30");
        lore.add("§7Status:  " + (petData.isSitting() ? "§eSitting" : "§aFollowing"));
        lore.add("§8───────────────────────");
        lore.add("§7XP:      §f" + petData.getXp() + " §8/ §f" + xpNeededDisplay(petData.getLevel()));
        lore.add("");
        lore.add("§7Kill mobs to earn pet XP.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void buildXpBar() {
        int level  = petData.getLevel();
        int xp     = petData.getXp();
        int needed = PetData.xpToNextLevel(level);
        boolean maxLevel = (level >= 30);

        int filled;
        if (maxLevel) {
            filled = XP_BAR_SLOTS.length;
        } else {
            filled = needed > 0 ? (int) Math.min(XP_BAR_SLOTS.length, (double) xp / needed * XP_BAR_SLOTS.length) : 0;
        }

        for (int i = 0; i < XP_BAR_SLOTS.length; i++) {
            Material mat;
            String name;

            if (maxLevel) {
                mat  = Material.YELLOW_STAINED_GLASS_PANE;
                name = "§6§lMAX LEVEL ✦";
            } else if (i < filled) {
                mat  = Material.LIME_STAINED_GLASS_PANE;
                name = "§a" + xp + " §7/ §a" + needed + " §7XP";
            } else {
                mat  = Material.GRAY_STAINED_GLASS_PANE;
                name = "§7" + xp + " §8/ §7" + needed + " §7XP";
            }

            ItemStack pane = new ItemStack(mat);
            ItemMeta meta  = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                pane.setItemMeta(meta);
            }
            inventory.setItem(XP_BAR_SLOTS[i], pane);
        }
    }

    private ItemStack buildUpgradeItem(PetUpgrade upgrade) {
        int currentLevel = petData.getUpgradeLevel(upgrade);
        int petLevel     = petData.getLevel();

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

        if (currentLevel > 0) {
            lore.add("§a▸ Active:  §f" + upgrade.getLevelDescription(currentLevel));
        } else {
            lore.add("§c▸ Not purchased yet");
        }

        lore.add("");

        if (currentLevel >= upgrade.getMaxLevel()) {
            lore.add("§6✦ MAXED OUT!");
        } else {
            int nextLevel  = currentLevel + 1;
            int minPetLvl  = getMinPetLevelForUpgrade(nextLevel);
            int cost       = upgrade.getUpgradeCost(currentLevel);

            lore.add("§b▸ Next:    §f" + upgrade.getLevelDescription(nextLevel));
            lore.add("§7▸ Cost:    §e" + cost + " §7XP levels");

            if (petLevel >= minPetLvl) {
                lore.add("§a▸ Click to upgrade!");
            } else {
                lore.add("§c▸ Requires pet level §f" + minPetLvl);
                lore.add("§c   (your pet is level §f" + petLevel + "§c)");
            }
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lClose");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private String xpNeededDisplay(int level) {
        if (level >= 30) return "§6MAX";
        return String.valueOf(PetData.xpToNextLevel(level));
    }

    private Material getSpawnEgg(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.BONE;
        }
    }

    private String prettify(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0))
                + name.substring(1).toLowerCase().replace('_', ' ');
    }
}
