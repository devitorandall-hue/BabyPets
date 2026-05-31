package net.legacy.babypets.model;

import net.legacy.babypets.BabyPetsPlugin;
import net.legacy.babypets.storage.MySqlStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PetManager {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final BabyPetsPlugin plugin;
    private final Map<UUID, List<PetData>> petsByOwner  = new HashMap<>();
    private final Map<UUID, UUID>          activeEntities = new HashMap<>();
    private final Map<UUID, EntityType>    pendingCreateType = new HashMap<>();
    private final Map<UUID, String>        pendingBiome      = new HashMap<>();
    private final Map<UUID, UUID>          pendingRenamePet  = new HashMap<>();

    private final NamespacedKey ownerKey;
    private final NamespacedKey petKey;

    private final boolean     useMySQL;
    private final MySqlStorage mysql;

    private File               dataFile;
    private YamlConfiguration  dataConfig;

    public PetManager(BabyPetsPlugin plugin) {
        this.plugin   = plugin;
        this.ownerKey = new NamespacedKey(plugin, "pet_owner");
        this.petKey   = new NamespacedKey(plugin, "pet_id");
        this.useMySQL = plugin.getConfig().getString("storage.type", "YAML").equalsIgnoreCase("MYSQL");
        this.mysql    = useMySQL ? new MySqlStorage(plugin) : null;
    }

    private boolean isMySQLActive() {
        return useMySQL && mysql != null && mysql.isAvailable();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void load() {
        petsByOwner.clear();
        activeEntities.clear();

        dataFile = new File(plugin.getDataFolder(), "pets.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (useMySQL) return; // MySQL: load per-player on join

        ConfigurationSection petsSection = dataConfig.getConfigurationSection("pets");
        if (petsSection == null) return;

        for (String ownerKeyString : petsSection.getKeys(false)) {
            UUID ownerUuid;
            try { ownerUuid = UUID.fromString(ownerKeyString); }
            catch (IllegalArgumentException ex) { continue; }

            List<PetData> list = new ArrayList<>();
            ConfigurationSection ownerSection = petsSection.getConfigurationSection(ownerKeyString);
            if (ownerSection == null) continue;

            for (String petIdString : ownerSection.getKeys(false)) {
                ConfigurationSection petSection = ownerSection.getConfigurationSection(petIdString);
                if (petSection == null) continue;

                try {
                    UUID       petUuid = UUID.fromString(petIdString);
                    EntityType type    = EntityType.valueOf(petSection.getString("type", "COW"));
                    String     name    = petSection.getString("name", "Pet");
                    String     biome   = petSection.getString("biome", "Plains");
                    boolean    active  = petSection.getBoolean("active", false);
                    boolean    sitting = petSection.getBoolean("sitting", false);

                    // ── XP: prefer new total-xp; fall back to legacy level/xp ─
                    int totalXp;
                    if (petSection.contains("total-xp")) {
                        totalXp = petSection.getInt("total-xp", 0);
                    } else {
                        int oldLevel = petSection.getInt("level", 1);
                        int oldXp    = petSection.getInt("xp", 0);
                        totalXp = PetData.legacyToTotalXp(oldLevel, oldXp);
                    }

                    // ── Upgrades ─────────────────────────────────────────────
                    Map<String, Integer> upgrades = new HashMap<>();
                    ConfigurationSection upgradesSection = petSection.getConfigurationSection("upgrades");
                    if (upgradesSection != null) {
                        for (String uk : upgradesSection.getKeys(false)) {
                            int uv = upgradesSection.getInt(uk, 0);
                            if (uv > 0) upgrades.put(uk, uv);
                        }
                    }

                    // ── Pet inventory (3 slots) ───────────────────────────────
                    ItemStack[] petInventory = new ItemStack[3];
                    ConfigurationSection invSection = petSection.getConfigurationSection("pet-inventory");
                    if (invSection != null) {
                        for (int i = 0; i < 3; i++) {
                            ItemStack stack = invSection.getItemStack(String.valueOf(i));
                            petInventory[i] = stack;
                        }
                    }

                    // ── Waypoints (3 strings) ─────────────────────────────────
                    String[] waypoints = new String[3];
                    ConfigurationSection wpSection = petSection.getConfigurationSection("waypoints");
                    if (wpSection != null) {
                        for (int i = 0; i < 3; i++) {
                            waypoints[i] = wpSection.getString(String.valueOf(i));
                        }
                    }

                    // ── Location ──────────────────────────────────────────────
                    String worldName = petSection.getString("location.world");
                    World  world     = worldName != null ? Bukkit.getWorld(worldName) : null;
                    double x = petSection.getDouble("location.x");
                    double y = petSection.getDouble("location.y");
                    double z = petSection.getDouble("location.z");
                    Location loc = world != null ? new Location(world, x, y, z) : null;

                    list.add(new PetData(ownerUuid, petUuid, type, name, biome, loc, active, sitting,
                            totalXp, upgrades, petInventory, waypoints));
                } catch (Exception ignored) {}
            }

            petsByOwner.put(ownerUuid, list);
        }
    }

    public void loadPets(UUID ownerUuid) {
        if (!isMySQLActive()) return;
        List<PetData> pets = mysql.loadPets(ownerUuid);
        petsByOwner.put(ownerUuid, pets);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void save() {
        if (isMySQLActive()) {
            for (PetData data : getAllPets()) savePet(data);
            return;
        }

        if (dataConfig == null) dataConfig = new YamlConfiguration();
        dataConfig.set("pets", null);

        for (Map.Entry<UUID, List<PetData>> entry : petsByOwner.entrySet()) {
            String ownerPath = "pets." + entry.getKey();

            for (PetData data : entry.getValue()) {
                String path = ownerPath + "." + data.getPetUuid();

                dataConfig.set(path + ".type",    data.getType().name());
                dataConfig.set(path + ".name",    data.getName());
                dataConfig.set(path + ".biome",   data.getBiome());
                dataConfig.set(path + ".active",  data.isActive());
                dataConfig.set(path + ".sitting", data.isSitting());
                dataConfig.set(path + ".total-xp", data.getTotalXp());

                // Upgrades
                dataConfig.set(path + ".upgrades", null);
                for (Map.Entry<String, Integer> ue : data.getUpgrades().entrySet()) {
                    if (ue.getValue() > 0) {
                        dataConfig.set(path + ".upgrades." + ue.getKey(), ue.getValue());
                    }
                }

                // Pet inventory (3 slots)
                dataConfig.set(path + ".pet-inventory", null);
                ItemStack[] inv = data.getPetInventory();
                for (int i = 0; i < inv.length; i++) {
                    if (inv[i] != null && inv[i].getType() != Material.AIR) {
                        dataConfig.set(path + ".pet-inventory." + i, inv[i]);
                    }
                }

                // Waypoints (3 strings)
                dataConfig.set(path + ".waypoints", null);
                String[] wps = data.getWaypoints();
                for (int i = 0; i < wps.length; i++) {
                    if (wps[i] != null && !wps[i].isEmpty()) {
                        dataConfig.set(path + ".waypoints." + i, wps[i]);
                    }
                }

                // Location
                Location loc = data.getLastLocation();
                if (loc != null && loc.getWorld() != null) {
                    dataConfig.set(path + ".location.world", loc.getWorld().getName());
                    dataConfig.set(path + ".location.x", loc.getX());
                    dataConfig.set(path + ".location.y", loc.getY());
                    dataConfig.set(path + ".location.z", loc.getZ());
                }
            }
        }

        try { dataConfig.save(dataFile); }
        catch (IOException e) { e.printStackTrace(); }
    }

    public void savePet(PetData data) {
        if (isMySQLActive()) {
            String serverName = plugin.getConfig().getString("server-name", "survival");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> mysql.savePet(data, serverName));
        } else {
            save();
        }
    }

    private void savePetSync(PetData data) {
        if (isMySQLActive()) {
            mysql.savePet(data, plugin.getConfig().getString("server-name", "survival"));
        } else {
            save();
        }
    }

    private void deletePet(PetData data) {
        if (isMySQLActive()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> mysql.deletePet(data.getPetUuid()));
        } else {
            save();
        }
    }

    // ── Pet CRUD ──────────────────────────────────────────────────────────────

    public List<PetData> getPets(UUID ownerUuid) {
        return petsByOwner.computeIfAbsent(ownerUuid, k -> new ArrayList<>());
    }

    public List<PetData> getAllPets() {
        List<PetData> all = new ArrayList<>();
        for (List<PetData> list : petsByOwner.values()) all.addAll(list);
        return all;
    }

    public PetData createPet(Player owner, EntityType type, String biome, String name) {
        Location spawnLoc = owner.getLocation().clone().add(1.5, 0, 1.5);
        Entity entity = owner.getWorld().spawnEntity(spawnLoc, type);

        if (!(entity instanceof LivingEntity living)) { entity.remove(); return null; }

        setupLivingPet(living, owner, name, false);
        applyBiomeVariant(living, biome);

        UUID petUuid = UUID.randomUUID();
        living.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, petUuid.toString());

        PetData data = new PetData(owner.getUniqueId(), petUuid, type, name, biome,
                living.getLocation(), true, false);
        getPets(owner.getUniqueId()).add(data);
        activeEntities.put(petUuid, living.getUniqueId());

        savePet(data);
        return data;
    }

    public void removePet(PetData data) {
        Entity liveEntity = getLiveEntity(data);
        if (liveEntity != null) liveEntity.remove();

        getPets(data.getOwnerUuid()).removeIf(p -> p.getPetUuid().equals(data.getPetUuid()));
        activeEntities.remove(data.getPetUuid());
        deletePet(data);
    }

    public void renamePet(PetData data, String newName) {
        data.setName(newName);
        Entity liveEntity = getLiveEntity(data);
        if (liveEntity instanceof LivingEntity living) {
            living.setCustomName(colorize(newName));
            living.setCustomNameVisible(true);
        }
        savePet(data);
    }

    public PetData findPetByName(UUID ownerUuid, String name) {
        String input = stripLegacyCodes(name).trim();
        for (PetData data : getPets(ownerUuid)) {
            if (stripLegacyCodes(data.getName()).trim().equalsIgnoreCase(input)) return data;
        }
        return null;
    }

    public Entity getLiveEntity(PetData data) {
        UUID entityUuid = activeEntities.get(data.getPetUuid());
        return entityUuid != null ? Bukkit.getEntity(entityUuid) : null;
    }

    /**
     * Looks up PetData for a live entity by reading its pet UUID from the PDC.
     */
    public PetData findPetDataByEntity(Entity entity) {
        String petUuidStr = entity.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (petUuidStr == null) return null;
        UUID petUuid;
        try { petUuid = UUID.fromString(petUuidStr); } catch (IllegalArgumentException e) { return null; }

        for (List<PetData> pets : petsByOwner.values()) {
            for (PetData data : pets) {
                if (data.getPetUuid().equals(petUuid)) return data;
            }
        }
        return null;
    }

    // ── Pending state ─────────────────────────────────────────────────────────

    public void setPendingCreateType(UUID p, EntityType t) { pendingCreateType.put(p, t); }
    public EntityType getPendingCreateType(UUID p)         { return pendingCreateType.get(p); }
    public void clearPendingCreateType(UUID p)             { pendingCreateType.remove(p); }

    public void setPendingBiome(UUID p, String b)  { pendingBiome.put(p, b); }
    public String getPendingBiome(UUID p)           { return pendingBiome.get(p); }
    public void clearPendingBiome(UUID p)           { pendingBiome.remove(p); }

    public void setPendingRenamePet(UUID p, UUID pet) { pendingRenamePet.put(p, pet); }
    public UUID getPendingRenamePet(UUID p)            { return pendingRenamePet.get(p); }
    public void clearPendingRenamePet(UUID p)          { pendingRenamePet.remove(p); }

    // ── Location / sitting helpers ────────────────────────────────────────────

    public void setLastLocation(PetData data, Location location) { data.setLastLocation(location); }

    public void setPetSitting(PetData data, boolean sitting) {
        data.setSitting(sitting);
        Entity entity = getLiveEntity(data);
        if (entity instanceof LivingEntity living) {
            living.setAI(!sitting);
            if (sitting) data.setLastLocation(living.getLocation());
        }
        savePet(data);
    }

    // ── Spawn / despawn helpers ───────────────────────────────────────────────

    public boolean isPet(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    public boolean isOwner(Player player, Entity entity) {
        if (entity == null) return false;
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner != null && owner.equals(player.getUniqueId().toString());
    }

    public NamespacedKey getOwnerKey() { return ownerKey; }
    public NamespacedKey getPetKey()   { return petKey; }

    public int getPetLimit(Player player) {
        if (player.hasPermission("babypets.user.amount.unlimited")) return Integer.MAX_VALUE;
        int max = 0;
        for (int i = 1; i <= 100; i++) {
            if (player.hasPermission("babypets.user.amount." + i)) max = Math.max(max, i);
        }
        return max > 0 ? max : 1;
    }

    public void despawnOwnerPets(UUID ownerUuid) {
        for (PetData data : getPets(ownerUuid)) {
            Entity entity = getLiveEntity(data);
            if (entity != null) {
                data.setLastLocation(entity.getLocation());
                entity.remove();
            }
            activeEntities.remove(data.getPetUuid());
            data.setActive(false);
            savePet(data);
        }
    }

    public void respawnOwnerPets(Player owner) {
        if (isMySQLActive()) {
            UUID ownerUuid = owner.getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                loadPets(ownerUuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (owner.isOnline()) spawnLoadedPets(owner);
                });
            });
        } else {
            spawnLoadedPets(owner);
        }
    }

    private void spawnLoadedPets(Player owner) {
        for (PetData data : getPets(owner.getUniqueId())) {
            Entity existing = getLiveEntity(data);
            if (existing instanceof LivingEntity livingExisting && !livingExisting.isDead()) continue;

            Location spawn = (data.isSitting() && data.getLastLocation() != null
                    && data.getLastLocation().getWorld() != null)
                    ? data.getLastLocation().clone()
                    : owner.getLocation().clone().add(1.5, 0, 1.5);

            Entity entity;
            try { entity = spawn.getWorld().spawnEntity(spawn, data.getType()); }
            catch (Exception ignored) { data.setActive(false); savePet(data); continue; }

            if (!(entity instanceof LivingEntity living)) {
                entity.remove(); data.setActive(false); savePet(data); continue;
            }

            setupLivingPet(living, owner, data.getName(), data.isSitting());
            applyBiomeVariant(living, data.getBiome());
            living.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, data.getPetUuid().toString());

            activeEntities.put(data.getPetUuid(), living.getUniqueId());
            data.setLastLocation(living.getLocation());
            data.setActive(true);
            savePet(data);
        }
    }

    public void despawnAll() {
        for (PetData data : getAllPets()) {
            Entity entity = getLiveEntity(data);
            if (entity != null) {
                data.setLastLocation(entity.getLocation());
                entity.remove();
            }
            activeEntities.remove(data.getPetUuid());
            data.setActive(false);
            savePetSync(data);
        }
    }

    public void glowPets(Player owner, String nameOrNull) {
        for (PetData data : getPets(owner.getUniqueId())) {
            if (nameOrNull == null || stripLegacyCodes(data.getName()).equalsIgnoreCase(stripLegacyCodes(nameOrNull))) {
                Entity entity = getLiveEntity(data);
                if (entity instanceof LivingEntity living) living.setGlowing(true);
            }
        }
    }

    public void clearGlow(Player owner) {
        for (PetData data : getPets(owner.getUniqueId())) {
            Entity entity = getLiveEntity(data);
            if (entity instanceof LivingEntity living) living.setGlowing(false);
        }
    }

    // ── String helpers ────────────────────────────────────────────────────────

    public String stripLegacyCodes(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)&#[A-F0-9]{6}", "")
                .replaceAll("(?i)&x(&[A-F0-9]){6}", "")
                .replaceAll("(?i)§x(§[A-F0-9]){6}", "")
                .replaceAll("(?i)[&§][0-9A-FK-ORX]", "");
    }

    private String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    // ── Entity setup / biome variant ─────────────────────────────────────────

    private void setupLivingPet(LivingEntity living, Player owner, String name, boolean sitting) {
        if (living instanceof Ageable ageable) { ageable.setBaby(); ageable.setAgeLock(true); }
        living.setCustomName(colorize(name));
        living.setCustomNameVisible(true);
        living.setInvulnerable(true);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.setAI(!sitting);
        if (living instanceof Mob mob) { mob.setTarget(null); mob.setAware(true); }
        if (living instanceof Hoglin h)  h.setImmuneToZombification(true);
        if (living instanceof Piglin p)  p.setImmuneToZombification(true);
        if (living instanceof Strider s) s.setShivering(false);
        if (living instanceof Ocelot o)  o.setTrusting(true);
        if (living instanceof Cat cat)   { cat.setTamed(true); cat.setOwner(owner); }
        living.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
    }

    public void applyBiomeVariant(LivingEntity entity, String biome) {
        if (biome == null) biome = "Plains";
        if (entity instanceof Wolf wolf)     wolf.setVariant(getWolfVariant(biome));
        else if (entity instanceof Fox fox)  fox.setFoxType(getFoxType(biome));
        else if (entity instanceof Frog frog) frog.setVariant(getFrogVariant(biome));
        else if (entity instanceof Rabbit rabbit) rabbit.setRabbitType(getRabbitType(biome));
    }

    private Wolf.Variant getWolfVariant(String biome) {
        return switch (biome) {
            case "Forest", "Birch Forest"        -> Wolf.Variant.WOODS;
            case "Dark Forest"                   -> Wolf.Variant.BLACK;
            case "Taiga"                         -> Wolf.Variant.ASHEN;
            case "Cherry Grove"                  -> Wolf.Variant.CHESTNUT;
            case "Snowy Plains", "Frozen Ocean"  -> Wolf.Variant.SNOWY;
            case "Savanna"                       -> Wolf.Variant.SPOTTED;
            case "Badlands"                      -> Wolf.Variant.STRIPED;
            case "Jungle", "Desert", "Crimson Forest" -> Wolf.Variant.RUSTY;
            default                              -> Wolf.Variant.PALE;
        };
    }

    private Fox.Type getFoxType(String biome) {
        return switch (biome) {
            case "Snowy Plains", "Frozen Ocean", "Taiga", "Soul Sand Valley" -> Fox.Type.SNOW;
            default -> Fox.Type.RED;
        };
    }

    private Frog.Variant getFrogVariant(String biome) {
        return switch (biome) {
            case "Desert", "Savanna", "Badlands", "Jungle",
                 "Crimson Forest", "Nether Wastes"  -> Frog.Variant.WARM;
            case "Snowy Plains", "Frozen Ocean",
                 "Soul Sand Valley", "The End"       -> Frog.Variant.COLD;
            default                                  -> Frog.Variant.TEMPERATE;
        };
    }

    private Rabbit.Type getRabbitType(String biome) {
        return switch (biome) {
            case "Desert", "Badlands", "Savanna"          -> Rabbit.Type.GOLD;
            case "Snowy Plains", "Frozen Ocean"           -> Rabbit.Type.WHITE;
            case "Dark Forest", "Nether Wastes", "The End" -> Rabbit.Type.BLACK;
            case "Forest", "Birch Forest"                 -> Rabbit.Type.BROWN;
            default                                       -> Rabbit.Type.SALT_AND_PEPPER;
        };
    }
}
