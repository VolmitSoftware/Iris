package art.arcane.iris.probe;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.world.pregen.PregenPerformanceProfile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spigotmc.SpigotWorldConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class OfflineRegionGenerator {
    private OfflineRegionGenerator() {
    }

    public static void main(String[] arguments) {
        PrintStream diagnostics = System.err;
        try {
            execute(arguments);
        } catch (Throwable failure) {
            failure.printStackTrace(diagnostics);
            diagnostics.flush();
            System.exit(1);
        }
    }

    private static void execute(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("--help")) {
            System.out.println("Usage: iris-offline pack dimension seed chunkX chunkZ width regionsX regionsZ parallelism spigot worldName levelKey noiseSettings output");
            System.out.println("Java 25; width 1..32 chunks; parallelism 1..32. Output must be a new absolute directory.");
            System.out.println("Generates fresh TERRAIN checkpoints and immutable Iris history. Native server completion is required for features, population and lighting.");
            return;
        }
        if (Bukkit.getServer() != null) {
            throw new IllegalStateException("Offline generation requires a separate process without a running Bukkit server");
        }
        long processStarted = System.nanoTime();
        HeadlessNativeBootstrap.initialize();
        Configuration configuration = Configuration.parse(arguments);
        Files.createDirectory(configuration.output());
        force(configuration.output());
        try (FileChannel channel = FileChannel.open(configuration.output().resolve("checkpoint.lock"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            if (!lock.isValid()) {
                throw new IllegalStateException("Headless checkpoint lock is invalid");
            }
            run(configuration);
            System.out.println("processSeconds=" + (System.nanoTime() - processStarted) / 1_000_000_000.0);
        }
    }

    private static void run(Configuration configuration) throws Exception {
        Path staged = Files.createDirectory(configuration.output().resolve("incomplete"));
        byte[] configurationBytes = Files.readAllBytes(configuration.spigot());
        Path capturedConfiguration = staged.resolve("spigot.yml");
        Files.write(capturedConfiguration, configurationBytes, StandardOpenOption.CREATE_NEW);
        String configurationHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(configurationBytes));
        HeadlessNativeRuntime runtime = new HeadlessNativeRuntime(staged);
        long started = System.nanoTime();
        long writes = 0;
        long terrainWrites = 0;
        double generationSeconds = 0;
        double serializationSeconds = 0;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(configuration.pack(),
                configuration.dimension(), "[headless-native-region]", runtime, staged));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "native-region")) {
            session.retainWorld(staged.resolve("world"));
            SpigotWorldConfig spigot = HeadlessStructureConfiguration.load(new HeadlessStructureConfiguration.Options(
                    capturedConfiguration, configuration.worldName(), configuration.levelKey(), staged.resolve("configuration")));
            PregenPerformanceProfile.apply(session.engine());
            System.out.println("generationProfile=pregen noiseCacheSize=" + IrisSettings.get().getPerformance().getNoiseCacheSize()
                    + " burstParallelism=" + MultiBurst.burst.parallelism() + " multicore=true");
            IrisHydrologyRuntime hydrology = session.engine().getComplex().getHydrologyRuntime();
            try (HydrologyTileCache.PregenerationScope hydrologyScope = hydrology == null ? null
                    : hydrology.preparePregeneration(configuration.hydrologyArea());
                 HeadlessTerrainContext context = HeadlessTerrainContext.create(new HeadlessTerrainContext.Options(
                    session.engine(), runtime, staged.resolve("templates"), configuration.levelKey(),
                    configuration.noiseSettings(), spigot));
                 RegionGenerationWindow workers = new RegionGenerationWindow(configuration.parallelism(),
                         RegionGenerationWindow.Policy.STANDALONE)) {
                HeadlessRegionTerrain.Session rolling = new HeadlessRegionTerrain.Session(workers);
                Path regionDirectory = session.engine().getTarget().getWorld().worldFolder().toPath().resolve("region");
                Files.createDirectory(regionDirectory);
                OfflineRegionWriter writer = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                        context, regionDirectory, workers));
                for (int row = 0; row < configuration.regionsZ(); row++) {
                    for (int column = 0; column < configuration.regionsX(); column++) {
                        int regionX = row % 2 == 0 ? column : configuration.regionsX() - column - 1;
                        int chunkX = Math.addExact(configuration.chunkX(), Math.multiplyExact(regionX, configuration.width()));
                        int chunkZ = Math.addExact(configuration.chunkZ(), Math.multiplyExact(row, configuration.width()));
                        RegionResult generated = generateRegion(new RegionRequest(context, rolling, writer, chunkX, chunkZ,
                                configuration.width(), configuration.parallelism(),
                                staged.resolve("receipts.properties")));
                        writes += generated.written().chunks();
                        terrainWrites += generated.written().terrainChunks();
                        generationSeconds += generated.generationSeconds();
                        serializationSeconds += generated.serializationSeconds();
                        System.out.println("region=" + regionX + "," + row + " residentChunks=" + generated.residentChunks()
                                + " upgradedChunks=" + generated.upgradedChunks() + " writtenChunks=" + generated.written().chunks());
                    }
                }
            } finally {
                PregenPerformanceProfile.restore();
            }
        }
        double wallSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        String report = String.format(Locale.ROOT,
                "format=iris-native-terrain-checkpoint\nplatform=headless-native\nstatus=minecraft:terrain\ncomplete=false\n"
                        + "targetChunks=%d\nchunkWrites=%d\nterrainWrites=%d\nparallelism=%d\nseed=%d\nchunkX=%d\nchunkZ=%d\nwidth=%d\nregionsX=%d\nregionsZ=%d\n"
                        + "levelKey=%s\nworldName=%s\nnoiseSettings=%s\nspigotSha256=%s\npipelineWallSeconds=%.3f\ngenerationSeconds=%.3f\nserializationSeconds=%.3f\n"
                        + "limitations=Fresh offline world checkpoint only. Native features, population, lighting, POI, entities and scheduled ticks require native completion. No live plugin events are dispatched. Authored tile payloads remain in retained mantle for native completion. No transitions or existing chunks are imported.\n",
                Math.multiplyExact(Math.multiplyExact(configuration.width(), configuration.width()),
                        Math.multiplyExact(configuration.regionsX(), configuration.regionsZ())), writes, terrainWrites,
                configuration.parallelism(), configuration.seed(), configuration.chunkX(), configuration.chunkZ(),
                configuration.width(), configuration.regionsX(), configuration.regionsZ(), configuration.levelKey().identifier(),
                configuration.worldName(), configuration.noiseSettings().identifier(), configurationHash,
                wallSeconds, generationSeconds, serializationSeconds);
        Files.writeString(staged.resolve("checkpoint.properties"), report, StandardOpenOption.CREATE_NEW);
        long publicationStarted = System.nanoTime();
        publish(staged, configuration.output().resolve("native-terrain-checkpoint"));
        System.out.println(report);
        System.out.println("publicationSeconds=" + (System.nanoTime() - publicationStarted) / 1_000_000_000.0);
        System.out.println("totalSeconds=" + (System.nanoTime() - started) / 1_000_000_000.0);
    }

    private static RegionResult generateRegion(RegionRequest request) throws Exception {
        long generationStarted = System.nanoTime();
        HeadlessRegionTerrain.Result generated = request.rolling().generate(new HeadlessRegionTerrain.Request(
                request.context(), request.chunkX(), request.chunkZ(), request.width(), request.parallelism()), System.out::println);
        double generationSeconds = (System.nanoTime() - generationStarted) / 1_000_000_000.0;
        long serializationStarted = System.nanoTime();
        OfflineRegionWriter.Result written = request.writer().write(generated);
        writeReceipts(generated, request.receipts());
        return new RegionResult(written, generated.chunks().size(), generated.updated().size(), generationSeconds,
                (System.nanoTime() - serializationStarted) / 1_000_000_000.0);
    }

    private record RegionRequest(HeadlessTerrainContext context, HeadlessRegionTerrain.Session rolling, OfflineRegionWriter writer,
                                 int chunkX, int chunkZ, int width, int parallelism, Path receipts) {
    }

    private record RegionResult(OfflineRegionWriter.Result written, int residentChunks, int upgradedChunks,
                                double generationSeconds, double serializationSeconds) {
    }

    private static void writeReceipts(HeadlessRegionTerrain.Result generated, Path path) throws Exception {
        NamespacedKey key = new NamespacedKey("iris", "natural_terrain");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder receipts = new StringBuilder();
        for (ProtoChunk chunk : generated.updated()) {
            if (!chunk.getPersistedStatus().isOrAfter(ChunkStatus.TERRAIN)) {
                continue;
            }
            byte[] receipt = chunk.persistentDataContainer.get(key, PersistentDataType.BYTE_ARRAY);
            if (receipt == null) {
                throw new IllegalStateException("Native TERRAIN checkpoint has no receipt at " + chunk.getPos());
            }
            receipts.append(chunk.getPos().x()).append(',').append(chunk.getPos().z()).append('=')
                    .append(HexFormat.of().formatHex(digest.digest(receipt))).append('\n');
        }
        Files.writeString(path, receipts, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void forceTree(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                force(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                force(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void publish(Path staged, Path destination) throws IOException {
        forceTree(staged);
        Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE);
        force(destination.getParent());
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, Files.isDirectory(path)
                ? StandardOpenOption.READ : StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    record Configuration(File pack, String dimension, long seed, int chunkX, int chunkZ, int width,
                         int regionsX, int regionsZ, int parallelism, Path spigot, String worldName, ResourceKey<Level> levelKey,
                         ResourceKey<NoiseGeneratorSettings> noiseSettings, Path output) {
        Configuration {
            if (regionsX < 1 || regionsZ < 1) {
                throw new IllegalArgumentException("Region dimensions must be positive");
            }
            Math.multiplyExact(Math.multiplyExact(width, width), Math.multiplyExact(regionsX, regionsZ));
            NativeRegionPlan.plan(chunkX, chunkZ, width);
            NativeRegionPlan.plan(Math.addExact(chunkX, Math.multiplyExact(regionsX - 1, width)),
                    Math.addExact(chunkZ, Math.multiplyExact(regionsZ - 1, width)), width);
            if (parallelism < 1 || parallelism > 32 || !Files.isRegularFile(spigot)) {
                throw new IllegalArgumentException("Parallelism must be 1..32 and Spigot configuration must exist");
            }
            if (!output.isAbsolute() || Files.exists(output) || !Files.isDirectory(output.getParent())) {
                throw new IllegalArgumentException("Output must be a new absolute directory under an existing parent");
            }
        }

        HydrologyTileCache.PregenerationArea hydrologyArea() {
            int halo = NativeRegionPlan.terrainHalo();
            long minimumX = ((long) chunkX - halo) * 16L;
            long minimumZ = ((long) chunkZ - halo) * 16L;
            long maximumX = ((long) chunkX + (long) width * regionsX + halo) * 16L - 1L;
            long maximumZ = ((long) chunkZ + (long) width * regionsZ + halo) * 16L - 1L;
            return new HydrologyTileCache.PregenerationArea(Math.toIntExact((minimumX + maximumX) / 2L),
                    Math.toIntExact((minimumZ + maximumZ) / 2L), minimumX, minimumZ, maximumX, maximumZ);
        }

        static Configuration parse(String[] arguments) throws IOException {
            if (arguments.length != 14) {
                throw new IllegalArgumentException("Expected pack dimension seed chunkX chunkZ width regionsX regionsZ parallelism spigot worldName levelKey noiseSettings output");
            }
            return new Configuration(new File(arguments[0]).getCanonicalFile(), arguments[1], Long.parseLong(arguments[2]),
                    Integer.parseInt(arguments[3]), Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]),
                    Integer.parseInt(arguments[6]), Integer.parseInt(arguments[7]), Integer.parseInt(arguments[8]),
                    Path.of(arguments[9]), arguments[10],
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(arguments[11])),
                    ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.parse(arguments[12])), Path.of(arguments[13]));
        }
    }
}
