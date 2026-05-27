package net.legacy.babypets.storage;

import net.legacy.babypets.BabyPetsPlugin;

public class StorageManager {

    private final MySqlStorage mysql;

    public StorageManager(BabyPetsPlugin plugin) {
        this.mysql = new MySqlStorage(plugin);
    }

    public MySqlStorage getMysql() {
        return mysql;
    }
}