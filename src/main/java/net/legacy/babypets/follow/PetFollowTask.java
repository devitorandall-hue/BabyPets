package net.legacy.babypets.follow;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.model.PetTier;
import net.legacy.babypets.model.PetUpgrade;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class PetFollowTask {

    private final BabyPetsPlugin plugin;
    private BukkitTask task;

    // ── Per-pet state ─────────────────────────────────────────────────────────
    /** ms timestamp of last mob-alarm per pet UUID (Scout radar cooldown). */
    private final Map<UUID, Long>     lastMobAlarm    = new HashMap<>();

    // ── Per-owner state ───────────────────────────────────────────────────────
    /** Last known position used to detect whether the owner is still. */
    private final Map<UUID, Location> ownerLastLoc    = new HashMap<>();
    /** How many consecutive timer ticks the owner has been standing still. */
    private final Map<UUID, Integer>  ownerStillTicks = new HashMap<>();
    /** ms timestamp of last HP heal per owner (Companion regen aura). */
    private final Map<UUID, Long>     ownerLastRegen  = new HashMap<>();

    private final Random random = new Random();

    public PetFollowTask(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long updateTicks = plugin.getConfig().getLong("follow.update-ticks", 10L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PetManager petManager = plugin.getPetManager();

            // Owners whose pets qualify for the Companion regen aura this tick
            Set<UUID> companionOwners = new HashSet<>();

            // ── Movement / follow / tier-perk logic ──────────────────────────

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

                // ── Sitting pets: lock in place ───────────────────────────────
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

                // ── Following pets: movement ──────────────────────────────────
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

                // ── Tier perks (following pets only) ─────────────────────────
                PetTier tier = data.getTier();

                // Tier 1 — Baby: random HEART particle + soft ambient sound
                if (tier == PetTier.BABY && random.nextInt(40) == 0) {
                    spawnParticle(living, Particle.HEART, 1);
                    owner.playSound(petLoc, Sound.ENTITY_CAT_AMBIENT, 0.25f, 1.5f);
                }
                // Tier 3 — Pack Mule: small CLOUD puff
                else if (tier == PetTier.PACK_MULE && random.nextInt(40) == 0) {
                    spawnParticle(living, Particle.CLOUD, 2);
                }
                // Tier 5 — Elder: END_ROD sparkles
                else if (tier == PetTier.ELDER && random.nextInt(40) == 0) {
                    spawnParticle(living, Particle.END_ROD, 3);
                }

                // Tier 2+ — Scout: mob radar
                if (tier.getTierNumber() >= PetTier.SCOUT.getTierNumber()) {
                    doMobRadar(owner, living, data);
                }

                // Tier 4+ — Companion: queue regen aura for this owner
                if (tier.getTierNumber() >= PetTier.COMPANION.getTierNumber()) {
                    companionOwners.add(data.getOwnerUuid());
                }
            }

            // ── Regen aura — one pass per qualifying owner ────────────────────

            for (UUID ownerUuid : companionOwners) {
                doRegenAura(ownerUuid);
            }

            // ── Upgrade buffs — collect max per type, then apply ──────────────

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
                    if (upgrade == PetUpgrade.XP_BOOST) continue;
                    int upgradeLevel = data.getUpgradeLevel(upgrade);
                    if (upgradeLevel <= 0) continue;
                    PotionEffectType potionType = upgrade.getPotionType();
                    if (potionType == null) continue;
                    int amp = upgrade.getPotionAmplifier(upgradeLevel);
                    playerBuffs.merge(potionType, amp, Math::max);
                }
            }

            for (Map.Entry<UUID, Map<PotionEffectType, Integer>> entry : buffMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;
                for (Map.Entry<PotionEffectType, Integer> buff : entry.getValue().entrySet()) {
                    // 40-tick duration, refreshed every updateTicks — never expires while pet follows
                    p.addPotionEffect(new PotionEffect(buff.getKey(), 40, buff.getValue(), false, false, true));
                }
            }

        }, 20L, updateTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    // ── Mob radar (Tier 2 Scout) ──────────────────────────────────────────────

    private void doMobRadar(Player owner, LivingEntity living, PetData data) {
        long now       = System.currentTimeMillis();
        Long lastAlarm = lastMobAlarm.get(data.getPetUuid());
        if (lastAlarm != null && now - lastAlarm < 4000) return; // 4-second cooldown per pet

        List<Entity> nearby = living.getNearbyEntities(16, 16, 16);
        long hostileCount = nearby.stream().filter(e -> e instanceof Monster).count();
        if (hostileCount == 0) return;

        lastMobAlarm.put(data.getPetUuid(), now);

        Entity nearest = nearby.stream()
                .filter(e -> e instanceof Monster)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(owner.getLocation())))
                .orElse(null);

        String dir = nearest != null ? getDirection(owner, nearest) : "?";

        owner.playSound(owner.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.0f, 0.8f);
        owner.sendActionBar(Component.text(
                "§c⚠ §f" + hostileCount + " hostile(s) detected §c[" + dir + "]"));
    }

    // ── Regen aura (Tier 4 Companion) ────────────────────────────────────────

    private void doRegenAura(UUID ownerUuid) {
        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null || !owner.isOnline()) {
            ownerStillTicks.remove(ownerUuid);
            ownerLastLoc.remove(ownerUuid);
            return;
        }

        Location current = owner.getLocation().clone();
        Location last    = ownerLastLoc.get(ownerUuid);

        if (last != null && last.getWorld() != null
                && last.getWorld().equals(current.getWorld())
                && current.distanceSquared(last) < 0.01) {
            ownerStillTicks.merge(ownerUuid, 1, Integer::sum);
        } else {
            ownerStillTicks.put(ownerUuid, 0);
        }
        ownerLastLoc.put(ownerUuid, current);

        // Need 10 consecutive still ticks ≈ 5 s at default 10 updateTicks
        if (ownerStillTicks.getOrDefault(ownerUuid, 0) < 10) return;

        long now       = System.currentTimeMillis();
        Long lastRegen = ownerLastRegen.get(ownerUuid);
        if (lastRegen != null && now - lastRegen < 30_000) return; // 30-second cooldown

        var maxHealthAttr = owner.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            owner.setHealth(Math.min(maxHealthAttr.getValue(), owner.getHealth() + 1.0));
        }
        ownerLastRegen.put(ownerUuid, now);

        // Visual feedback — a couple of hearts above the owner's head
        try {
            owner.getWorld().spawnParticle(Particle.HEART,
                    owner.getLocation().add(0, 2.0, 0), 2, 0.3, 0.3, 0.3, 0);
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** 8-point compass direction from player to target entity. */
    private String getDirection(Player player, Entity target) {
        double dx = target.getLocation().getX() - player.getLocation().getX();
        double dz = target.getLocation().getZ() - player.getLocation().getZ();
        double angle   = Math.toDegrees(Math.atan2(dz, dx));
        double bearing = (angle + 90 + 360) % 360; // convert to 0=N, 90=E, 180=S, 270=W

        if (bearing < 22.5  || bearing >= 337.5) return "N";
        if (bearing < 67.5)  return "NE";
        if (bearing < 112.5) return "E";
        if (bearing < 157.5) return "SE";
        if (bearing < 202.5) return "S";
        if (bearing < 247.5) return "SW";
        if (bearing < 292.5) return "W";
        return "NW";
    }

    private void spawnParticle(LivingEntity entity, Particle particle, int count) {
        try {
            entity.getWorld().spawnParticle(particle,
                    entity.getLocation().add(0, 1.0, 0), count, 0.2, 0.2, 0.2, 0);
        } catch (Exception ignored) {}
    }

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
