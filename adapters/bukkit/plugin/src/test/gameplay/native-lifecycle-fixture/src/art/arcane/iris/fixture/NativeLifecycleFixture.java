package art.arcane.iris.fixture;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.iris.world.runtime.RuntimeInjection;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.world.IrisToolbelt;
import org.bukkit.Material;
import org.bukkit.block.Biome;
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

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.logging.Level;

public final class NativeLifecycleFixture extends JavaPlugin implements WorldRuntimeExecution {
    private static final NamespacedKey WORLD_KEY = NamespacedKey.minecraft("native_lifecycle_probe");
    private static final NamespacedKey TERRAIN_KEY = new NamespacedKey("iris", "native_terrain_probe");
    private static final long WORLD_SEED = 78264193L;
    private NativeWorldRuntime runtime;

    @Override
    public void onEnable() {
        runtime = NativeAdapters.require(NativeWorldRuntime.class);
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
        if (arguments.length != 1) {
            sender.sendMessage("Use /irislifecycle create|unload|status|structures|terrain-create|terrain-check|terrain-unload");
            return true;
        }
        try {
            switch (arguments[0]) {
                case "create" -> create(sender);
                case "unload" -> unload(sender);
                case "structures" -> structures(sender);
                case "terrain-create" -> createTerrain(sender);
                case "terrain-check" -> checkTerrain(sender);
                case "terrain-unload" -> unloadTerrain(sender);
                case "status" -> sender.sendMessage("NATIVE_STATUS available=" + runtime.available()
                        + " loaded=" + (Bukkit.getWorld(WORLD_KEY) != null));
                default -> throw new IllegalArgumentException("Unknown lifecycle operation");
            }
        } catch (Throwable failure) {
            fail(sender, failure);
        }
        return true;
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
        return false;
    }

    public boolean primaryThread() {
        return Bukkit.isPrimaryThread();
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
        if (Bukkit.isPrimaryThread()) {
            wrapped.run();
        } else {
            Bukkit.getScheduler().runTask(this, wrapped);
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
