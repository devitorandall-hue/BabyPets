package net.legacy.babypets.util;

import net.legacy.babypets.BabyPetsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String prefix() {
        return color(BabyPetsPlugin.getInstance().getConfig().getString("messages.prefix", "&d&lBabyPets &7» "));
    }

    public static void send(CommandSender sender, String path) {
        String message = BabyPetsPlugin.getInstance().getConfig().getString(path, "&cMissing message: " + path);
        sender.sendMessage(prefix() + color(message));
    }

    public static void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(color(text));
    }

    public static void sendWithPrefix(CommandSender sender, String text) {
        sender.sendMessage(prefix() + color(text));
    }

    public static String get(String path) {
        return color(BabyPetsPlugin.getInstance().getConfig().getString(path, ""));
    }

    public static String get(String path, String fallback) {
        return color(BabyPetsPlugin.getInstance().getConfig().getString(path, fallback));
    }
}