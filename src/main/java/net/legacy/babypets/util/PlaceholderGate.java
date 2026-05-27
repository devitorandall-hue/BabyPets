package net.legacy.babypets.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.legacy.babypets.BabyPetsPlugin;
import org.bukkit.entity.Player;

public class PlaceholderGate {

    private final BabyPetsPlugin plugin;

    public PlaceholderGate(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getAllowedPetCount(Player player) {
        String requiredValue = plugin.getConfig().getString("limits.placeholder-value", "yes");
        int allowed = 0;

        for (int i = 1; i <= 5; i++) {
            String placeholder = plugin.getConfig().getString("limits.slot-placeholders." + i);
            if (placeholder == null || placeholder.isBlank()) {
                continue;
            }

            String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
            if (resolved != null && resolved.equalsIgnoreCase(requiredValue)) {
                allowed++;
            }
        }

        return allowed;
    }

    public boolean canUsePets(Player player) {
        return getAllowedPetCount(player) > 0;
    }
}