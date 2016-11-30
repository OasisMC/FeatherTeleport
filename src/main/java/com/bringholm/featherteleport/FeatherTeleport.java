package com.bringholm.featherteleport;

import com.bringholm.featherteleport.bukkitutils.BukkitReflectionUtils;
import com.bringholm.featherteleport.bukkitutils.ConfigurationHandler;
import com.bringholm.featherteleport.bukkitutils.ConfigurationUtils;
import com.google.common.collect.Lists;
import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static org.bukkit.ChatColor.*;

public class FeatherTeleport extends JavaPlugin implements Listener {
    private HashMap<UUID, List<Entity>> selectedMobs = new HashMap<>();
    private HashMap<UUID, List<UUID>> privateAnimals = new HashMap<>();
    private List<ChunkID> chunkList = new ArrayList<>();
    private boolean isWGEnabled = false;
    private boolean recentEntityEvent = false;
    private static StateFlag allowFlag = new StateFlag("allow-feather-teleport", true);

    @Override
    public void onEnable() {
        loadAnimalData();
        this.getServer().getPluginManager().registerEvents(this, this);
        if (!this.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            this.getLogger().warning("WorldGuard is not enabled. Region flags using WorldGuard will not work!");
        } else {
            List<Flag> newFlagList = Lists.newArrayList(DefaultFlag.getFlags());
            newFlagList.add(allowFlag);
            Flag<?>[] newFlagArray = Arrays.copyOf(newFlagList.toArray(), newFlagList.toArray().length, Flag[].class);
            try {
                BukkitReflectionUtils.modifyFinalField(DefaultFlag.class.getField("flagsList"), null, newFlagArray);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            isWGEnabled = true;
        }
    }

    @Override
    public void onDisable() {
        saveAnimalData();
    }

    @EventHandler
    public void onEntityRightClick(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().hasPermission("featherteleport.use")) {
            return;
        }
        if (e.getHand() == EquipmentSlot.HAND) {
            if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FEATHER) {
                if (e.getRightClicked() instanceof Animals) {
                    if (isWGEnabled) {
                        WorldGuardPlugin worldGuard = (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
                        RegionQuery regionQuery = worldGuard.getRegionContainer().createQuery();
                        StateFlag.State queryResult = regionQuery.queryState(e.getRightClicked().getLocation(), e.getPlayer(), allowFlag);
                        if (queryResult == StateFlag.State.DENY && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                            regionQuery = worldGuard.getRegionContainer().createQuery();
                            e.getPlayer().sendMessage(regionQuery.queryValue(e.getRightClicked().getLocation(), e.getPlayer(), DefaultFlag.DENY_MESSAGE).replace("%what%", "teleport that entity"));
                            return;
                        }
                    }
                    if (!canPlayerTeleportAnimal(e.getPlayer(), e.getRightClicked()) && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                        e.getPlayer().sendMessage(RED + "Someone else owns this animal, so you cannot teleport it!");
                        return;
                    }
                    if (!selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                        selectedMobs.put(e.getPlayer().getUniqueId(), new ArrayList<>());
                    }
                    ChunkID chunk = new ChunkID(e.getRightClicked().getLocation().getChunk());
                    if (selectedMobs.get(e.getPlayer().getUniqueId()).contains(e.getRightClicked())) {
                        selectedMobs.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked());
                        e.getPlayer().sendMessage(GOLD + "Deselected " + WordUtils.capitalizeFully(e.getRightClicked().getType().toString()) + " for teleporting!");
                        if (selectedMobs.get(e.getPlayer().getUniqueId()).size() == 0) {
                            selectedMobs.remove(e.getPlayer().getUniqueId());
                            chunkList.remove(chunk);
                        }
                        return;
                    }
                    if (selectedMobs.get(e.getPlayer().getUniqueId()).size() == 10) {
                        e.getPlayer().sendMessage(RED + "You can only select 10 animals at once!");
                        return;
                    }
                    selectedMobs.get(e.getPlayer().getUniqueId()).add(e.getRightClicked());
                    chunkList.add(chunk);
                    e.getPlayer().sendMessage(GOLD + "Selected " + WordUtils.capitalizeFully(e.getRightClicked().getType().toString()) + " to teleport!");
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                                return;
                            }
                            chunkList.remove(chunk);
                            selectedMobs.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked());
                            e.getPlayer().sendMessage(RED + "You waited to long to teleport " + WordUtils.capitalizeFully(e.getRightClicked().getType().toString()) + ", so it has been deselected.");
                        }
                    }.runTaskLater(this, 1200L);
                    recentEntityEvent = true;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            recentEntityEvent = false;
                        }
                    }.runTask(this);
                }
            } else if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.STICK) {
                ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    if (item.getItemMeta().getDisplayName().equalsIgnoreCase("Private")) {
                        if (!isAnimalPrivate(e.getRightClicked())) {
                            if (e.getRightClicked() instanceof Animals) {
                                privateAnimal(e.getPlayer(), e.getRightClicked());
                                e.getPlayer().sendMessage(GOLD + "This Animal is now yours!");
                            }
                        } else {
                            if (!doesPlayerOwnAnimal(e.getPlayer(), e.getRightClicked())) {
                                e.getPlayer().sendMessage(RED + "This Animal is already owned!");
                            } else {
                                e.getPlayer().sendMessage(GOLD + "This Animal is no longer yours!");
                                privateAnimals.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked().getUniqueId());
                            }
                        }
                    }
                } else if (item.getItemMeta().getDisplayName().equalsIgnoreCase("Staffunprivate") && e.getPlayer().hasPermission("featherteleport.bypass")) {
                    if (e.getRightClicked() instanceof Animals) {
                        if (isAnimalPrivate(e.getRightClicked())) {
                            for (HashMap.Entry<UUID, List<UUID>> entry : privateAnimals.entrySet()) {
                                if (entry.getValue().contains(e.getRightClicked().getUniqueId())) {
                                    entry.getValue().remove(e.getRightClicked().getUniqueId());
                                    e.getPlayer().sendMessage(GOLD + "Successfully unprivated " + WordUtils.capitalizeFully(e.getRightClicked().getType().toString()) + "!");
                                    return;
                                }
                            }
                        } else {
                            e.getPlayer().sendMessage(RED + "This Animal isn't private!");
                        }
                    }
                }
            }
        }
    }


    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getItem() == null || recentEntityEvent) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND && e.getItem().getType() == Material.FEATHER) {
            if (selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                Location location = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
                if (isWGEnabled) {
                    WorldGuardPlugin worldGuard = (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
                    RegionQuery regionQuery = worldGuard.getRegionContainer().createQuery();
                    StateFlag.State queryResult = regionQuery.queryState(location, e.getPlayer(), allowFlag);
                    if (queryResult == StateFlag.State.DENY && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                        regionQuery = worldGuard.getRegionContainer().createQuery();
                        e.getPlayer().sendMessage(regionQuery.queryValue(location, e.getPlayer(), DefaultFlag.DENY_MESSAGE).replace("%what%", "teleport that entity"));
                        return;
                    }
                }
                if (!location.getBlock().getType().isSolid()) {
                    int count = 0;
                    for (Entity entity : selectedMobs.get(e.getPlayer().getUniqueId())) {
                        if (!entity.getLocation().getWorld().equals(e.getPlayer().getWorld())) {
                            e.getPlayer().sendMessage(RED + "Cannot teleport entity " + WordUtils.capitalizeFully(entity.getType().toString()) + " because it is in another world!");
                            continue;
                        }
                        ChunkID chunk = new ChunkID(entity.getLocation().getChunk());
                        entity.teleport(location);
                        chunkList.remove(chunk);
                        count++;
                    }
                    selectedMobs.remove(e.getPlayer().getUniqueId());
                    e.getPlayer().sendMessage(GOLD + "Teleported " + count + " entit" + (count == 1 ? "y" : "ies") + "!");
                } else {
                    e.getPlayer().sendMessage(RED + "Unsafe location to teleport mob to!");
                }
            }
        }
    }

    private boolean canPlayerTeleportAnimal(Player player, Entity entity) {
        return !isAnimalPrivate(entity) || doesPlayerOwnAnimal(player, entity);
    }

    private boolean doesPlayerOwnAnimal(Player player, Entity entity) {
        return privateAnimals.containsKey(player.getUniqueId()) && privateAnimals.get(player.getUniqueId()).contains(entity.getUniqueId());
    }

    private boolean isAnimalPrivate(Entity entity) {
        UUID uuid = entity.getUniqueId();
        for (HashMap.Entry<UUID, List<UUID>> entry : privateAnimals.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    private void privateAnimal(Player player, Entity entity) {
        if (privateAnimals.containsKey(player.getUniqueId())) {
            privateAnimals.get(player.getUniqueId()).add(entity.getUniqueId());
        } else {
            privateAnimals.put(player.getUniqueId(), new ArrayList<>());
            privateAnimals.get(player.getUniqueId()).add(entity.getUniqueId());
        }
    }

    private void saveAnimalData() {
        ConfigurationHandler config = new ConfigurationHandler("privateAnimals.yml", this);
        for (HashMap.Entry<UUID, List<UUID>> entry : privateAnimals.entrySet()) {
            ConfigurationUtils.saveUUIDList(config.getConfig(), entry.getValue(), entry.getKey().toString());
        }
        config.saveConfig();
    }

    private void loadAnimalData() {
        privateAnimals.clear();
        ConfigurationHandler config = new ConfigurationHandler("privateAnimals.yml", this);
        for (String string : config.getConfig().getKeys(false)) {
            privateAnimals.put(UUID.fromString(string), ConfigurationUtils.getUUIDList(config.getConfig(), string));
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (chunkList.contains(new ChunkID(e.getChunk()))) {
            e.setCancelled(true);
        }
    }

    private class ChunkID {
        int x, z;

        ChunkID(Chunk chunk) {
            this.x = chunk.getX();
            this.z = chunk.getZ();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof ChunkID)) {
                return false;
            } else {
                ChunkID chunkID = (ChunkID) o;
                return chunkID.x == this.x && chunkID.z == this.z;
            }
        }
    }
}
