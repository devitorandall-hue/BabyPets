package net.legacy.babypets.listener;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.gui.PetStatsMenu;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PetProtectionListener implements Listener {

    private final BabyPetsPlugin plugin;

    public PetProtectionListener(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPetDamage(EntityDamageEvent event) {
        PetManager petManager = plugin.getPetManager();
        Entity entity = event.getEntity();

        if (!petManager.isPet(entity)) {
            return;
        }

        event.setCancelled(true);

        if (entity instanceof LivingEntity living) {
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.setHealth(living.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
            living.setFireTicks(0);
        }
    }

    @EventHandler
    public void onPetAttack(EntityDamageByEntityEvent event) {
        if (plugin.getPetManager().isPet(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPetTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();

        if (!plugin.getPetManager().isPet(entity)) {
            return;
        }

        event.setCancelled(true);

        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        // Only handle the main-hand interaction to avoid firing twice
        if (event.getHand() != EquipmentSlot.HAND) return;

        PetManager petManager = plugin.getPetManager();
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        if (!petManager.isPet(entity)) {
            return;
        }

        // Cancel vanilla interaction (prevents feeding, taming, riding, etc.)
        event.setCancelled(true);

        if (!petManager.isOwner(player, entity)) {
            return;
        }

        // Open the pet stats GUI for the owner
        PetData petData = petManager.findPetDataByEntity(entity);
        if (petData != null) {
            new PetStatsMenu(plugin, petData).open(player);
            return;
        }

        // Fallback: re-apply safety properties in case PDC lookup fails
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
        }

        if (entity instanceof Ageable ageable) {
            ageable.setAgeLock(true);
            ageable.setBaby();
        }

        if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        if (entity instanceof Piglin piglin) {
            piglin.setImmuneToZombification(true);
        }

        if (entity instanceof Strider strider) {
            strider.setShivering(false);
        }

        // NOTE: We intentionally do NOT call setTamed(false) here.
        // Cats need to stay tamed to prevent their flee AI from activating,
        // and the event is already cancelled so vanilla taming cannot occur.
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent event) {
        if (plugin.getPetManager().isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPetTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();

        if (!plugin.getPetManager().isPet(entity)) {
            return;
        }

        if (entity instanceof Hoglin || entity instanceof Piglin || entity instanceof Strider) {
            event.setCancelled(true);
        }
    }
}
