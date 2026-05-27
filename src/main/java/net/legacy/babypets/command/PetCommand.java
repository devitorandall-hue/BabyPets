package net.legacy.babypets.command;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.gui.PetCreateMenu;
import net.legacy.babypets.model.PetData;
import net.legacy.babypets.model.PetManager;
import net.legacy.babypets.util.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final BabyPetsPlugin plugin;

    public PetCommand(BabyPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "messages.players-only");
            return true;
        }

        if (!player.hasPermission("babypets.use")) {
            MessageUtil.send(player, "messages.no-permission");
            return true;
        }

        PetManager petManager = plugin.getPetManager();
        String sub = args.length > 0 ? args[0].toLowerCase() : "";

        if (args.length == 0) {
            List<PetData> pets = petManager.getPets(player.getUniqueId());

            if (pets.isEmpty()) {
                sendCommandHelp(player);
                return true;
            }

            MessageUtil.sendWithPrefix(player, "&d&lYour Pets &7(" + pets.size() + "/" + petManager.getPetLimit(player) + ")");
            for (PetData pet : pets) {
                String cleanName = petManager.stripLegacyCodes(pet.getName());
                String status = pet.isSitting() ? "&eSitting" : "&aFollowing";
                MessageUtil.sendWithPrefix(player, "&7- &f" + cleanName + " &8| &7Type: &d" + pet.getType().name() + " &8| " + status);
            }

            MessageUtil.sendWithPrefix(player, "&7Use &f/pet sit <pet> &7or &f/pet unsit <pet>&7.");
            return true;
        }

        switch (sub) {

            case "create" -> {
                int limit = petManager.getPetLimit(player);
                int current = petManager.getPets(player.getUniqueId()).size();

                if (!player.isOp() && !player.hasPermission("babypets.admin") && current >= limit) {
                    MessageUtil.sendWithPrefix(player, "&cYou have reached your pet limit of &f" + limit + "&c.");
                    return true;
                }

                new PetCreateMenu(plugin).open(player);
                return true;
            }

            case "remove" -> {
                if (args.length < 2) {
                    MessageUtil.sendWithPrefix(player, "&cUsage: /pet remove <pet>");
                    return true;
                }

                PetData data = petManager.findPetByName(player.getUniqueId(), args[1]);

                if (data == null) {
                    MessageUtil.send(player, "messages.pet-not-found");
                    return true;
                }

                petManager.removePet(data);
                MessageUtil.sendWithPrefix(player, "&cRemoved pet &f" + petManager.stripLegacyCodes(data.getName()) + "&c.");
                return true;
            }

            case "rename" -> {
                if (args.length < 2) {
                    MessageUtil.sendWithPrefix(player, "&cUsage: /pet rename <pet>");
                    return true;
                }

                PetData data = petManager.findPetByName(player.getUniqueId(), args[1]);

                if (data == null) {
                    MessageUtil.send(player, "messages.pet-not-found");
                    return true;
                }

                petManager.setPendingRenamePet(player.getUniqueId(), data.getPetUuid());
                MessageUtil.send(player, "messages.choose-name");
                return true;
            }

            case "check" -> {
                List<PetData> pets = petManager.getPets(player.getUniqueId());

                if (pets.isEmpty()) {
                    MessageUtil.sendWithPrefix(player, "&cYou have no pets.");
                    return true;
                }

                if (args.length >= 2) {
                    PetData data = petManager.findPetByName(player.getUniqueId(), args[1]);

                    if (data == null) {
                        MessageUtil.send(player, "messages.pet-not-found");
                        return true;
                    }

                    String cleanName = petManager.stripLegacyCodes(data.getName());
                    String status = data.isSitting() ? "&eSitting" : "&aFollowing";
                    String world = data.getLastLocation() != null && data.getLastLocation().getWorld() != null
                            ? data.getLastLocation().getWorld().getName() : "&7Unknown";

                    MessageUtil.sendWithPrefix(player, "&f" + cleanName
                            + " &8| &7Type: &d" + data.getType().name()
                            + " &8| &7Biome: &b" + data.getBiome()
                            + " &8| &7Status: " + status
                            + " &8| &7World: &f" + world);

                    petManager.glowPets(player, args[1]);
                    MessageUtil.sendWithPrefix(player, "&aHighlighting &f" + cleanName + " &afor &f10 seconds&a.");
                } else {
                    MessageUtil.sendWithPrefix(player, "&d&lYour Pets &7(" + pets.size() + "/" + petManager.getPetLimit(player) + ")");

                    for (PetData data : pets) {
                        String cleanName = petManager.stripLegacyCodes(data.getName());
                        String status = data.isSitting() ? "&eSitting" : "&aFollowing";
                        String world = data.getLastLocation() != null && data.getLastLocation().getWorld() != null
                                ? data.getLastLocation().getWorld().getName() : "Unknown";

                        MessageUtil.sendWithPrefix(player, "&7- &f" + cleanName
                                + " &8| &7Type: &d" + data.getType().name()
                                + " &8| &7Biome: &b" + data.getBiome()
                                + " &8| " + status
                                + " &8| &7World: &f" + world);
                    }

                    petManager.glowPets(player, null);
                    MessageUtil.sendWithPrefix(player, "&aHighlighting all pets for &f10 seconds&a.");
                }

                player.getServer().getScheduler().runTaskLater(plugin,
                        () -> petManager.clearGlow(player),
                        200L);

                return true;
            }

            case "sit" -> {
                if (args.length < 2) {
                    MessageUtil.sendWithPrefix(player, "&cUsage: /pet sit <pet>");
                    return true;
                }

                PetData data = petManager.findPetByName(player.getUniqueId(), args[1]);

                if (data == null) {
                    MessageUtil.send(player, "messages.pet-not-found");
                    return true;
                }

                petManager.setPetSitting(data, true);
                MessageUtil.sendWithPrefix(player, "&aYour pet &f" + petManager.stripLegacyCodes(data.getName()) + " &ais now sitting.");
                return true;
            }

            case "unsit" -> {
                if (args.length < 2) {
                    MessageUtil.sendWithPrefix(player, "&cUsage: /pet unsit <pet>");
                    return true;
                }

                PetData data = petManager.findPetByName(player.getUniqueId(), args[1]);

                if (data == null) {
                    MessageUtil.send(player, "messages.pet-not-found");
                    return true;
                }

                petManager.setPetSitting(data, false);
                MessageUtil.sendWithPrefix(player, "&aYour pet &f" + petManager.stripLegacyCodes(data.getName()) + " &awill now follow you.");
                return true;
            }

            case "reload" -> {
                if (!player.hasPermission("babypets.admin")) {
                    MessageUtil.send(player, "messages.no-permission");
                    return true;
                }

                plugin.reloadEverything();
                MessageUtil.sendWithPrefix(player, "&aReloaded BabyPets.");
                return true;
            }

            default -> {
                MessageUtil.sendWithPrefix(player, "&cUnknown subcommand.");
                sendCommandHelp(player);
                return true;
            }
        }
    }

    private void sendCommandHelp(Player player) {
        MessageUtil.sendWithPrefix(player, "&f/pet create &7- Create a pet");
        MessageUtil.sendWithPrefix(player, "&f/pet remove <pet> &7- Remove a pet");
        MessageUtil.sendWithPrefix(player, "&f/pet rename <pet> &7- Rename a pet");
        MessageUtil.sendWithPrefix(player, "&f/pet check [pet] &7- Glow pets");
        MessageUtil.sendWithPrefix(player, "&f/pet sit <pet> &7- Make a pet stay");
        MessageUtil.sendWithPrefix(player, "&f/pet unsit <pet> &7- Make a pet follow again");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> out = new ArrayList<>();

        if (!(sender instanceof Player player)) {
            return out;
        }

        if (args.length == 1) {
            out.add("create");
            out.add("remove");
            out.add("rename");
            out.add("check");
            out.add("sit");
            out.add("unsit");

            if (player.hasPermission("babypets.admin")) {
                out.add("reload");
            }

            return out;
        }

        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("remove")
                        || args[0].equalsIgnoreCase("rename")
                        || args[0].equalsIgnoreCase("check")
                        || args[0].equalsIgnoreCase("sit")
                        || args[0].equalsIgnoreCase("unsit"))) {

            for (PetData pet : plugin.getPetManager().getPets(player.getUniqueId())) {
                out.add(plugin.getPetManager().stripLegacyCodes(pet.getName()));
            }
        }

        return out;
    }
}