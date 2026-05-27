package net.legacy.babypets.listener;

import net.legacy.babypets.BabyPetsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final BabyPetsPlugin plugin;

    public PlayerJoinQuitListener(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getPetManager().respawnOwnerPets(player);

        if (player.isOp()) {
            player.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
                    player.sendMessage("§6[BabyPets] §eA new update is available: §f"
                            + plugin.getUpdateChecker().getLatestVersion());
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetManager().despawnOwnerPets(event.getPlayer().getUniqueId());
    }
}