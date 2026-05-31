package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Tier-3 Pack Mule perk — a small 3-slot satchel the pet carries.
 *
 * Layout (27 slots, 3 rows):
 *   Row 0:  border  border  border  border [PET_ICON] border  border  border  border
 *   Row 1:  border  border  border  [INV1] [INV2]  [INV3]  border  border  border
 *   Row 2:  border  border  border  border [CLOSE] border  border  border  border
 */
public class PetInventoryMenu implements InventoryHolder {

    /** The three slots that players can freely place items into. */
    public static final int[] INVENTORY_SLOTS = {12, 13, 14};
    public static final int   SLOT_CLOSE      = 22;

    private final BabyPetsPlugin plugin;
    private final PetData        petData;
    private final Inventory      inventory;

    public PetInventoryMenu(BabyPetsPlugin plugin, PetData petData) {
        this.plugin  = plugin;
        this.petData = petData;

        String cleanName = plugin.getPetManager().stripLegacyCodes(petData.getName());
        this.inventory  = Bukkit.createInventory(this, 27, "§6✦ §f" + cleanName + "§6's Satchel");

        populate();
    }

    public PetData getPetData()    { return petData; }
    public void open(Player p)     { p.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if the given slot is one of the three open item slots. */
    public static boolean isInventorySlot(int slot) {
        for (int s : INVENTORY_SLOTS) if (s == slot) return true;
        return false;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void populate() {
        ItemStack border = makeBorder();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, border);
        }

        // Pet icon
        inventory.setItem(4, buildPetIcon());

        // Load existing items from PetData into the open slots
        ItemStack[] stored = petData.getPetInventory();
        for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
            inventory.setItem(INVENTORY_SLOTS[i], stored[i]);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) { cm.setDisplayName("§c§lClose"); close.setItemMeta(cm); }
        inventory.setItem(SLOT_CLOSE, close);
    }

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta  m    = pane.getItemMeta();
        if (m != null) { m.setDisplayName("§0"); pane.setItemMeta(m); }
        return pane;
    }

    private ItemStack buildPetIcon() {
        Material mat = getSpawnEgg(petData.getType());
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getPetManager().stripLegacyCodes(petData.getName());
        meta.setDisplayName("§6§l" + name + "'s Satchel");
        meta.setLore(List.of(
                "§7Place items in the three slots below.",
                "§7They are saved when you close this menu.",
                "",
                "§c§lPack Mule §6— Tier 3 perk"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Material getSpawnEgg(EntityType type) {
        try { return Material.valueOf(type.name() + "_SPAWN_EGG"); }
        catch (IllegalArgumentException e) { return Material.CHEST; }
    }
}
