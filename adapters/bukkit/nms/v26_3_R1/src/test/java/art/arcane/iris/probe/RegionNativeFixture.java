package art.arcane.iris.probe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class RegionNativeFixture extends JavaPlugin {
    private final AtomicBoolean running = new AtomicBoolean();
    private final LoadCompletions pendingLoads = new LoadCompletions();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    public void onEnable() {
        getLogger().info("Native TERRAIN export fixture ready; console: mcaterrain <world> <chunkX> <chunkZ> <width> <workers>");
    }

    @Override
    public void onDisable() {
        pendingLoads.stop();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(60, TimeUnit.SECONDS)) {
                getLogger().severe("Native terrain export worker did not stop before shutdown");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            getLogger().log(Level.SEVERE, "Interrupted while stopping native terrain export", failure);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        boolean loading = command.getName().equals("mcaload");
        int expectedArguments = 5;
        if (!(sender instanceof ConsoleCommandSender) || arguments.length != expectedArguments) {
            sender.sendMessage("Console only: " + command.getName() + " <world> <chunkX> <chunkZ> <width>"
                    + " <workers>");
            return true;
        }
        try {
            if (!Bukkit.getWorldContainer().getCanonicalFile().getName().startsWith("mca-")) {
                throw new IllegalStateException("The fixture requires a disposable mca-* test instance");
            }
            if (!Bukkit.getOnlinePlayers().isEmpty() || Bukkit.getName().toLowerCase(Locale.ROOT).contains("folia")) {
                throw new IllegalStateException("The fixture requires an empty Paper test server");
            }
            World world = Bukkit.getWorld(arguments[0]);
            if (world == null || world.getGenerator() == null) {
                throw new IllegalArgumentException("A loaded Iris world is required");
            }
            int chunkX = Integer.parseInt(arguments[1]);
            int chunkZ = Integer.parseInt(arguments[2]);
            int width = Integer.parseInt(arguments[3]);
            List<NativeRegionPlan.PlannedChunk> plan = NativeRegionPlan.plan(chunkX, chunkZ, width);
            if (loading) {
                load(new LoadRequest(world, chunkX, chunkZ, width, Integer.parseInt(arguments[4])));
                return true;
            }
            Path worldRoot = world.getWorldFolder().toPath();
            for (NativeRegionPlan.PlannedChunk chunk : plan) {
                if (world.isChunkLoaded(chunk.x(), chunk.z()) || Files.exists(worldRoot.resolve("region")
                        .resolve("r." + (chunk.x() >> 5) + "." + (chunk.z() >> 5) + ".mca"))) {
                    throw new IllegalStateException("Native export requires untouched chunk and halo regions");
                }
            }
            Files.createDirectories(getDataFolder().toPath());
            Path output = getDataFolder().toPath().resolve("terrain-" + chunkX + "-" + chunkZ + "-" + width);
            ServerLevel level = ((CraftWorld) world).getHandle();
            DetachedNativeTerrain.Request request = new DetachedNativeTerrain.Request(level, chunkX, chunkZ, width,
                    Integer.parseInt(arguments[4]), output);
            if (Files.exists(output) || !running.compareAndSet(false, true)) {
                throw new IllegalStateException("An export is active or the output already exists");
            }
            worker.execute(() -> export(request));
            sender.sendMessage("Native TERRAIN export started: " + output);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Native TERRAIN export could not start", failure);
        }
        return true;
    }

    private void load(LoadRequest request) throws Exception {
        Path completed = getDataFolder().toPath().resolve("terrain-" + request.chunkX() + "-" + request.chunkZ()
                + "-" + request.width()).resolve("terrain.properties");
        if (!Files.isRegularFile(completed)) {
            throw new IllegalStateException("No completed native TERRAIN export exists for these coordinates");
        }
        Properties receipts = new Properties();
        try (Reader reader = Files.newBufferedReader(completed.getParent().resolve("receipts.properties"))) {
            receipts.load(reader);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A native fixture operation is active");
        }
        worker.execute(() -> loadRegion(request, receipts));
    }

    private void loadRegion(LoadRequest request, Properties receipts) {
        long started = System.nanoTime();
        int chunks = request.width() * request.width();
        getLogger().info("NATIVE_LOAD_STARTED chunks=" + chunks + " chunkX=" + request.chunkX()
                + " chunkZ=" + request.chunkZ() + " workers=" + request.parallelism());
        try {
            RegionGenerationWindow.process(new RegionGenerationWindow.Request<>(chunks, request.parallelism(),
                    index -> {
                        int x = request.chunkX() + index % request.width();
                        int z = request.chunkZ() + index / request.width();
                        requestChunk(new ChunkLoad(request.world(), x, z, receipts.getProperty(x + "," + z))).join();
                        return index;
                    }, (index, completed) -> {}));
            getLogger().info("NATIVE_LOAD_COMPLETE chunks=" + chunks + " workers=" + request.parallelism()
                    + " status=full light=true receiptSha256Matches=true seconds="
                    + (System.nanoTime() - started) / 1_000_000_000.0);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Native terrain reload failed after draining started requests", failure);
        } finally {
            running.set(false);
        }
    }

    private CompletableFuture<Void> requestChunk(ChunkLoad request) {
        CompletableFuture<Void> completion = pendingLoads.register();
        if (completion.isDone()) {
            return completion;
        }
        try {
            Bukkit.getScheduler().runTask(this, () -> {
                if (completion.isDone()) {
                    return;
                }
                try {
                    request.world().getChunkAtAsync(request.x(), request.z(), true)
                            .whenComplete((chunk, failure) -> completeLoad(new LoadedChunk(request, chunk, failure), completion));
                } catch (RuntimeException failure) {
                    completion.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    private void completeLoad(LoadedChunk loaded, CompletableFuture<Void> completion) {
        if (completion.isDone()) {
            return;
        }
        if (loaded.failure() != null) {
            completion.completeExceptionally(loaded.failure());
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            try {
                Bukkit.getScheduler().runTask(this, () -> completeLoad(loaded, completion));
            } catch (RuntimeException failure) {
                completion.completeExceptionally(failure);
            }
            return;
        }
        try {
            ChunkAccess nativeChunk = ((CraftChunk) loaded.chunk()).getHandle(ChunkStatus.FULL);
            if (nativeChunk.getPersistedStatus() != ChunkStatus.FULL || !nativeChunk.isLightCorrect()
                    || !DetachedNativeTerrain.receiptHash(nativeChunk).equals(loaded.request().receipt())) {
                throw new IllegalStateException("Native load did not finish status, light and receipt at "
                        + loaded.request().x() + "," + loaded.request().z());
            }
            completion.complete(null);
        } catch (RuntimeException failure) {
            completion.completeExceptionally(failure);
        }
    }

    private void export(DetachedNativeTerrain.Request request) {
        try {
            DetachedNativeTerrain.Result result = DetachedNativeTerrain.generate(request, getLogger()::info);
            String report = String.format(Locale.ROOT,
                    "stage=minecraft:terrain%ncomplete=false%nterrainChunks=%d%ndependencyChunks=%d%nseconds=%.3f%nworkers=%d%n"
                            + "remaining=features,lighting,spawn,full%n"
                            + "world=%s%nseed=%d%n"
                            + "installation=Stop this test server, retain its world history and mantle, recheck that EVERY destination MCA file is absent, then copy all MCA files without replacement into this same untouched world region directory. Never copy while running.%n",
                    result.terrainChunks(), result.dependencyChunks(), result.seconds(), request.parallelism(),
                    request.level().getWorld().getName(), request.level().getSeed());
            Files.writeString(request.output().resolve("terrain.properties"), report);
            getLogger().info("NATIVE_TERRAIN_COMPLETE " + report.replace('\n', ' '));
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Native TERRAIN export failed; output is incomplete", failure);
        } finally {
            running.set(false);
        }
    }
    static final class LoadCompletions {
        private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean stopping = new AtomicBoolean();

        CompletableFuture<Void> register() {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            pending.add(completion);
            completion.whenComplete((result, failure) -> pending.remove(completion));
            if (stopping.get()) {
                cancel(completion);
            }
            return completion;
        }

        void stop() {
            stopping.set(true);
            for (CompletableFuture<Void> completion : pending) {
                cancel(completion);
            }
        }

        int pendingCount() {
            return pending.size();
        }

        private static void cancel(CompletableFuture<Void> completion) {
            completion.completeExceptionally(new CancellationException("Native fixture is stopping"));
        }
    }

    private record LoadRequest(World world, int chunkX, int chunkZ, int width, int parallelism) {
        private LoadRequest {
            if (parallelism < 1 || parallelism > 16) {
                throw new IllegalArgumentException("Native load worker count must be 1..16");
            }
        }
    }

    private record ChunkLoad(World world, int x, int z, String receipt) {
    }

    private record LoadedChunk(ChunkLoad request, Chunk chunk, Throwable failure) {
    }

}
