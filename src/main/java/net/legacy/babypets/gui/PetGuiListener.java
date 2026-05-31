package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.model.PetTier;
import net.legacy.babypets.model.PetTypeRegistry;
import net.legacy.babypets.model.PetUpgrade;
import net.legacy.babypets.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PetGuiListener implements Listener {

    private final BabyPetsPlugin plugin;

    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public PetGuiListener(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Click dispatcher ──────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof PetCreateMenu) {
            handlePetSelect(event);
        } else if (holder instanceof BiomeSelectMenu) {
            handleBiomeSelect(event);
        } else if (holder instanceof PetStatsMenu statsMenu) {
            handlePetStatsClick(event, statsMenu);
        } else if (holder instanceof PetInventoryMenu invMenu) {
            handlePetInventoryClick(event, invMenu);
        } else if (holder instanceof WaypointMenu wpMenu) {
            handleWaypointClick(event, wpMenu);
        }
    }

    // ── Close handler — save satchel contents ─────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetInventoryMenu invMenu)) return;

        PetData petData = invMenu.getPetData();
        ItemStack[] stored = petData.getPetInventory();

        for (int i = 0; i < PetInventoryMenu.INVENTORY_SLOTS.length; i++) {
            stored[i] = event.getInventory().getItem(PetInventoryMenu.INVENTORY_SLOTS[i]);
        }

        plugin.getPetManager().savePet(petData);
    }

    // ── Step 1: player picks a pet type ──────────────────────────────────────

    private void handlePetSelect(InventoryClickEvent event) {
        event.setCancelled(true);

        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        int petIndex = getIndexFromSlots(slot, PET_SLOTS);
        if (petIndex == -1) return;

        List<EntityType> enabled = PetTypeRegistry.getEnabledTypes(
                plugin.getConfig().getStringList("pets.enabled-types")
        );
        if (petIndex >= enabled.size()) return;

        PetManager petManager = plugin.getPetManager();
        petManager.setPendingCreateType(player.getUniqueId(), enabled.get(petIndex));

        player.closeInventory();
        new BiomeSelectMenu().open(player);
    }

    // ── Step 2: player picks a biome ─────────────────────────────────────────

    private void handleBiomeSelect(InventoryClickEvent event) {
        event.setCancelled(true);

        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        int biomeIndex = getIndexFromSlots(slot, BiomeSelectMenu.BIOME_SLOTS);
        if (biomeIndex == -1) return;
        if (biomeIndex >= BiomeSelectMenu.BIOMES.size()) return;

        String biomeName = BiomeSelectMenu.BIOMES.get(biomeIndex).name();

        PetManager petManager = plugin.getPetManager();
        petManager.setPendingBiome(player.getUniqueId(), biomeName);

        player.closeInventory();
        MessageUtil.send(player, "messages.choose-name");
        MessageUtil.send(player, "messages.choose-name-cancel");
    }

    // ── Pet stats GUI ─────────────────────────────────────────────────────────

    private void handlePetStatsClick(InventoryClickEvent event, PetStatsMenu statsMenu) {
        event.setCancelled(true);

        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (slot == PetStatsMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        PetData pet = statsMenu.getPetData();

        // ── Satchel button ────────────────────────────────────────────────────
        if (slot == PetStatsMenu.SLOT_SATCHEL) {
            if (pet.getTier().getTierNumber() < PetTier.PACK_MULE.getTierNumber()) {
                player.sendMessage("§c✦ Your pet needs to reach "
                        + PetTier.PACK_MULE.getColoredName() + " §ctier to use the satchel!");
                return;
            }
            player.closeInventory();
            new PetInventoryMenu(plugin, pet).open(player);
            return;
        }

        // ── Waypoints button ──────────────────────────────────────────────────
        if (slot == PetStatsMenu.SLOT_WAYPOINTS) {
            if (pet.getTier() != PetTier.ELDER) {
                player.sendMessage("§c✦ Your pet needs to reach "
                        + PetTier.ELDER.getColoredName() + " §ctier to use waypoints!");
                return;
            }
            player.closeInventory();
            new WaypointMenu(plugin, pet).open(player);
            return;
        }

        // ── Upgrade buttons ───────────────────────────────────────────────────
        PetUpgrade upgrade = PetStatsMenu.getUpgradeForSlot(slot);
        if (upgrade == null) return;

        int currentLevel = pet.getUpgradeLevel(upgrade);

        if (currentLevel >= upgrade.getMaxLevel()) {
            player.sendMessage("§c✦ This upgrade is already maxed out!");
            return;
        }

        int nextLevel    = currentLevel + 1;
        PetTier required = PetStatsMenu.getMinTierForUpgrade(nextLevel);

        if (pet.getTier().getTierNumber() < required.getTierNumber()) {
            player.sendMessage("§c✦ Your pet must be " + required.getColoredName()
                    + " §ctier to unlock this! §8(Current: " + pet.getTier().getColoredName() + "§8)");
            return;
        }

        int cost = upgrade.getUpgradeCost(currentLevel);
        if (player.getLevel() < cost) {
            player.sendMessage("§c✦ Not enough XP levels! Need §f" + cost
                    + " §cbut you have §f" + player.getLevel() + "§c.");
            return;
        }

        player.setLevel(player.getLevel() - cost);
        pet.setUpgradeLevel(upgrade, nextLevel);
        plugin.getPetManager().savePet(pet);
        player.sendMessage("§a✦ §f" + upgrade.getDisplayName() + " §aupgraded to §flevel " + nextLevel + "§a!");

        plugin.getServer().getScheduler().runTask(plugin, statsMenu::refresh);
    }

    // ── Pet inventory (Pack Mule) GUI ─────────────────────────────────────────

    private void handlePetInventoryClick(InventoryClickEvent event, PetInventoryMenu invMenu) {
        int slot    = event.getRawSlot();
        int guiSize = event.getInventory().getSize(); // 27

        if (slot < 0) return;

        // Clicks within the GUI on non-open slots: cancel
        if (slot < guiSize && !PetInventoryMenu.isInventorySlot(slot)) {
            event.setCancelled(true);
            if (slot == PetInventoryMenu.SLOT_CLOSE && event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        }
        // Clicks on open slots (12/13/14) or player inventory pass through normally
    }

    // ── Waypoint GUI ──────────────────────────────────────────────────────────

    private void handleWaypointClick(InventoryClickEvent event, WaypointMenu wpMenu) {
        event.setCancelled(true);

        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (slot == WaypointMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        int wpIndex = WaypointMenu.getWaypointIndex(slot);
        if (wpIndex < 0) return;

        PetData  petData   = wpMenu.getPetData();
        String[] waypoints = petData.getWaypoints();

        if (event.isRightClick()) {
            // Right-click: clear waypoint
            if (waypoints[wpIndex] != null && !waypoints[wpIndex].isEmpty()) {
                waypoints[wpIndex] = null;
                plugin.getPetManager().savePet(petData);
                player.sendMessage("§c✦ Waypoint " + (wpIndex + 1) + " cleared.");
                plugin.getServer().getScheduler().runTask(plugin, wpMenu::refresh);
            }
        } else {
            // Left-click
            if (waypoints[wpIndex] == null || waypoints[wpIndex].isEmpty()) {
                // Save current location
                Location loc = player.getLocation();
                waypoints[wpIndex] = WaypointMenu.encodeLocation(loc);
                plugin.getPetManager().savePet(petData);
                player.sendMessage("§a✦ Waypoint " + (wpIndex + 1) + " saved at §f"
                        + loc.getWorld().getName() + " §7("
                        + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ() + ")§a!");
                plugin.getServer().getScheduler().runTask(plugin, wpMenu::refresh);
            } else {
                // Point compass (or display coords)
                Location target = WaypointMenu.decodeLocation(waypoints[wpIndex]);
                if (target == null) {
                    player.sendMessage("§c✦ Could not load waypoint — world may not be loaded.");
                    return;
                }

                boolean holdingCompass = player.getInventory().getItemInMainHand().getType() == Material.COMPASS;
                if (holdingCompass) {
                    WaypointMenu.pointCompass(player, target);
                    player.sendMessage("§e✦ Compass now pointing to Waypoint " + (wpIndex + 1) + "!");
                } else {
                    String[] parts = waypoints[wpIndex].split(",", 4);
                    String coords = parts.length == 4
                            ? parts[0] + " (" + (int) Double.parseDouble(parts[1]) + ", "
                              + (int) Double.parseDouble(parts[2]) + ", "
                              + (int) Double.parseDouble(parts[3]) + ")"
                            : waypoints[wpIndex];
                    player.sendMessage("§e✦ Waypoint " + (wpIndex + 1) + ": §f" + coords);
                    player.sendMessage("§7Hold a compass in your main hand to point it here.");
                }
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private int getIndexFromSlots(int slot, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return i;
        }
        return -1;
    }
}
