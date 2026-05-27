package net.legacy.babypets.listener;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.model.PetUpgrade;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

/**
 * Handles pet XP gain when the owner kills mobs,
 * and applies XP Boost upgrade bonuses.
 */
public class PetInteractionListener implements Listener {

    private final BabyPetsPlugin plugin;

    public PetInteractionListener(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PetManager petManager = plugin.getPetManager();
        List<PetData> pets = petManager.getPets(killer.getUniqueId());
        if (pets.isEmpty()) return;

        int petXpGain = getPetXpForMob(event.getEntity().getType());
        double maxXpBoostMultiplier = 0.0;

        for (PetData pet : pets) {
            // All of the player's pets earn XP (sitting or following)
            boolean leveledUp = pet.addXp(petXpGain);
            if (leveledUp) {
                String cleanName = petManager.stripLegacyCodes(pet.getName());
                killer.sendMessage("§d✦ §fYour pet §d" + cleanName
                        + " §fleveled up to §dlevel §f" + pet.getLevel() + "§f!");
            }
            petManager.savePet(pet);

            // Track the highest XP Boost level across all following pets
            if (!pet.isSitting()) {
                int xpBoostLevel = pet.getUpgradeLevel(PetUpgrade.XP_BOOST);
                if (xpBoostLevel > 0) {
                    double mult = PetUpgrade.XP_BOOST.getXpBoostMultiplier(xpBoostLevel);
                    if (mult > maxXpBoostMultiplier) {
                        maxXpBoostMultiplier = mult;
                    }
                }
            }
        }

        // Grant the bonus player XP from the highest active XP Boost
        if (maxXpBoostMultiplier > 0) {
            int baseXp   = event.getDroppedExp();
            int bonusXp  = (int) (baseXp * maxXpBoostMultiplier);
            if (bonusXp > 0) {
                killer.giveExp(bonusXp);
            }
        }
    }

    // ── XP values per mob type ────────────────────────────────────────────────

    private int getPetXpForMob(EntityType type) {
        return switch (type) {
            // Boss mobs
            case WITHER, ENDER_DRAGON -> 100;

            // Strong / mini-boss mobs
            case ELDER_GUARDIAN, WITHER_SKELETON, BLAZE, GHAST,
                 RAVAGER, EVOKER -> 30;

            // Standard hostile mobs
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER,
                 WITCH, ENDERMAN, DROWNED, HUSK, STRAY,
                 PILLAGER, VINDICATOR, PHANTOM, SLIME, MAGMA_CUBE,
                 ZOMBIFIED_PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN,
                 GUARDIAN -> 15;

            // Neutral / semi-hostile mobs
            case WOLF, POLAR_BEAR, BEE, ENDERMITE, SILVERFISH -> 10;

            // Passive mobs (default)
            default -> 5;
        };
    }
}
