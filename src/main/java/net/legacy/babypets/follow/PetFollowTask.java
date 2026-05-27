package net.legacy.babypets.follow;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.model.PetUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetFollowTask {

    private final BabyPetsPlugin plugin;
    private BukkitTask task;

    public PetFollowTask(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long updateTicks = plugin.getConfig().getLong("follow.update-ticks", 10L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PetManager petManager = plugin.getPetManager();

            // ── Movement / follow logic ───────────────────────────────────────

            for (PetData data : petManager.getAllPets()) {
                Player owner = Bukkit.getPlayer(data.getOwnerUuid());
                if (owner == null || !owner.isOnline()) continue;

                Entity entity = petManager.getLiveEntity(data);
                if (!(entity instanceof LivingEntity living)) continue;
                if (living.isDead()) continue;

                if (living instanceof Ageable ageable) {
                    ageable.setBaby();
                    ageable.setAgeLock(true);
                }

                living.setInvulnerable(true);
                living.setRemoveWhenFarAway(false);
                living.setCanPickupItems(false);

                if (data.isSitting()) {
                    living.setAI(false);

                    Location sitLoc = data.getLastLocation();
                    if (sitLoc != null && sitLoc.getWorld() != null) {
                        if (!living.getWorld().equals(sitLoc.getWorld())
                                || living.getLocation().distance(sitLoc) > 1.5) {
                            living.teleport(sitLoc);
                        }
                    }

                    continue;
                }

                living.setAI(true);

                Location ownerLoc = owner.getLocation();
                Location petLoc   = living.getLocation();

                double teleportDistance = plugin.getConfig().getDouble("follow.teleport-distance", 30.0);
                double followDistance   = plugin.getConfig().getDouble("follow.wander-distance", 3.0);

                if (!ownerLoc.getWorld().equals(petLoc.getWorld())) {
                    Location tp = ownerLoc.clone().add(ownerLoc.getDirection().normalize().multiply(-1.5));
                    tp.setY(ownerLoc.getY());
                    living.teleport(tp);
                    petManager.setLastLocation(data, tp);
                    continue;
                }

                double distance = ownerLoc.distance(petLoc);

                if (distance >= teleportDistance) {
                    Location tp = ownerLoc.clone().add(ownerLoc.getDirection().normalize().multiply(-1.5));
                    tp.setY(ownerLoc.getY());
                    living.teleport(tp);
                    petManager.setLastLocation(data, tp);
                    continue;
                }

                if (living instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.setAware(true);

                    if (isFleeingType(data.getType())) {
                        // Always push a follow path every tick so the flee goal
                        // can never win — our path overwrites it each tick.
                        if (distance > 1.5) {
                            mob.getPathfinder().moveTo(owner, getFollowSpeed(data.getType()));
                        }
                    } else {
                        if (distance > followDistance) {
                            mob.getPathfinder().moveTo(owner, getFollowSpeed(data.getType()));
                        } else {
                            mob.getPathfinder().stopPathfinding();
                        }
                    }
                }

                petManager.setLastLocation(data, living.getLocation());
            }

            // ── Upgrade buff application (collect max per type, then apply) ───

            // Map: ownerUUID → (PotionEffectType → max amplifier)
            Map<UUID, Map<PotionEffectType, Integer>> buffMap = new HashMap<>();

            for (PetData data : petManager.getAllPets()) {
                if (data.isSitting()) continue;

                Player owner = Bukkit.getPlayer(data.getOwnerUuid());
                if (owner == null || !owner.isOnline()) continue;

                Entity entity = petManager.getLiveEntity(data);
                if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
                if (!living.getWorld().equals(owner.getWorld())) continue;

                Map<PotionEffectType, Integer> playerBuffs =
                        buffMap.computeIfAbsent(data.getOwnerUuid(), k -> new HashMap<>());

                for (PetUpgrade upgrade : PetUpgrade.values()) {
                    if (upgrade == PetUpgrade.XP_BOOST) continue; // handled on kill
                    int upgradeLevel = data.getUpgradeLevel(upgrade);
                    if (upgradeLevel <= 0) continue;
                    PotionEffectType potionType = upgrade.getPotionType();
                    if (potionType == null) continue;
                    int amp = upgrade.getPotionAmplifier(upgradeLevel);
                    playerBuffs.merge(potionType, amp, Math::max);
                }
            }

            // Apply the highest-level buff of each type to each owner
            for (Map.Entry<UUID, Map<PotionEffectType, Integer>> entry : buffMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;
                for (Map.Entry<PotionEffectType, Integer> buff : entry.getValue().entrySet()) {
                    // Duration 40 ticks (2s), refreshed every updateTicks — never expires while pet follows
                    p.addPotionEffect(new PotionEffect(buff.getKey(), 40, buff.getValue(), false, false, true));
                }
            }

        }, 20L, updateTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Returns true for mobs that naturally flee from players.
     * These get their follow path pushed every tick so flee AI never wins.
     */
    private boolean isFleeingType(EntityType type) {
        return switch (type) {
            case FOX, OCELOT, CAT, RABBIT -> true;
            default -> false;
        };
    }

    private double getFollowSpeed(EntityType type) {
        return switch (type) {
            case CHICKEN -> 1.35;
            case RABBIT  -> 1.45;
            case PARROT  -> 1.40;
            case BEE     -> 1.35;
            case AXOLOTL -> 1.30;
            case CAT, OCELOT, FOX, WOLF -> 1.30;
            case PIG, SHEEP, COW, MOOSHROOM, GOAT -> 1.20;
            case TURTLE, FROG -> 1.20;
            case CAMEL, DONKEY, MULE, HORSE, LLAMA, TRADER_LLAMA -> 1.25;
            case PANDA, POLAR_BEAR, HOGLIN, STRIDER, SNIFFER -> 1.20;
            default -> 1.20;
        };
    }
}
