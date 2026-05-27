package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.model.PetTypeRegistry;
import net.legacy.babypets.model.PetUpgrade;
import net.legacy.babypets.util.MessageUtil;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof PetCreateMenu) {
            handlePetSelect(event);
        } else if (holder instanceof BiomeSelectMenu) {
            handleBiomeSelect(event);
        } else if (holder instanceof PetStatsMenu statsMenu) {
            handlePetStatsClick(event, statsMenu);
        }
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

        // Close button
        if (slot == PetStatsMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        // Upgrade click
        PetUpgrade upgrade = PetStatsMenu.getUpgradeForSlot(slot);
        if (upgrade == null) return;

        PetData pet = statsMenu.getPetData();
        int currentLevel = pet.getUpgradeLevel(upgrade);

        // Already maxed?
        if (currentLevel >= upgrade.getMaxLevel()) {
            player.sendMessage("§c✦ This upgrade is already maxed out!");
            return;
        }

        int nextLevel  = currentLevel + 1;
        int minPetLvl  = PetStatsMenu.getMinPetLevelForUpgrade(nextLevel);

        // Pet level gate
        if (pet.getLevel() < minPetLvl) {
            player.sendMessage("§c✦ Your pet needs to be §flevel " + minPetLvl
                    + " §cto unlock this tier. (Current pet level: §f" + pet.getLevel() + "§c)");
            return;
        }

        int cost = upgrade.getUpgradeCost(currentLevel);

        // Player XP level check
        if (player.getLevel() < cost) {
            player.sendMessage("§c✦ Not enough XP levels! Need §f" + cost
                    + " §cbut you only have §f" + player.getLevel() + "§c.");
            return;
        }

        // Deduct XP levels and apply upgrade
        player.setLevel(player.getLevel() - cost);
        pet.setUpgradeLevel(upgrade, nextLevel);
        plugin.getPetManager().savePet(pet);

        player.sendMessage("§a✦ §f" + upgrade.getDisplayName()
                + " §aupgraded to §flevel " + nextLevel + "§a!");

        // Refresh the GUI next tick so the current click finishes cleanly
        plugin.getServer().getScheduler().runTask(plugin, statsMenu::refresh);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private int getIndexFromSlots(int slot, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return i;
        }
        return -1;
    }
}
