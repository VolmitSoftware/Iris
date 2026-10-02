package art.arcane.iris.fixture;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitEntityType;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class NaturalSpawningFixture extends JavaPlugin implements Listener {
    private static final NamespacedKey WORLD_KEY = new NamespacedKey("iris", "spawn_qa");
    private static final long WORLD_SEED = 78264193L;
    private static final int FROG_CAPACITY = 16;
    private static final int ZOMBIE_CAPACITY = 2;
    private static final int BIOME_READY_ATTEMPTS = 60;
    private static final long BIOME_RETRY_TICKS = 10L;

    private boolean previousAmbient;
    private boolean previousMarkers;
    private boolean previousForcedPersistence;
    private volatile boolean ready;
    private int firstZombieBatch;
    private int ambientEvents;
    private int cancelledAmbientEvents;
    private final ThreadLocal<Boolean> ambientInvocation = ThreadLocal.withInitial(() -> false);

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        previousAmbient = IrisSettings.get().getWorld().isAmbientEntitySpawningSystem();
        previousMarkers = IrisSettings.get().getWorld().isMarkerEntitySpawningSystem();
        previousForcedPersistence = IrisSettings.get().getWorld().isForcePersistEntities();
        IrisSettings.get().getWorld().setAmbientEntitySpawningSystem(false);
        IrisSettings.get().getWorld().setMarkerEntitySpawningSystem(false);
        IrisSettings.get().getWorld().setForcePersistEntities(false);
        getLogger().info("Natural spawning fixture ready; commands use the Iris ambient path on the owning region.");
    }

    @Override
    public void onDisable() {
        IrisSettings.get().getWorld().setAmbientEntitySpawningSystem(previousAmbient);
        IrisSettings.get().getWorld().setMarkerEntitySpawningSystem(previousMarkers);
        IrisSettings.get().getWorld().setForcePersistEntities(previousForcedPersistence);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length != 1) {
            notifySender(sender, "Use /irisspawnqa setup|frogs|zombies|stress|check|surface|cave");
            return true;
        }
        try {
            switch (arguments[0]) {
                case "setup" -> setup(sender);
                case "frogs" -> runRegion(sender, () -> spawnFrogs(sender));
                case "zombies" -> runRegion(sender, () -> spawnZombies(sender));
                case "stress" -> runRegion(sender, () -> stress(sender));
                case "check" -> runRegion(sender, () -> check(sender));
                case "surface" -> view(sender, 67D);
                case "cave" -> view(sender, -46D);
                default -> throw new IllegalArgumentException("Unknown spawning operation");
            }
        } catch (Throwable failure) {
            fail(sender, failure);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void isolateSpawns(CreatureSpawnEvent event) {
        if (!WORLD_KEY.equals(event.getLocation().getWorld().getKey())) {
            return;
        }
        if (!ambientInvocation.get()) {
            event.setCancelled(true);
            return;
        }
        ambientEvents++;
        if (event.isCancelled()) {
            cancelledAmbientEvents++;
        }
    }

    private void setup(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new IllegalArgumentException("Setup requires a player");
        }
        World world = Bukkit.getWorld(WORLD_KEY);
        if (world != null) {
            require(IrisToolbelt.isIrisWorld(world) && world.getSeed() == WORLD_SEED,
                    "Existing fixture key is not the owned Iris world with the expected seed");
            ready = false;
            Bukkit.getGlobalRegionScheduler().execute(this, () -> configureWorld(sender, world));
            return;
        }
        ready = false;
        Bukkit.getAsyncScheduler().runNow(this, task -> createWorld(sender));
    }

    private void createWorld(CommandSender sender) {
        try {
            World world = new IrisCreator().name(WORLD_KEY.toString()).dimension("spawn-qa")
                    .seed(WORLD_SEED).create();
            Bukkit.getGlobalRegionScheduler().execute(this, () -> configureWorld(sender, world));
        } catch (Throwable failure) {
            fail(sender, failure);
        }
    }

    private void configureWorld(CommandSender sender, World world) {
        try {
            world.setDifficulty(Difficulty.NORMAL);
            world.setGameRule(GameRules.SPAWN_MOBS, true);
            List<CompletableFuture<Void>> chunks = new ArrayList<>();
            for (int chunkX = -1; chunkX <= 1; chunkX++) {
                for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                    chunks.add(retainChunk(world, chunkX, chunkZ));
                }
            }
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    fail(sender, failure);
                } else {
                    Bukkit.getRegionScheduler().execute(this, world, 0, 0, () -> prepareChunk(sender, world));
                }
            });
        } catch (Throwable failure) {
            fail(sender, failure);
        }
    }

    private CompletableFuture<Void> retainChunk(World world, int chunkX, int chunkZ) {
        CompletableFuture<Void> retained = new CompletableFuture<>();
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, failure) -> {
            if (failure != null) {
                retained.completeExceptionally(failure);
                return;
            }
            Bukkit.getRegionScheduler().execute(this, world, chunkX, chunkZ, () -> {
                try {
                    chunk.addPluginChunkTicket(this);
                    retained.complete(null);
                } catch (Throwable error) {
                    retained.completeExceptionally(error);
                }
            });
        });
        return retained;
    }

    private void prepareChunk(CommandSender sender, World world) {
        try {
            require(WORLD_KEY.equals(world.getKey()) && IrisToolbelt.isIrisWorld(world)
                    && world.getSeed() == WORLD_SEED, "Fixture world ownership changed");
            Chunk chunk = world.getChunkAt(0, 0);
            int removed = 0;
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    entity.remove();
                    removed++;
                }
            }
            for (Entity entity : chunk.getEntities()) {
                require(!(entity instanceof LivingEntity) || entity instanceof Player || !entity.isValid(),
                        "Fixture target chunk retained a living mob after cleanup: " + entity.getType());
            }
            getLogger().info("SPAWN_QA_CLEANUP removed=" + removed + " chunk=0,0");
            firstZombieBatch = 0;
            ambientEvents = 0;
            cancelledAmbientEvents = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, 64, z).setType(Material.GRASS_BLOCK, false);
                    for (int y = 65; y <= 68; y++) {
                        chunk.getBlock(x, y, z).setType(Material.AIR, false);
                    }
                    for (int y = -48; y <= -42; y++) {
                        boolean shell = y == -48 || y == -42 || x == 0 || x == 15 || z == 0 || z == 15;
                        chunk.getBlock(x, y, z).setType(shell ? Material.STONE : Material.CAVE_AIR, false);
                    }
                }
            }
            if (!IrisToolbelt.isIrisWorld(world) || world.getSeed() != WORLD_SEED) {
                throw new IllegalStateException("Fixture is not the requested Iris world");
            }
            awaitBiomeEnvironment(sender, world, 0);
        } catch (Throwable failure) {
            fail(sender, failure);
        }
    }

    private void awaitBiomeEnvironment(CommandSender sender, World world, int attempt) {
        try {
            Engine engine = IrisToolbelt.access(world).getEngine();
            engine.getSurfaceBiomeEnvironment(8, 8);
            ready = true;
            teleport(sender, world, 67D, "SPAWN_QA_SETUP world=" + WORLD_KEY + " seed=" + WORLD_SEED);
        } catch (SavedBiomeUnavailableException unavailable) {
            if (!unavailable.isLoading() || attempt >= BIOME_READY_ATTEMPTS) {
                fail(sender, unavailable);
                return;
            }
            Bukkit.getRegionScheduler().runDelayed(this, world, 0, 0,
                    task -> awaitBiomeEnvironment(sender, world, attempt + 1), BIOME_RETRY_TICKS);
        } catch (Throwable failure) {
            fail(sender, failure);
        }
    }

    private void spawnFrogs(CommandSender sender) {
        Chunk chunk = requireWorld().getChunkAt(0, 0);
        RuntimeSpawn spawn = runtimeSpawn(chunk, "frogs");
        getLogger().info(spawnDiagnostics(spawn, chunk));
        for (int attempt = 0; attempt < 12; attempt++) {
            invokeAmbient(spawn, chunk);
            require(count(chunk, EntityType.FROG) <= FROG_CAPACITY, "Frog batch exceeded its category capacity");
        }
        require(count(chunk, EntityType.FROG) == FROG_CAPACITY,
                "Frog ambient category did not fill: " + spawnDiagnostics(spawn, chunk));
        require(count(chunk, EntityType.ZOMBIE) == 0, "Unexpected zombie before cave phase");
        verifyPersistence(chunk);
        evidence(sender, "SPAWN_QA_FROGS frogs=" + count(chunk, EntityType.FROG)
                + " persistent=true removable=true ambient=true");
    }

    private void spawnZombies(CommandSender sender) {
        Chunk chunk = requireWorld().getChunkAt(0, 0);
        require(count(chunk, EntityType.FROG) == FROG_CAPACITY, "Frogs must fill their category before cave spawning");
        require(count(chunk, EntityType.ZOMBIE) == 0, "Cave phase already ran");
        RuntimeSpawn spawn = runtimeSpawn(chunk, "cave-zombies");
        getLogger().info(spawnDiagnostics(spawn, chunk));
        invokeAmbient(spawn, chunk);
        firstZombieBatch = count(chunk, EntityType.ZOMBIE);
        require(firstZombieBatch == ZOMBIE_CAPACITY,
                "Authored seven-zombie batch was not clamped to capacity two: " + spawnDiagnostics(spawn, chunk));
        require(count(chunk, EntityType.FROG) == FROG_CAPACITY, "Cave spawning changed the passive population");
        verifyPersistence(chunk);
        evidence(sender, "SPAWN_QA_ZOMBIES frogs=" + count(chunk, EntityType.FROG)
                + " zombies=" + firstZombieBatch + " firstBatch=" + firstZombieBatch + " ambient=true");
    }

    private void stress(CommandSender sender) {
        Chunk chunk = requireWorld().getChunkAt(0, 0);
        RuntimeSpawn spawn = runtimeSpawn(chunk, "cave-zombies");
        for (int attempt = 0; attempt < 50; attempt++) {
            invokeAmbient(spawn, chunk);
            require(count(chunk, EntityType.ZOMBIE) == ZOMBIE_CAPACITY, "Saturated zombie category exceeded capacity");
        }
        evidence(sender, "SPAWN_QA_STRESS attempts=50 zombies=" + count(chunk, EntityType.ZOMBIE));
    }

    private void check(CommandSender sender) {
        Chunk chunk = requireWorld().getChunkAt(0, 0);
        require(count(chunk, EntityType.FROG) == FROG_CAPACITY, "Passive population differs");
        require(count(chunk, EntityType.ZOMBIE) == ZOMBIE_CAPACITY, "Hostile population differs");
        require(firstZombieBatch == ZOMBIE_CAPACITY, "First hostile batch was not pinned");
        require(chunk.getBlock(8, -47, 8).getType() == Material.CAVE_AIR, "Cave is not real CAVE_AIR");
        require(chunk.getBlock(8, -47, 8).getLightLevel() == 0, "Cave is not dark");
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ZOMBIE) {
                require(entity.getLocation().getY() < -42, "Hostile spawned outside the cave");
            }
        }
        verifyPersistence(chunk);
        evidence(sender, "SPAWN_QA_RESULT frogs=16 zombies=2 firstBatch=2 persistent=true removable=true caveAir=true light=0");
    }

    private RuntimeSpawn runtimeSpawn(Chunk chunk, String biomeKey) {
        try {
            Engine engine = IrisToolbelt.access(chunk.getWorld()).getEngine();
            BiomeEnvironment surface = engine.getSurfaceBiomeEnvironment(8, 8);
            IrisBiome biome = surface.data().getBiomeLoader().load(biomeKey);
            require(biome != null, "Synthetic biome was not loaded");
            BiomeEnvironment selected = new BiomeEnvironment(surface.activationId(), biome, surface.region(),
                    surface.dimension(), surface.data());
            Object manager = engine.getWorldManager();
            Field field = manager.getClass().getDeclaredField("entitySpawner");
            field.setAccessible(true);
            Object spawner = field.get(manager);
            Method method = spawner.getClass().getDeclaredMethod("spawnAmbient", Chunk.class, boolean.class, BiomeEnvironment.class);
            method.setAccessible(true);
            return new RuntimeSpawn(engine, spawner, method, selected);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not access Iris ambient spawning", failure);
        }
    }

    private String spawnDiagnostics(RuntimeSpawn spawn, Chunk chunk) {
        World world = chunk.getWorld();
        int floor = world.getHighestBlockYAt(8, 8, HeightMap.OCEAN_FLOOR);
        int top = world.getHighestBlockYAt(8, 8, HeightMap.WORLD_SURFACE);
        Map<String, Integer> categories = new TreeMap<>();
        Map<String, Integer> types = new TreeMap<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                categories.merge(BukkitEntityType.of(entity.getType()).spawnCategory(), 1, Integer::sum);
                types.merge(entity.getType().name(), 1, Integer::sum);
            }
        }
        StringBuilder diagnostics = new StringBuilder("SPAWN_QA_DIAGNOSTICS frogs=")
                .append(count(chunk, EntityType.FROG)).append(" zombies=").append(count(chunk, EntityType.ZOMBIE))
                .append(" categories=").append(categories).append(" types=").append(types)
                .append(" loadLevel=").append(chunk.getLoadLevel())
                .append(" spawnMobs=").append(world.getGameRuleValue(GameRules.SPAWN_MOBS))
                .append(" animals=").append(world.getAllowAnimals()).append(" monsters=").append(world.getAllowMonsters())
                .append(" floor=").append(floor).append(" top=").append(top)
                .append(" support=").append(world.getBlockAt(8, top, 8).getType())
                .append(" above=").append(world.getBlockAt(8, top + 1, 8).getType())
                .append(" light=").append(world.getBlockAt(8, top + 1, 8).getLightLevel())
                .append(" events=").append(ambientEvents).append(" cancelled=").append(cancelledAmbientEvents);
        for (String key : spawn.environment().biome().getEntitySpawners()) {
            IrisSpawner spawner = spawn.environment().data().getSpawnerLoader().load(key);
            if (spawner == null) {
                diagnostics.append(" spawner=").append(key).append(":missing");
                continue;
            }
            diagnostics.append(" spawner=").append(key).append(" canSpawn=")
                    .append(spawner.canSpawn(spawn.engine(), chunk.getX(), chunk.getZ()))
                    .append(" excluded=").append(spawner.isCompatExcluded())
                    .append(" group=").append(spawner.getGroup()).append(" capacity=").append(spawner.getMaxEntitiesPerChunk());
            for (IrisEntitySpawn entry : spawner.getSpawns()) {
                IrisEntity entity = entry.getRealEntity(spawn.engine());
                diagnostics.append(" entity=").append(entry.getEntity());
                if (entity == null) {
                    diagnostics.append(":missing");
                    continue;
                }
                diagnostics.append(" type=").append(entity.getBukkitType()).append(" category=").append(entity.spawnCategory())
                        .append(" surface=").append(entity.getSurface())
                        .append(" supportMatches=").append(entity.getSurface().matches(world.getBlockAt(8, top, 8)))
                        .append(" bounds=").append(BukkitPlatform.entityBoundingBox(entity.getBukkitType()));
            }
        }
        return diagnostics.toString();
    }

    private void invokeAmbient(RuntimeSpawn spawn, Chunk chunk) {
        ambientInvocation.set(true);
        try (BiomeEnvironment.Scope ignored = spawn.engine().openBiomeEnvironmentScope(spawn.environment())) {
            spawn.method().invoke(spawn.spawner(), chunk, false, spawn.environment());
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Iris ambient spawning failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not invoke Iris ambient spawning", failure);
        } finally {
            ambientInvocation.remove();
        }
    }

    private int count(Chunk chunk, EntityType type) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private void verifyPersistence(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.FROG || entity.getType() == EntityType.ZOMBIE) {
                require(entity.isPersistent(), "Ambient entity stopped participating in chunk saves");
                require(entity instanceof LivingEntity living && living.getRemoveWhenFarAway(),
                        "Ambient entity has distance despawning disabled");
            }
        }
    }

    private void view(CommandSender sender, double y) {
        teleport(sender, requireWorld(), y, "SPAWN_QA_VIEW y=" + (int) y);
    }

    private void teleport(CommandSender sender, World world, double y, String message) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Viewing requires a player");
        }
        player.getScheduler().run(this, task -> {
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.teleportAsync(new Location(world, 40.5D, y, 8.5D)).whenComplete((success, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    fail(sender, failure == null ? new IllegalStateException("Fixture teleport failed") : failure);
                } else {
                    notifySender(sender, message);
                }
            });
        }, null);
    }

    private World requireWorld() {
        World world = Bukkit.getWorld(WORLD_KEY);
        require(ready && world != null, "Run setup before a spawning phase");
        return world;
    }

    private void runRegion(CommandSender sender, Runnable operation) {
        World world = requireWorld();
        Bukkit.getRegionScheduler().execute(this, world, 0, 0, () -> {
            try {
                operation.run();
            } catch (Throwable failure) {
                fail(sender, failure);
            }
        });
    }

    private void evidence(CommandSender sender, String message) {
        getLogger().info(message);
        notifySender(sender, message);
    }

    private void notifySender(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(this, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    private void fail(CommandSender sender, Throwable failure) {
        getLogger().log(Level.SEVERE, "Spawning acceptance failed", failure);
        notifySender(sender, "SPAWN_QA_FAILED " + failure.getMessage());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record RuntimeSpawn(Engine engine, Object spawner, Method method, BiomeEnvironment environment) {
    }
}
