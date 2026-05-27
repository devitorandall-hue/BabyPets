package net.legacy.babypets;

import net.legacy.babypets.chat.ChatNameListener;
import net.legacy.babypets.command.PetCommand;
import net.legacy.babypets.follow.PetFollowTask;
import net.legacy.babypets.gui.PetGuiListener;
import net.legacy.babypets.listener.PetInteractionListener;
import net.legacy.babypets.listener.PetProtectionListener;
import net.legacy.babypets.listener.PlayerJoinQuitListener;
import net.legacy.babypets.model.PetManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.legacy.babypets.update.UpdateChecker;

public class BabyPetsPlugin extends JavaPlugin {

    private static BabyPetsPlugin instance;
    private PetManager petManager;
    private PetFollowTask petFollowTask;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.petManager = new PetManager(this);
        this.petManager.load();

        PluginCommand petCommand = getCommand("pet");
        if (petCommand != null) {
            PetCommand commandExecutor = new PetCommand(this);
            petCommand.setExecutor(commandExecutor);
            petCommand.setTabCompleter(commandExecutor);
        }

        getServer().getPluginManager().registerEvents(new PetGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatNameListener(this), this);
        getServer().getPluginManager().registerEvents(new PetProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PetInteractionListener(this), this);

        this.petFollowTask = new PetFollowTask(this);
        this.petFollowTask.start();

        this.updateChecker = new UpdateChecker(
                this,
                "https://raw.githubusercontent.com/devitorandall-hue/BabyPets/refs/heads/main/version.txt"
        );
        this.updateChecker.check();

        getLogger().info("BabyPets has been enabled.");

    }

    @Override
    public void onDisable() {
        if (petFollowTask != null) {
            petFollowTask.stop();
        }

        if (petManager != null) {
            petManager.despawnAll();
            petManager.save();
        }

        getLogger().info("BabyPets has been disabled.");
    }

    public void reloadEverything() {
        reloadConfig();

        if (petFollowTask != null) {
            petFollowTask.stop();
            petFollowTask.start();
        }

        if (petManager != null) {
            petManager.despawnAll();
            petManager.load();

            for (Player player : getServer().getOnlinePlayers()) {
                petManager.respawnOwnerPets(player);
            }
        }
    }

    public static BabyPetsPlugin getInstance() {
        return instance;
    }

    public PetManager getPetManager() {
        return petManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}