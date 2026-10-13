package art.arcane.iris.fixture;

import art.arcane.iris.Iris;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.iris.world.runtime.RuntimeInjection;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.world.task.J;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.WorldCreatorCompat;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Container;
import org.bukkit.loot.Lootable;
import org.bukkit.loot.LootTable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;
import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeOptions;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.logging.Level;

public final class NativeLifecycleFixture extends JavaPlugin implements WorldRuntimeExecution {
    private static final NamespacedKey WORLD_KEY = NamespacedKey.minecraft("native_lifecycle_probe");
    private static final NamespacedKey TERRAIN_KEY = new NamespacedKey("iris", "native_terrain_probe");
    private static final NamespacedKey COMMAND_KEY = new NamespacedKey("iris", "lifecycle_probe");
    private static final NamespacedKey TREASURE_KEY = new NamespacedKey("iris", "treasure_map_probe");
    private static final long WORLD_SEED = 78264193L;
    private NativeWorldRuntime runtime;
    private TreasureLootChecks treasureLootChecks;

    @Override
    public void onEnable() {
        runtime = NativeAdapters.require(NativeWorldRuntime.class);
        treasureLootChecks = new TreasureLootChecks(this);
        NativeTerrainAccess terrain = NativeAdapters.require(NativeTerrainAccess.class);
        getLogger().info("Native lifecycle ready: " + runtime.description()
                + "; terrain provider=" + terrain.getClass().getName());
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new ChunkGenerator() {};
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length == 0) {
            sender.sendMessage("Use /irislifecycle create|unload|status|structures|terrain-create|terrain-check|terrain-unload|lifecycle-status|lifecycle-entry");
            return true;
        }
        try {
            switch (arguments[0]) {
                case "treasure-map-create" -> createTreasureMapWorld(sender);
                case "treasure-map-limit" -> runTreasureRegion(sender, () -> checkTreasureMapLimit(sender));
                case "treasure-map-chest" -> {
                    if (arguments.length != 3) {
                        throw new IllegalArgumentException("Use treasure-map-chest <destination> <CHEST|COPPER_CHEST>");
                    }
                    runTreasureRegion(sender, () -> prepareTreasureMapChest(sender, arguments[1], arguments[2]));
                }
                case "treasure-map-inspect" -> {
                    if (arguments.length != 2) {
                        throw new IllegalArgumentException("Use treasure-map-inspect <destination>");
                    }
                    runTreasureRegion(sender, () -> inspectTreasureMapChest(sender, arguments[1]));
                }
                case "treasure-map-check" -> runTreasureRegion(sender, () -> verifyTreasureMapLoot(sender));
                case "treasure-extra-check" -> runTreasureRegion(sender, () -> treasureLootChecks.verify(sender));
                case "treasure-region-check" -> runTreasureRegion(sender, () -> treasureLootChecks.verifyRegion(sender));
                case "treasure-reload-before" -> runTreasureRegion(sender, () -> treasureLootChecks.verifyReload(sender, false));
                case "treasure-reload-after" -> runTreasureRegion(sender, () -> treasureLootChecks.verifyReload(sender, true));
                case "treasure-resources" -> treasureLootChecks.reportResourceReloads(sender);
                case "create" -> create(sender);
                case "unload" -> unload(sender);
                case "structures" -> structures(sender);
                case "terrain-create" -> createTerrain(sender);
                case "terrain-check" -> checkTerrain(sender);
                case "terrain-unload" -> unloadTerrain(sender);
                case "lifecycle-status" -> lifecycleStatus(sender);
                case "lifecycle-entry" -> lifecycleEntry(sender);
                case "status" -> sender.sendMessage("NATIVE_STATUS available=" + runtime.available()
                        + " loaded=" + (Bukkit.getWorld(WORLD_KEY) != null));
                default -> throw new IllegalArgumentException("Unknown lifecycle operation");
            }
        } catch (Throwable failure) {
            fail(sender, failure);
        }
        return true;
    }

    private void createTreasureMapWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalStateException("Treasure map fixture requires a player");
        }
        runTreasureRegion(sender, () -> prepareTreasureMapWorld(player));
    }

    private void runTreasureRegion(CommandSender sender, Runnable task) {
        ensureTreasureMapWorld()
                .thenCompose(ignored -> J.runRegionFuture(Bukkit.getWorld(TREASURE_KEY), 0, 0, task))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        fail(sender, failure);
                    }
                });
    }

    private CompletableFuture<?> ensureTreasureMapWorld() {
        if (Bukkit.getWorld(TREASURE_KEY) != null) {
            return CompletableFuture.completedFuture(null);
        }
        if (WorldCreatorCompat.persistentLevelRoot(TREASURE_KEY).exists()) {
            return Iris.instance.worldReconciler().loadWorld(ServerProperties.BUKKIT_YML, TREASURE_KEY.toString());
        }
        return runAsync(() -> {
            try {
                new IrisCreator().name(TREASURE_KEY.toString()).dimension("treasure-map-fixture")
                        .seed(WORLD_SEED).create();
            } catch (Exception failure) {
                throw new IllegalStateException("Treasure map fixture creation failed", failure);
            }
        });
    }

    private void prepareTreasureMapWorld(Player player) {
        World world = Bukkit.getWorld(TREASURE_KEY);
        if (world == null || !IrisToolbelt.isIrisWorld(world) || world.getSeed() != WORLD_SEED
                || !"treasure-map-fixture".equals(IrisToolbelt.access(world).getEngine().getDimension().getLoadKey())) {
            throw new IllegalStateException("Treasure map fixture world identity is invalid");
        }
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                world.getBlockAt(x, 160, z).setType(Material.STONE, false);
                for (int y = 161; y <= 164; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        if (!J.runEntity(player, () -> player.teleportAsync(new Location(world, 0.5, 161, 0.5)).whenComplete((teleported, failure) -> {
            if (failure != null) {
                fail(player, failure);
            } else if (!Boolean.TRUE.equals(teleported)) {
                fail(player, new IllegalStateException("Treasure map fixture teleport failed"));
            } else {
                player.sendMessage("TREASURE_MAP_READY world=" + TREASURE_KEY
                        + " version=" + Bukkit.getMinecraftVersion() + " folia=" + J.isFolia());
            }
        }))) {
            throw new IllegalStateException("Could not schedule treasure map fixture teleport");
        }
    }

    private void checkTreasureMapLimit(CommandSender sender) {
        World world = Bukkit.getWorld(TREASURE_KEY);
        if (world == null) {
            throw new IllegalStateException("Treasure map fixture world is absent");
        }
        Engine engine = IrisToolbelt.access(world).getEngine();
        IrisStructureLocator.LocateResult located = IrisStructureLocator.locateInPlacementRings(
                engine, "minecraft:buried_treasure", 2, 2, 50, (x, z) -> true);
        if (located.status() != IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
            throw new IllegalStateException("Blocked treasure placement returned " + located.status());
        }
        sender.sendMessage("TREASURE_MAP_LIMIT status=" + located.status());
    }

    private void prepareTreasureMapChest(CommandSender sender, String destination, String material) {
        World world = Bukkit.getWorld(TREASURE_KEY);
        treasureMapChest(world, destination, material);
        sender.sendMessage("TREASURE_MAP_CHEST destination=" + destination + " material=" + material);
    }

    private Container treasureMapChest(World world, String destination, String material) {
        requireTreasureDestination(destination);
        if (!List.of("CHEST", "COPPER_CHEST").contains(material)) {
            throw new IllegalArgumentException("Unknown treasure map container: " + material);
        }
        LootTable loot = Bukkit.getLootTable(new NamespacedKey("iris_qa", destination));
        if (loot == null) {
            throw new IllegalStateException("Treasure map loot table is absent: " + destination);
        }
        world.getBlockAt(2, 161, 2).setType(Material.AIR, false);
        world.getBlockAt(2, 161, 2).setType(Material.valueOf(material), false);
        Container chest = (Container) world.getBlockAt(2, 161, 2).getState();
        ((Lootable) chest).setLootTable(loot);
        ((Lootable) chest).setSeed(WORLD_SEED);
        if (!chest.update(true, false)) {
            throw new IllegalStateException("Treasure map loot chest was not installed");
        }
        return (Container) world.getBlockAt(2, 161, 2).getState();
    }

    private void verifyTreasureMapLoot(CommandSender sender) {
        checkTreasureMapLimit(sender);
        World world = Bukkit.getWorld(TREASURE_KEY);
        for (String destination : List.of("blocked", "disabled", "found", "mixed")) {
            Inventory inventory = treasureMapChest(world, destination, "CHEST").getInventory();
            inspectTreasureMapChest(sender, destination);
            inventory.remove(Material.DIAMOND);
            Inventory reopened = ((Container) world.getBlockAt(2, 161, 2).getState()).getInventory();
            if (reopened.contains(Material.DIAMOND)
                    || !(reopened.contains(Material.FILLED_MAP) || reopened.contains(Material.MAP))) {
                throw new IllegalStateException("Reopening changed " + destination + " chest loot");
            }
        }
        sender.sendMessage("TREASURE_MAP_CHECK destinations=4 retained=true");
    }

    private void inspectTreasureMapChest(CommandSender sender, String destination) {
        requireTreasureDestination(destination);
        World world = Bukkit.getWorld(TREASURE_KEY);
        Inventory inventory = ((Container) world.getBlockAt(2, 161, 2).getState()).getInventory();
        ItemStack map = null;
        int diamonds = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item == null) {
                continue;
            }
            if ((item.getType() == Material.MAP || item.getType() == Material.FILLED_MAP)
                    && item.getAmount() == 1 && map == null) {
                map = item;
            } else if (item.getType() == Material.DIAMOND) {
                diamonds += item.getAmount();
            } else {
                throw new IllegalStateException("Unexpected chest loot: " + item);
            }
        }
        if (map == null || diamonds != 1) {
            throw new IllegalStateException("Invalid " + destination + " chest: map=" + map + " diamonds=" + diamonds);
        }
        boolean expectedLocated = destination.equals("found") || destination.equals("mixed");
        MapView view = map.getItemMeta() instanceof MapMeta meta && meta.hasMapView() ? meta.getMapView() : null;
        if ((view != null) != expectedLocated) {
            throw new IllegalStateException("Unexpected map data for " + destination + ": " + map);
        }
        String result = "TREASURE_MAP_INSPECT destination=" + destination + " diamonds=1 located=" + expectedLocated;
        if (view != null) {
            StructureSearchResult target = world.locateNearestStructure(
                    new Location(world, 2, 161, 2), Structure.DESERT_PYRAMID, 50, false);
            if (target == null || target.getStructure() != Structure.DESERT_PYRAMID) {
                throw new IllegalStateException("Map has no verified pyramid target");
            }
            int targetX = target.getLocation().getBlockX();
            int targetZ = target.getLocation().getBlockZ();
            if (view.getWorld() != world || view.getScale() != MapView.Scale.CLOSEST
                    || view.getCenterX() != Math.floorDiv(targetX + 64, 128) * 128
                    || view.getCenterZ() != Math.floorDiv(targetZ + 64, 128) * 128) {
                throw new IllegalStateException("Map data does not cover the verified pyramid at " + target.getLocation());
            }
            result += " mapId=" + view.getId() + " targetX=" + targetX + " targetZ=" + targetZ
                    + " centerX=" + view.getCenterX() + " centerZ=" + view.getCenterZ();
        }
        sender.sendMessage(result);
    }

    private void requireTreasureDestination(String destination) {
        if (!List.of("blocked", "disabled", "found", "mixed").contains(destination)) {
            throw new IllegalArgumentException("Unknown treasure map destination: " + destination);
        }
    }

    private void lifecycleStatus(CommandSender sender) throws Exception {
        World world = Bukkit.getWorld(COMMAND_KEY);
        Path storage = WorldCreatorCompat.persistentLevelRoot(COMMAND_KEY).toPath();
        Path levelRoot = IrisWorldStorage.levelRoot().toPath();
        Path registry = levelRoot.resolve("iris/worlds.json");
        boolean registered = Files.exists(registry)
                && Files.readString(registry).contains("\"" + COMMAND_KEY + "\"");
        String startupName = IrisWorldStorage.configuredWorldName(COMMAND_KEY, levelRoot.getFileName().toString());
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        boolean configured = configuration.getConfigurationSection("worlds") != null
                && configuration.getConfigurationSection("worlds").contains(startupName);
        File quarantineRoot = levelRoot.resolve("dimensions/iris").toFile();
        File[] quarantines = quarantineRoot.listFiles((directory, name) -> name.startsWith(".iris-delete-"));
        int quarantineCount = quarantines == null ? 0 : quarantines.length;
        Path queueFile = Bukkit.getPluginManager().getPlugin("Iris").getDataFolder().toPath()
                .resolve("pending-world-deletes.txt");
        List<String> queue = Files.exists(queueFile) ? Files.readAllLines(queueFile) : List.of();
        boolean queued = quarantineCount > 0;
        if (quarantines != null) {
            for (File quarantine : quarantines) {
                queued &= queue.contains(quarantine.getName());
            }
        }
        boolean disk = Files.exists(storage, LinkOption.NOFOLLOW_LINKS);
        String cleanup = disk ? "present" : quarantineCount == 0 ? "deleted" : queued ? "queued" : "unsafe";
        boolean idle = true;
        LifecycleOperationCoordinator coordinator = LifecycleOperationCoordinator.get();
        for (LifecycleOperationCoordinator.Domain domain : LifecycleOperationCoordinator.Domain.values()) {
            idle &= coordinator.active(domain).isEmpty();
        }
        sender.sendMessage("IRIS_LIFECYCLE loaded=" + (world != null) + " registered=" + registered
                + " configured=" + configured + " disk=" + disk + " quarantines=" + quarantineCount
                + " cleanup=" + cleanup + " idle=" + idle);
    }

    private void lifecycleEntry(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Lifecycle entry requires a player");
        }
        World world = Bukkit.getWorld(COMMAND_KEY);
        if (world == null || player.getWorld() != world || world.getSeed() != WORLD_SEED
                || !IrisToolbelt.isIrisWorld(world)) {
            throw new IllegalStateException("Player did not enter the requested seeded Iris world");
        }
        Location feet = player.getLocation();
        Material floor = world.getBlockAt(feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ()).getType();
        if (floor != Material.LIME_WOOL || !floor.isSolid()
                || !world.getBlockAt(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ()).isPassable()
                || !world.getBlockAt(feet.getBlockX(), feet.getBlockY() + 1, feet.getBlockZ()).isPassable()) {
            throw new IllegalStateException("Lifecycle player entry lacks a safe fixture floor");
        }
        sender.sendMessage("IRIS_LIFECYCLE_ENTRY world=" + COMMAND_KEY + " seed=" + world.getSeed()
                + " floor=lime_wool safe=true");
    }

    private void create(CommandSender sender) throws ReflectiveOperationException {
        if (!runtime.available() || Bukkit.getWorld(WORLD_KEY) != null) {
            throw new IllegalStateException("Native runtime unavailable or probe world already loaded");
        }
        RuntimeInjection.installIfDeferred();
        WorldRuntimeOptions options = new WorldRuntimeOptions(WORLD_KEY.getKey(), WORLD_KEY,
                World.Environment.NORMAL, null, getName() + ":runtime", false, WORLD_SEED, false,
                Bukkit.getWorlds().getFirst().getWorldFolder());
        World world = runtime.create(options, this);
        if (!WORLD_KEY.equals(world.getKey()) || world.getSeed() != WORLD_SEED) {
            throw new IllegalStateException("World identity or seed differs from requested options");
        }
        world.getChunkAtAsync(0, 0, true).whenComplete((chunk, failure) -> runGlobal(() -> {
            if (failure != null) {
                fail(sender, failure);
                return;
            }
            if (chunk == null || chunk.getWorld() != world || !chunk.isLoaded()) {
                fail(sender, new IllegalStateException("Native runtime world failed chunk generation"));
                return;
            }
            sender.sendMessage("NATIVE_CREATED seed=" + world.getSeed() + " chunk=true");
        }));
    }

    private void createTerrain(CommandSender sender) {
        if (Bukkit.getWorld(TERRAIN_KEY) != null) {
            throw new IllegalStateException("Terrain world is already loaded");
        }
        runAsync(() -> {
            try {
                new IrisCreator().name(TERRAIN_KEY.toString()).dimension("native-terrain")
                        .seed(WORLD_SEED).create();
                runGlobal(() -> checkTerrain(sender));
            } catch (Throwable failure) {
                runGlobal(() -> fail(sender, failure));
            }
        });
    }

    private void checkTerrain(CommandSender sender) {
        World world = Bukkit.getWorld(TERRAIN_KEY);
        if (world == null || world.getSeed() != WORLD_SEED || !IrisToolbelt.isIrisWorld(world)
                || IrisToolbelt.access(world).getEngine() == null) {
            throw new IllegalStateException("Iris terrain engine or world identity is absent");
        }
        world.getChunkAtAsync(2, -3, true).whenComplete((chunk, failure) -> runGlobal(() -> {
            if (failure != null) {
                fail(sender, failure);
                return;
            }
            try {
                int columns = 0;
                for (int x = 32; x < 48; x += 5) {
                    for (int z = -48; z < -32; z += 5) {
                        int y = world.getHighestBlockYAt(x, z);
                        if (y < 90 || y > 100 || world.getBlockAt(x, y, z).getType() != Material.LIME_WOOL
                                || world.getBlockAt(x, y - 3, z).getType() != Material.GREEN_CONCRETE
                                || world.getBlockAt(x, world.getMinHeight(), z).getType() != Material.GREEN_CONCRETE
                                || world.getBiome(x, y, z) != Biome.PLAINS) {
                            throw new IllegalStateException("Terrain column differs at " + x + "," + y + "," + z
                                    + ": " + world.getBlockAt(x, y, z).getType());
                        }
                        columns++;
                    }
                }
                sender.sendMessage("NATIVE_TERRAIN seed=" + world.getSeed() + " columns=" + columns + " biome=plains");
            } catch (Throwable assertion) {
                fail(sender, assertion);
            }
        }));
    }

    private void unloadTerrain(CommandSender sender) {
        World world = Bukkit.getWorld(TERRAIN_KEY);
        if (world == null) {
            throw new IllegalStateException("Terrain world is absent");
        }
        runtime.unload(world, true, this).whenComplete((unloaded, failure) -> runGlobal(() -> {
            if (failure != null) {
                fail(sender, failure);
            } else if (!Boolean.TRUE.equals(unloaded) || Bukkit.getWorld(TERRAIN_KEY) != null) {
                fail(sender, new IllegalStateException("Terrain world did not unload"));
            } else {
                sender.sendMessage("NATIVE_TERRAIN_UNLOADED true");
            }
        }));
    }

    private void structures(CommandSender sender) throws Exception {
        NativeStructureReader reader = NativeAdapters.require(NativeStructureReader.class);
        List<String> templates = reader.templates();
        if (!templates.contains("minecraft:village/plains/town_centers/plains_fountain_01")) {
            throw new IllegalStateException("Vanilla village template was not enumerated");
        }
        NativeStructureReader.Session session = reader.open();
        NativeStructureReader.Structure village = session.structure("minecraft:village_plains");
        if (village == null || !village.jigsaw() || village.maxDepth() <= 0
                || village.maxDistanceFromCenter() <= 0) {
            throw new IllegalStateException("Vanilla village metadata is missing");
        }
        NativeStructureReader.Pool pool = session.pool(village.startPoolKey());
        if (pool == null || !"minecraft:empty".equals(pool.fallbackKey()) || pool.entries().isEmpty()) {
            throw new IllegalStateException("Vanilla village start pool is missing");
        }
        int connectorCount = 0;
        for (NativeStructureReader.Entry entry : pool.entries()) {
            NativeStructureReader.Element element = entry.element();
            if (entry.weight() <= 0 || element == null || element.templateLocation() == null) {
                throw new IllegalStateException("Vanilla village pool element is invalid");
            }
            NativeStructureReader.ConnectorSet connectors = element.connectors(() -> 91L);
            if (connectors.status() != NativeStructureReader.ConnectorStatus.AVAILABLE) {
                throw new IllegalStateException("Vanilla village connectors unavailable");
            }
            for (NativeStructureReader.Connector connector : connectors.connectors()) {
                NativeStructureReader.ConnectorData data = connector.read();
                if (data.front() == null || data.top() == null || data.pool() == null
                        || data.metadata().finalState() == null) {
                    throw new IllegalStateException("Vanilla village connector metadata is incomplete");
                }
                connectorCount++;
            }
        }
        if (connectorCount == 0) {
            throw new IllegalStateException("Vanilla village pool has no connectors");
        }
        sender.sendMessage("NATIVE_STRUCTURES templates=" + templates.size() + " village=true connectors=" + connectorCount);
    }

    private void unload(CommandSender sender) {
        World world = Bukkit.getWorld(WORLD_KEY);
        if (world == null) {
            throw new IllegalStateException("Probe world is absent");
        }
        runtime.unload(world, true, this).whenComplete((unloaded, failure) -> runGlobal(() -> {
            if (failure != null) {
                fail(sender, failure);
            } else if (!Boolean.TRUE.equals(unloaded) || Bukkit.getWorld(WORLD_KEY) != null) {
                fail(sender, new IllegalStateException("Native unload did not remove the world"));
            } else {
                sender.sendMessage("NATIVE_UNLOADED true");
            }
        }));
    }

    public boolean regionized() {
        return J.isFolia();
    }

    public boolean primaryThread() {
        return J.isFolia() ? Bukkit.isGlobalTickThread() : Bukkit.isPrimaryThread();
    }

    public CompletableFuture<Void> runGlobal(Runnable task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        if (primaryThread()) {
            wrapped.run();
        } else if (!J.runGlobal(wrapped)) {
            result.completeExceptionally(new IllegalStateException("Could not schedule fixture task"));
        }
        return result;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task);
    }

    public void reportFailure(String message, Throwable failure) {
        getLogger().log(Level.SEVERE, message, failure);
    }

    private void fail(CommandSender sender, Throwable failure) {
        reportFailure("Native lifecycle assertion failed", failure);
        sender.sendMessage("NATIVE_FAILED " + failure.getMessage());
    }
}
