package art.arcane.iris.fixture;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisVanillaLootTable;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.matter.Matter;
import io.papermc.paper.event.world.StructuresLocateEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StructureSearchResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TreasureLootChecks implements Listener {
    private static final NamespacedKey WORLD_KEY = new NamespacedKey("iris", "treasure_map_probe");
    private static final List<String> CHESTS = List.of(
            "CHEST", "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST",
            "OXIDIZED_COPPER_CHEST", "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST",
            "WAXED_WEATHERED_COPPER_CHEST", "WAXED_OXIDIZED_COPPER_CHEST");
    private final IrisVanillaLootTable reloadTable = new IrisVanillaLootTable("iris_qa:reload");
    private final AtomicInteger resourceReloads = new AtomicInteger();
    private LocateAction locateAction = LocateAction.PASS;
    private int locateEvents;
    private int lootEvents;
    private boolean lootEventsOwned = true;

    public TreasureLootChecks(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void verify(CommandSender sender) {
        World world = requireWorld();
        verifyCopperLoot(world);
        sender.sendMessage("TREASURE_COPPER_LOOT variants=8 normal=true diamonds=1");
        verifyInventoryRollback(world);
        sender.sendMessage("TREASURE_LOOT_ROLLBACK contents=true nativeTable=true seed=true retry=true");
        verifyLocateEvents(world);
        sender.sendMessage("TREASURE_LOCATE_EVENTS cancelled=true replaced=true calls=2");
        sender.sendMessage("TREASURE_EXTRA_CHECK variants=9 rollback=true locateEvents=true");
    }

    public void verifyRegion(CommandSender sender) {
        World world = requireWorld();
        if (!J.isOwnedByCurrentRegion(world, 0, 0)) {
            throw new IllegalStateException("Fixture does not own its loot region");
        }
        lootEvents = 0;
        lootEventsOwned = true;
        verifyCopperLoot(world);
        if (lootEvents != CHESTS.size() || !lootEventsOwned) {
            throw new IllegalStateException("Loot event ownership failed: count=" + lootEvents
                    + " owned=" + lootEventsOwned);
        }
        sender.sendMessage("TREASURE_REGION_LOOT copper=8 normal=1 events=" + lootEvents
                + " owned=true folia=" + J.isFolia());
    }

    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (event.getWorld().getKey().equals(WORLD_KEY) && event.isPlugin()) {
            Location origin = event.getLootContext().getLocation();
            lootEvents++;
            lootEventsOwned &= J.isOwnedByCurrentRegion(event.getWorld(),
                    origin.getBlockX() >> 4, origin.getBlockZ() >> 4);
        }
    }

    public void verifyReload(CommandSender sender, boolean reloaded) {
        World world = requireWorld();
        Material expected = reloaded ? Material.EMERALD : Material.DIAMOND;
        Collection<ItemStack> items = reloadTable.getLoot(
                false, 783L, InventorySlotType.STORAGE, world, 2, 161, 2);
        if (items.size() != 1 || items.iterator().next().getType() != expected) {
            throw new IllegalStateException("Reloaded loot did not contain " + expected + ": " + items);
        }
        sender.sendMessage("TREASURE_RELOAD material=" + expected);
    }

    public void reportResourceReloads(CommandSender sender) {
        sender.sendMessage("TREASURE_RESOURCES revision=" + resourceReloads.get());
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        resourceReloads.incrementAndGet();
    }

    @EventHandler
    public void onLocate(StructuresLocateEvent event) {
        if (!event.getWorld().getKey().equals(WORLD_KEY) || locateAction == LocateAction.PASS) {
            return;
        }
        locateEvents++;
        if (locateAction == LocateAction.CANCEL) {
            event.setCancelled(true);
        } else {
            event.setResult(new StructuresLocateEvent.Result(Position.block(123, 100, 456), Structure.DESERT_PYRAMID));
        }
    }

    private void verifyCopperLoot(World world) {
        Engine engine = IrisToolbelt.access(world).getEngine();
        Block block = world.getBlockAt(4, 161, 4);
        MantleChunk<Matter> mantle = engine.getMantle().getMantle().useChunk(block.getChunk());
        try {
            for (String name : CHESTS) {
                block.setType(Material.AIR, false);
                block.setType(Material.valueOf(name), false);
                if (!BukkitBlockResolution.isStorageChest(block.getBlockData())) {
                    throw new IllegalStateException("Chest is excluded from Iris loot: " + name);
                }
                EngineBukkitOps.update(engine, 4, 161, 4, block.getChunk(), mantle);
                Container chest = (Container) block.getState();
                int diamonds = 0;
                for (ItemStack item : chest.getInventory().getContents()) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getType() != Material.DIAMOND) {
                        throw new IllegalStateException("Unexpected " + name + " loot: " + item);
                    }
                    diamonds += item.getAmount();
                }
                if (diamonds != 1) {
                    throw new IllegalStateException(name + " contains " + diamonds + " diamonds");
                }
            }
        } finally {
            mantle.release();
        }
    }

    private void verifyInventoryRollback(World world) {
        Engine engine = IrisToolbelt.access(world).getEngine();
        Block block = world.getBlockAt(6, 161, 4);
        block.setType(Material.AIR, false);
        block.setType(Material.CHEST, false);
        Chest chest = (Chest) block.getState();
        chest.getBlockInventory().setItem(0, new ItemStack(Material.EMERALD, 3));
        chest = (Chest) block.getState();
        NamespacedKey key = new NamespacedKey("iris_qa", "reload");
        chest.setLootTable(Bukkit.getLootTable(key));
        chest.setSeed(913L);
        if (!chest.update(true, false)) {
            throw new IllegalStateException("Could not set native loot for rollback check");
        }
        Inventory inventory = chest.getBlockInventory();
        IllegalStateException injected = new IllegalStateException("Fixture insertion interruption");
        Inventory interrupted = (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class}, (proxy, method, arguments) -> {
                    Object result;
                    try {
                        result = method.invoke(inventory, arguments);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                    if (method.getName().equals("addItem")) {
                        throw injected;
                    }
                    return result;
                });
        KList<IrisLootTable> tables = new KList<>();
        tables.add(new IrisVanillaLootTable("iris_qa:reload"));
        try {
            EngineBukkitOps.addItems(engine, false, interrupted, tables, InventorySlotType.STORAGE,
                    world, 6, 161, 4);
            throw new IllegalStateException("Interrupted insertion did not fail");
        } catch (IllegalStateException failure) {
            if (failure != injected) {
                throw failure;
            }
        }
        Chest restored = (Chest) block.getState();
        if (restored.getLootTable() == null || !restored.getLootTable().getKey().equals(key)
                || restored.getSeed() != 913L) {
            throw new IllegalStateException("Rollback lost the native loot table or seed");
        }
        ItemStack existing = restored.getSnapshotInventory().getItem(0);
        if (existing == null || existing.getType() != Material.EMERALD || existing.getAmount() != 3) {
            throw new IllegalStateException("Rollback lost the original contents: " + existing);
        }
        EngineBukkitOps.addItems(engine, false, restored.getBlockInventory(), tables, InventorySlotType.STORAGE,
                world, 6, 161, 4);
        int diamonds = 0;
        int emeralds = 0;
        for (ItemStack item : restored.getBlockInventory().getContents()) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.DIAMOND) {
                diamonds += item.getAmount();
            } else if (item.getType() == Material.EMERALD) {
                emeralds += item.getAmount();
            }
        }
        if (diamonds != 2 || emeralds != 3 || ((Chest) block.getState()).getLootTable() != null) {
            throw new IllegalStateException("Loot retry failed: diamonds=" + diamonds + " emeralds=" + emeralds);
        }
    }

    private void verifyLocateEvents(World world) {
        Location origin = new Location(world, 2, 161, 2);
        locateEvents = 0;
        try {
            locateAction = LocateAction.CANCEL;
            if (world.locateNearestStructure(origin, Structure.DESERT_PYRAMID, 50, false) != null) {
                throw new IllegalStateException("Cancelled structure search returned a target");
            }
            locateAction = LocateAction.REPLACE;
            StructureSearchResult result = world.locateNearestStructure(origin, Structure.DESERT_PYRAMID, 50, false);
            if (result == null || result.getStructure() != Structure.DESERT_PYRAMID
                    || result.getLocation().getBlockX() != 123 || result.getLocation().getBlockY() != 100
                    || result.getLocation().getBlockZ() != 456 || locateEvents != 2) {
                throw new IllegalStateException("Structure search ignored event result: " + result);
            }
        } finally {
            locateAction = LocateAction.PASS;
        }
    }

    private World requireWorld() {
        World world = Bukkit.getWorld(WORLD_KEY);
        if (world == null || !IrisToolbelt.isIrisWorld(world)) {
            throw new IllegalStateException("Treasure map fixture world is absent");
        }
        return world;
    }

    private enum LocateAction {
        PASS, CANCEL, REPLACE
    }
}
