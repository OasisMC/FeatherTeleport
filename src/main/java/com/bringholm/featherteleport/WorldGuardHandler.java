package com.bringholm.featherteleport;

import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

class WorldGuardHandler {

    private static final StateFlag ALLOW_FLAG = new StateFlag("allow-feather-teleport", true);
    private FeatherTeleport plugin;
    private FlagConflictException flagConflictException = null;

    WorldGuardHandler(FeatherTeleport plugin) {
        this.plugin = plugin;
    }

    void registerFlag() {
        FlagRegistry flagRegistry =  WorldGuardPlugin.inst().getFlagRegistry();
        try {
            flagRegistry.register(ALLOW_FLAG);
        } catch (FlagConflictException e) {
            flagConflictException = e;
            return;
        }
        plugin.isWGEnabled = true;
    }

    void printOnEnableMessages() {
        if (flagConflictException != null) {
            plugin.getLogger().warning(plugin.i18n.tr("worldguard.flag.failed").replace("%error%", flagConflictException.toString()));
        }
    }

    boolean checkRegionAccess(Player player, Location location) {
        WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
        RegionQuery regionQuery = worldGuard.getRegionContainer().createQuery();
        StateFlag.State queryResult = regionQuery.queryState(location, player, ALLOW_FLAG);
        return queryResult == StateFlag.State.ALLOW;
    }

    String getDenyMessage(Player player, Location location) {
        WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
        RegionQuery regionQuery = worldGuard.getRegionContainer().createQuery();
        return regionQuery.queryValue(location, player, DefaultFlag.DENY_MESSAGE);
    }
}
