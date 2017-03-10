package com.bringholm.featherteleport;

import com.bringholm.featherteleport.bukkitutils.ConfigurationHandler;
import com.bringholm.featherteleport.bukkitutils.ConfigurationUtils;
import com.bringholm.featherteleport.bukkitutils.I18n;
import com.google.common.collect.Lists;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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


public class FeatherTeleport extends JavaPlugin implements Listener {
    private HashMap<UUID, List<Entity>> selectedMobs = new HashMap<>();
    private HashMap<UUID, List<UUID>> privateAnimals = new HashMap<>();
    private HashMap<UUID, List<BukkitRunnable>> bukkitRunnables = new HashMap<>();
    private EnumSet<EntityType> allowedTypes = EnumSet.of(EntityType.VILLAGER);
    private int animalTimeLimit;
    private int animalAmountLimit;
    private List<UUID> recentEntityEventList = new ArrayList<>();
    private List<ChunkID> chunkList = new ArrayList<>();
    private WorldGuardHandler worldGuardHandler;
    boolean isWGEnabled = false;
    I18n i18n;

    @Override
    public void onLoad() {
        if (this.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        worldGuardHandler = new WorldGuardHandler(this);
        worldGuardHandler.registerFlag();
    }

    @Override
    public void onEnable() {
        i18n = new I18n(this, "en_US", "en_US");
        if (!isWGEnabled) {
            this.getLogger().warning(i18n.tr("missing.worldguard"));
        } else {
            worldGuardHandler.printOnEnableMessages();
        }
        reloadConfigValues();
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveAnimalData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(i18n.tr("command.invalid.args"));
        } else {
            reloadConfigValues();
            sender.sendMessage(i18n.tr("command.success"));
        }
        return true;
    }

    @EventHandler
    public void onEntityRightClick(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().hasPermission("featherteleport.use")) {
            return;
        }
        if (e.getHand() == EquipmentSlot.HAND) {
            if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FEATHER) {
                recentEntityEventList.add(e.getPlayer().getUniqueId());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        recentEntityEventList.remove(e.getPlayer().getUniqueId());
                    }
                }.runTask(this);
                if (e.getRightClicked() instanceof Animals || allowedTypes.contains(e.getRightClicked().getType())) {
                    e.setCancelled(true);
                    if (isWGEnabled) {
                        if (!worldGuardHandler.checkRegionAccess(e.getPlayer(), e.getRightClicked().getLocation()) && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                            e.getPlayer().sendMessage(worldGuardHandler.getDenyMessage(e.getPlayer(), e.getRightClicked().getLocation()).replace("%what%", i18n.tr("worldguard.denymessage")));
                            return;
                        }
                    }
                    if (!canPlayerTeleportAnimal(e.getPlayer(), e.getRightClicked()) && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                        e.getPlayer().sendMessage(i18n.tr("animal.other.owner"));
                        return;
                    }
                    if (!selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                        selectedMobs.put(e.getPlayer().getUniqueId(), new ArrayList<>());
                    }
                    ChunkID chunk = new ChunkID(e.getRightClicked().getLocation().getChunk());
                    if (selectedMobs.get(e.getPlayer().getUniqueId()).contains(e.getRightClicked())) {
                        selectedMobs.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked());
                        e.getPlayer().sendMessage(i18n.tr("animal.deselected").replace("%animal%", WordUtils.capitalizeFully(e.getRightClicked().getType().toString())));
                        if (selectedMobs.get(e.getPlayer().getUniqueId()).size() == 0) {
                            selectedMobs.remove(e.getPlayer().getUniqueId());
                            chunkList.remove(chunk);
                        }
                        return;
                    }
                    if (selectedMobs.get(e.getPlayer().getUniqueId()).size() == animalAmountLimit) {
                        e.getPlayer().sendMessage(i18n.tr("animal.selected.limit").replace("%amount%", String.valueOf(animalAmountLimit)));
                        return;
                    }
                    selectedMobs.get(e.getPlayer().getUniqueId()).add(e.getRightClicked());
                    chunkList.add(chunk);
                    e.getPlayer().sendMessage(i18n.tr("animal.selected").replace("%animal%", WordUtils.capitalizeFully(e.getRightClicked().getType().toString())));
                    BukkitRunnable bukkitRunnable = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                                return;
                            }
                            chunkList.remove(chunk);
                            selectedMobs.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked());
                            if (selectedMobs.get(e.getPlayer().getUniqueId()).isEmpty()) {
                                selectedMobs.remove(e.getPlayer().getUniqueId());
                            }
                            e.getPlayer().sendMessage(i18n.tr("animal.timelimit").replace("%animal%", WordUtils.capitalizeFully(e.getRightClicked().getType().toString())));
                            if (bukkitRunnables.containsKey(e.getPlayer().getUniqueId()) && bukkitRunnables.get(e.getPlayer().getUniqueId()).contains(this)) {
                                bukkitRunnables.get(e.getPlayer().getUniqueId()).remove(this);
                            }
                        }
                    };
                    bukkitRunnable.runTaskLater(this, animalTimeLimit * 20);
                    if (!bukkitRunnables.containsKey(e.getPlayer().getUniqueId())) {
                        bukkitRunnables.put(e.getPlayer().getUniqueId(), Lists.newArrayList());
                    }
                    bukkitRunnables.get(e.getPlayer().getUniqueId()).add(bukkitRunnable);
                }
            } else if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.STICK) {
                ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    if (item.getItemMeta().getDisplayName().equalsIgnoreCase("private")) {
                        e.setCancelled(true);
                        if (!isAnimalPrivate(e.getRightClicked())) {
                            if (e.getRightClicked() instanceof Animals || allowedTypes.contains(e.getRightClicked().getType())) {
                                privateAnimal(e.getPlayer(), e.getRightClicked());
                                e.getPlayer().sendMessage(i18n.tr("animal.privated"));
                            }
                        } else {
                            if (!doesPlayerOwnAnimal(e.getPlayer(), e.getRightClicked())) {
                                e.getPlayer().sendMessage(i18n.tr("animal.already.owned"));
                            } else {
                                e.getPlayer().sendMessage(i18n.tr("animal.deprivated"));
                                privateAnimals.get(e.getPlayer().getUniqueId()).remove(e.getRightClicked().getUniqueId());
                            }
                        }
                    }
                } else if (item.getItemMeta().getDisplayName().equalsIgnoreCase("staffunprivate") && e.getPlayer().hasPermission("featherteleport.bypass")) {
                    if (e.getRightClicked() instanceof Animals || allowedTypes.contains(e.getRightClicked().getType())) {
                        e.setCancelled(true);
                        if (isAnimalPrivate(e.getRightClicked())) {
                            for (HashMap.Entry<UUID, List<UUID>> entry : privateAnimals.entrySet()) {
                                if (entry.getValue().contains(e.getRightClicked().getUniqueId())) {
                                    entry.getValue().remove(e.getRightClicked().getUniqueId());
                                    e.getPlayer().sendMessage(i18n.tr("animal.staff.deprivated").replace("%animal%", WordUtils.capitalizeFully(e.getRightClicked().getType().toString())));
                                    return;
                                }
                            }
                        } else {
                            e.getPlayer().sendMessage(i18n.tr("animal.staff.notprivate"));
                        }
                    }
                }
            }
        }
    }


    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getItem() == null || recentEntityEventList.contains(e.getPlayer().getUniqueId())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND && e.getItem().getType() == Material.FEATHER) {
            if (selectedMobs.containsKey(e.getPlayer().getUniqueId())) {
                Location location = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
                if (isWGEnabled) {
                    if (!worldGuardHandler.checkRegionAccess(e.getPlayer(), e.getClickedBlock().getLocation()) && !e.getPlayer().hasPermission("featherteleport.bypass")) {
                        e.getPlayer().sendMessage(worldGuardHandler.getDenyMessage(e.getPlayer(), e.getClickedBlock().getLocation()).replace("%what%", i18n.tr("worldguard.denymessage")));
                        return;
                    }
                }
                if (!location.getBlock().getType().isSolid()) {
                    int count = 0;
                    for (Entity entity : selectedMobs.get(e.getPlayer().getUniqueId())) {
                        ChunkID chunk = new ChunkID(entity.getLocation().getChunk());
                        chunkList.remove(chunk);
                        if (!entity.getLocation().getWorld().equals(e.getPlayer().getWorld())) {
                            e.getPlayer().sendMessage(i18n.tr("animal.inotherworld").replace("%animal%", WordUtils.capitalizeFully(entity.getType().toString())));
                            continue;
                        }
                        entity.teleport(location);
                        count++;
                    }
                    selectedMobs.remove(e.getPlayer().getUniqueId());
                    if (bukkitRunnables.containsKey(e.getPlayer().getUniqueId())) {
                        for (BukkitRunnable bukkitRunnable : bukkitRunnables.get(e.getPlayer().getUniqueId())) {
                            bukkitRunnable.cancel();
                        }
                        bukkitRunnables.get(e.getPlayer().getUniqueId()).clear();
                    }
                    e.getPlayer().sendMessage(count == 1 ? i18n.tr("animal.teleport.singular") : i18n.tr("animal.teleport.plural").replace("%amount%", String.valueOf(count)));
                } else {
                    e.getPlayer().sendMessage(i18n.tr("animal.teleport.unsafe"));
                }
            }
        }
    }

    private void reloadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        i18n.setLanguage(getConfig().getString("language"));
        i18n.reload();
        animalTimeLimit = getConfig().getInt("animal-time-limit");
        animalAmountLimit = getConfig().getInt("animal-amount-limit");
        loadAnimalData();
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
        ConfigurationHandler config = new ConfigurationHandler("private-animals.yml", this);
        for (HashMap.Entry<UUID, List<UUID>> entry : privateAnimals.entrySet()) {
            ConfigurationUtils.saveUUIDList(config.getConfig(), entry.getValue(), entry.getKey().toString());
        }
        config.saveConfig();
    }

    private void loadAnimalData() {
        privateAnimals.clear();
        ConfigurationHandler config = new ConfigurationHandler("private-animals.yml", this);
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
