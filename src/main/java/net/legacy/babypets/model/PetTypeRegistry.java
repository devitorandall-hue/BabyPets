package net.legacy.babypets.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class PetTypeRegistry {

    public static List<EntityType> getEnabledTypes(List<String> names) {
        List<EntityType> types = new ArrayList<>();

        for (String name : names) {
            try {
                types.add(EntityType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return types;
    }

    public static Material getIcon(EntityType type) {
        return switch (type) {
            case COW -> Material.COW_SPAWN_EGG;
            case SHEEP -> Material.SHEEP_SPAWN_EGG;
            case PIG -> Material.PIG_SPAWN_EGG;
            case CHICKEN -> Material.CHICKEN_SPAWN_EGG;
            case RABBIT -> Material.RABBIT_SPAWN_EGG;
            case GOAT -> Material.GOAT_SPAWN_EGG;
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case OCELOT -> Material.OCELOT_SPAWN_EGG;
            case FOX -> Material.FOX_SPAWN_EGG;
            case PANDA -> Material.PANDA_SPAWN_EGG;
            case POLAR_BEAR -> Material.POLAR_BEAR_SPAWN_EGG;
            case TURTLE -> Material.TURTLE_SPAWN_EGG;
            case BEE -> Material.BEE_SPAWN_EGG;
            case CAMEL -> Material.CAMEL_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case TRADER_LLAMA -> Material.TRADER_LLAMA_SPAWN_EGG;
            case MOOSHROOM -> Material.MOOSHROOM_SPAWN_EGG;
            case HOGLIN -> Material.HOGLIN_SPAWN_EGG;
            case STRIDER -> Material.STRIDER_SPAWN_EGG;
            case SNIFFER -> Material.SNIFFER_SPAWN_EGG;
            case AXOLOTL -> Material.AXOLOTL_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            case FROG -> Material.FROG_SPAWN_EGG;
            case ALLAY -> Material.ALLAY_SPAWN_EGG;
            default -> Material.NAME_TAG;
        };
    }

    public static String getBabyDisplayName(EntityType type) {
        return switch (type) {
            case TRADER_LLAMA -> "Baby Trader Llama";
            case POLAR_BEAR -> "Baby Polar Bear";
            case MOOSHROOM -> "Baby Mooshroom";
            default -> "Baby " + pretty(type.name());
        };
    }

    private static String pretty(String input) {
        String lower = input.toLowerCase().replace("_", " ");
        String[] parts = lower.split(" ");
        StringBuilder out = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }

        return out.toString().trim();
    }
}