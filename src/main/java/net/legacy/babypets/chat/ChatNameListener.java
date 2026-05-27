package net.legacy.babypets.chat;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.util.MessageUtil;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatNameListener implements Listener {

    private final BabyPetsPlugin plugin;

    public ChatNameListener(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PetManager petManager = plugin.getPetManager();

        EntityType pendingCreate = petManager.getPendingCreateType(player.getUniqueId());
        UUID pendingRename = petManager.getPendingRenamePet(player.getUniqueId());

        if (pendingCreate == null && pendingRename == null) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            petManager.clearPendingCreateType(player.getUniqueId());
            petManager.clearPendingBiome(player.getUniqueId());
            petManager.clearPendingRenamePet(player.getUniqueId());

            player.getServer().getScheduler().runTask(plugin, () ->
                    MessageUtil.sendWithPrefix(player, "&cPet action cancelled."));
            return;
        }

        int maxLength = plugin.getConfig().getInt("naming.max-length", 8);
        if (stripCodesForLength(message).length() > maxLength) {
            player.getServer().getScheduler().runTask(plugin, () ->
                    MessageUtil.sendWithPrefix(player, "&cThat name is too long. Max " + maxLength + " visible characters."));
            return;
        }

        String finalName = message.replace("§", "&");

        player.getServer().getScheduler().runTask(plugin, () -> {
            if (pendingCreate != null) {
                String biome = petManager.getPendingBiome(player.getUniqueId());
                PetData data = petManager.createPet(player, pendingCreate, biome != null ? biome : "Plains", finalName);
                petManager.clearPendingCreateType(player.getUniqueId());
                petManager.clearPendingBiome(player.getUniqueId());

                if (data != null) {
                    MessageUtil.sendWithPrefix(player,
                            plugin.getConfig().getString("messages.pet-created", "&aYour pet &f{name} &ahas been created.")
                                    .replace("{name}", data.getName()));
                } else {
                    MessageUtil.sendWithPrefix(player, "&cFailed to create pet.");
                }
            } else if (pendingRename != null) {
                PetData data = null;
                for (PetData pet : petManager.getPets(player.getUniqueId())) {
                    if (pet.getPetUuid().equals(pendingRename)) {
                        data = pet;
                        break;
                    }
                }

                petManager.clearPendingRenamePet(player.getUniqueId());

                if (data != null) {
                    petManager.renamePet(data, finalName);
                    MessageUtil.sendWithPrefix(player,
                            plugin.getConfig().getString("messages.pet-renamed", "&aYour pet is now named &f{name}&a.")
                                    .replace("{name}", finalName));
                } else {
                    MessageUtil.send(player, "messages.pet-not-found");
                }
            }
        });
    }

    private String stripCodesForLength(String text) {
        return text.replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }
}