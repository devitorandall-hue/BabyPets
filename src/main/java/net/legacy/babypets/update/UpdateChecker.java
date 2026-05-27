package net.legacy.babypets.update;

import net.legacy.babypets.BabyPetsPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class UpdateChecker {

    private final BabyPetsPlugin plugin;
    private final String versionUrl;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(BabyPetsPlugin plugin, String versionUrl) {
        this.plugin = plugin;
        this.versionUrl = versionUrl;
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(versionUrl);
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String line = reader.readLine();
                reader.close();

                if (line == null || line.trim().isEmpty()) {
                    plugin.getLogger().warning("Update check failed: version file was empty.");
                    return;
                }

                latestVersion = line.trim();

                String currentVersion = plugin.getDescription().getVersion();
                updateAvailable = !currentVersion.equalsIgnoreCase(latestVersion);

                if (updateAvailable) {
                    plugin.getLogger().warning("A new BabyPets update is available!");
                    plugin.getLogger().warning("Current version: " + currentVersion);
                    plugin.getLogger().warning("Latest version: " + latestVersion);
                } else {
                    plugin.getLogger().info("BabyPets is up to date.");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}