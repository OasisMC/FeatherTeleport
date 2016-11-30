package com.bringholm.featherteleport.bukkitutils;

import com.bringholm.featherteleport.FeatherTeleport;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigurationHandler {
    private String configName;
    private FeatherTeleport plugin;
    private FileConfiguration config = null;
    private File configFile = null;

    public ConfigurationHandler(String name, FeatherTeleport plugin) {
        this.configName = name;
        this.plugin = plugin;
    }

    public ConfigurationHandler(File file, FeatherTeleport plugin) {
        this.configFile = file;
        this.plugin = plugin;
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    private void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), configName);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        if (configFile == null || config == null) {
            return;
        }
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + configFile);
        }
    }
}
