package net.legacy.babypets.gui;

import net.legacy.babypets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class BiomeSelectMenu implements InventoryHolder {

    public record BiomeOption(String name, Material icon, String description) {}

    public static final List<BiomeOption> BIOMES = List.of(
            new BiomeOption("Plains",          Material.GRASS_BLOCK,        "&7Open and grassy."),
            new BiomeOption("Forest",          Material.OAK_LOG,            "&7Dense oak woodland."),
            new BiomeOption("Birch Forest",    Material.BIRCH_LOG,          "&7Tall white-barked trees."),
            new BiomeOption("Dark Forest",     Material.DARK_OAK_LOG,       "&7A shadowy, gloomy wood."),
            new BiomeOption("Jungle",          Material.JUNGLE_LOG,         "&7Lush and tropical."),
            new BiomeOption("Taiga",           Material.SPRUCE_LOG,         "&7Cold spruce forest."),
            new BiomeOption("Cherry Grove",    Material.CHERRY_LEAVES,      "&7Pink blossoms in the breeze."),
            new BiomeOption("Desert",          Material.SAND,               "&7Vast golden dunes."),
            new BiomeOption("Savanna",         Material.ACACIA_LOG,         "&7Dry grasslands."),
            new BiomeOption("Badlands",        Material.RED_SAND,           "&7Eroded red mesas."),
            new BiomeOption("Snowy Plains",    Material.SNOW_BLOCK,         "&7A frozen, windswept expanse."),
            new BiomeOption("Frozen Ocean",    Material.BLUE_ICE,           "&7Icy waters and packed floes."),
            new BiomeOption("Swamp",           Material.SLIME_BLOCK,        "&7Murky waters and mossy trees."),
            new BiomeOption("Mushroom Fields", Material.RED_MUSHROOM_BLOCK, "&7Giant mushrooms and mycelium."),
            new BiomeOption("Ocean",           Material.PRISMARINE,         "&7Deep blue waters."),
            new BiomeOption("Nether Wastes",   Material.NETHERRACK,         "&7A blazing hellscape."),
            new BiomeOption("Crimson Forest",  Material.CRIMSON_NYLIUM,     "&7Red fungal forest of the Nether."),
            new BiomeOption("Warped Forest",   Material.WARPED_NYLIUM,      "&7Eerie blue-green Nether woodland."),
            new BiomeOption("Soul Sand Valley",Material.SOUL_SAND,          "&7Haunted vale of ancient souls."),
            new BiomeOption("The End",         Material.END_STONE,          "&7The void between worlds.")
    );

    public static final int[] BIOME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33
    };

    private final Inventory inventory;

    public BiomeSelectMenu() {
        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.color("&0Choose a Biome"));
        fillBackground();

        for (int i = 0; i < Math.min(BIOMES.size(), BIOME_SLOTS.length); i++) {
            inventory.setItem(BIOME_SLOTS[i], createBiomeItem(BIOMES.get(i)));
        }
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(MessageUtil.color("&fChoose a Biome"));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack createBiomeItem(BiomeOption biome) {
        ItemStack item = new ItemStack(biome.icon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(MessageUtil.color("&f" + biome.name()));
            meta.setLore(List.of(
                    MessageUtil.color(biome.description()),
                    MessageUtil.color(""),
                    MessageUtil.color("&eClick to select!")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
