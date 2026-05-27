package net.legacy.babypets.gui;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.model.PetTypeRegistry;
import net.legacy.babypets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PetCreateMenu implements InventoryHolder {

    private final Inventory inventory;

    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    public PetCreateMenu(BabyPetsPlugin plugin) {
        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.color("&0Choose a Pet"));

        fillBackground();

        List<EntityType> types = PetTypeRegistry.getEnabledTypes(
                plugin.getConfig().getStringList("pets.enabled-types")
        );

        for (int i = 0; i < Math.min(types.size(), PET_SLOTS.length); i++) {
            EntityType type = types.get(i);
            inventory.setItem(PET_SLOTS[i], createPetItem(type));
        }
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(MessageUtil.color("&fChoose a Pet"));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack createPetItem(EntityType type) {
        ItemStack item = new ItemStack(PetTypeRegistry.getIcon(type));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(MessageUtil.color("&f" + PetTypeRegistry.getBabyDisplayName(type)));
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