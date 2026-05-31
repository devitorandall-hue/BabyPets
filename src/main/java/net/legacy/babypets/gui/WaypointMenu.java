package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier-5 Elder perk — GUI to manage up to 3 saved waypoints.
 *
 * Layout (27 slots, 3 rows):
 *   Row 0:  border  border  border  border [PET_ICON] border  border  border  border
 *   Row 1:  border  [WP1]  border  [WP2]  border  [WP3]  border  border  border
 *   Row 2:  border  border  border  border [CLOSE] border  border  border  border
 */
public class WaypointMenu implements InventoryHolder {

    public static final int[] WAYPOINT_SLOTS = {10, 12, 14};
    public static final int   SLOT_CLOSE     = 22;

    private final BabyPetsPlugin plugin;
    private final PetData        petData;
    private final Inventory      inventory;

    public WaypointMenu(BabyPetsPlugin plugin, PetData petData) {
        this.plugin  = plugin;
        this.petData = petData;

        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        this.inventory = Bukkit.createInventory(this, 27, "§e✦ §f" + cleanName + "§e's Waypoints");

        populate();
    }

    public PetData getPetData() { return petData; }
    public void open(Player p)  { p.openInventory(inventory); }
    public void refresh()       { populate(); }

    @Override
    public Inventory getInventory() { return inventory; }

    /** Returns the waypoint index (0/1/2) for a slot, or -1 if not a waypoint slot. */
    public static int getWaypointIndex(int slot) {
        for (int i = 0; i < WAYPOINT_SLOTS.length; i++) {
            if (WAYPOINT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void populate() {
        ItemStack border = makeBorder();
        for (int i = 0; i < 27; i++) inventory.setItem(i, border);

        inventory.setItem(4, buildPetIcon());

        String[] waypoints = petData.getWaypoints();
        for (int i = 0; i < WAYPOINT_SLOTS.length; i++) {
            inventory.setItem(WAYPOINT_SLOTS[i], buildWaypointItem(i, waypoints[i]));
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) { cm.setDisplayName("§c§lClose"); close.setItemMeta(cm); }
        inventory.setItem(SLOT_CLOSE, close);
    }

    private ItemStack buildWaypointItem(int index, String waypointStr) {
        if (waypointStr == null || waypointStr.isEmpty()) {
            ItemStack item = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7Waypoint " + (index + 1) + " §8— §7Empty");
                meta.setLore(List.of(
                        "§8───────────────────────",
                        "§aLeft-click §7to save your current location."
                ));
                item.setItemMeta(meta);
            }
            return item;
        }

        // Filled waypoint — parse "world,x,y,z"
        String[] parts = waypointStr.split(",", 4);
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lWaypoint " + (index + 1));
            List<String> lore = new ArrayList<>();
            lore.add("§8───────────────────────");
            if (parts.length == 4) {
                lore.add("§7World: §f" + parts[0]);
                lore.add("§7X: §f" + fmt(parts[1]) + "  §7Y: §f" + fmt(parts[2]) + "  §7Z: §f" + fmt(parts[3]));
            }
            lore.add("");
            lore.add("§aLeft-click §7to point compass here.");
            lore.add("§cRight-click §7to clear this waypoint.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String fmt(String raw) {
        try { return String.valueOf((int) Double.parseDouble(raw.trim())); }
        catch (NumberFormatException e) { return raw.trim(); }
    }

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        if (m != null) { m.setDisplayName("§0"); pane.setItemMeta(m); }
        return pane;
    }

    private ItemStack buildPetIcon() {
        Material mat;
        try { mat = Material.valueOf(petData.getType().name() + "_SPAWN_EGG"); }
        catch (IllegalArgumentException e) { mat = Material.COMPASS; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getPetManager().stripLegacyCodes(petData.getName());
        meta.setDisplayName("§e§l" + name + "'s Waypoints");
        meta.setLore(List.of(
                "§7Save up to 3 locations your pet",
                "§7remembers. Left-click a saved",
                "§7waypoint to point your compass.",
                "",
                "§e§lElder §6— Tier 5 perk"
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Static utilities ──────────────────────────────────────────────────────

    /** Encodes a Location to the storage format "world,x,y,z". */
    public static String encodeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    /** Decodes a "world,x,y,z" string to a Location, or null if invalid/unloaded. */
    public static Location decodeLocation(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] parts = encoded.split(",", 4);
        if (parts.length < 4) return null;
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Points the player's held compass (if any) to the target location.
     * Uses {@code setLodestoneTracked(false)} so no physical lodestone block is needed.
     */
    public static void pointCompass(Player player, Location target) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.COMPASS) return;
        ItemMeta meta = held.getItemMeta();
        if (!(meta instanceof CompassMeta cm)) return;
        cm.setLodestone(target);
        cm.setLodestoneTracked(false);
        held.setItemMeta(cm);
        player.getInventory().setItemInMainHand(held);
    }
}
