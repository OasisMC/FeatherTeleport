package com.bringholm.featherteleport.bukkitutils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConfigurationUtils {
    public static void saveUUIDList(FileConfiguration config, List<UUID> uuidList, String path) {
        config.set(path, uuidList.stream().map(UUID::toString).collect(Collectors.toList()));
    }

    public static List<UUID> getUUIDList(FileConfiguration config, String path) {
        return config.getStringList(path).stream().map(UUID::fromString).collect(Collectors.toList());
    }
}
