package art.arcane.iris.probe;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.world.history.ChunkGenerationOwnership;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.SavedBiomeChunk;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptStorage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public final class TransitionProbe {
    private TransitionProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        Configuration configuration = Configuration.parse(arguments);
        Files.createDirectory(configuration.output());
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        Bindings bindings = new Bindings();
        Evidence evidence = new Evidence(
                new ArrayList<>(List.of("phase,chunk_x,chunk_z,activation,band,elapsed_ns,receipt_bytes,blocks_sha256,biomes_sha256,receipt_sha256,metadata_sha256")),
                new ArrayList<>(List.of("phase,x,z,ground_y,fluid_y,solid_spans,fluid_gaps")),
                new ArrayList<>(List.of("phase,x,z,minimum_y,maximum_y_exclusive,block")),
                new ArrayList<>(List.of("phase,chunk_x,chunk_z,block,count")));
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                new RealPackProbeSupport.WorkspaceOptions(configuration.packA().toFile(), configuration.dimension(),
                        "[transition-probe]", bindings, Path.of(System.getProperty("java.io.tmpdir"))));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), true, "transition")) {
            IrisEngine engine = (IrisEngine) session.engine();
            GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElseThrow();
            GenerationHistory history = router.history();
            long originalActivation = history.activeActivation().activationId();
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    generate(new GenerationRequest(engine, bindings, x, z, "original", false, deadline), evidence);
                }
            }
            Path world = engine.getWorld().worldFolder().toPath();
            Map<Long, byte[]> historicalReceipts = new HashMap<>();
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    historicalReceipts.put(ChunkGenerationOwnership.packChunk(x, z), SavedTerrainChunk.readReceipt(world, x, z));
                }
            }
            Path replacementPack = workspace.pack().toPath().getParent().resolve("replacement-pack");
            copyPack(configuration.packB(), replacementPack);
            SavedBiomeChunk historicalBiomes = router.biomes().snapshot(0, 0).orElseThrow();
            IrisData replacement = IrisData.openRuntime(replacementPack.toFile());
            try {
                IrisDimension dimension = replacement.getDimensionLoader().load(configuration.dimension());
                if (dimension == null) {
                    throw new IOException("Replacement dimension did not load");
                }
                requireVanillaBiomes(replacement, dimension);
                history.stageUpdate(replacementPack, GenerationPackFingerprint.compute(replacementPack,
                                GenerationPackFingerprint.CURRENT_VERSION),
                        GenerationEpochContractFactory.create(dimension, configuration.dimension(), "iris:transition_probe"),
                        GenerationRegistryContract.empty(), configuration.width());
            } finally {
                replacement.close();
            }
            try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(30_000)) {
                GenerationActivation activated = cutover.promotePending();
                require(activated.activationId() != originalActivation, "Pack update did not create another activation");
            }
            require(historicalBiomes.equals(router.biomes().snapshot(0, 0).orElseThrow()),
                    "Historical biome data changed during promotion");
            require(history.resolveActivation(0, 0).activationId() == originalActivation,
                    "Historical chunk ownership changed");
            require(history.semantics(0, 0).orElseThrow().activationId() == originalActivation,
                    "Historical semantic ownership changed");
            for (int sequence = 0; sequence < configuration.chunks(); sequence++) {
                int index = configuration.reverse() ? configuration.chunks() - 1 - sequence : sequence;
                generate(new GenerationRequest(engine, bindings, 2 + index, 0, "transition", true, deadline), evidence);
                generate(new GenerationRequest(engine, bindings, 2 + configuration.width() / 16 + index, 0,
                        "distant", false, deadline), evidence);
            }
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    require(Arrays.equals(historicalReceipts.get(ChunkGenerationOwnership.packChunk(x, z)),
                            SavedTerrainChunk.readReceipt(world, x, z)), "Historical disk receipt changed after generation");
                }
            }
            require(historicalBiomes.equals(router.biomes().snapshot(0, 0).orElseThrow()),
                    "Historical biome data changed after generation");
            for (Throwable failure : RealPackProbeSupport.settleAndDrain(engine)) {
                throw new IllegalStateException("Generation reported a failure", failure);
            }
            require(bindings.captures > 0, "Promotion did not capture historical terrain");
            Files.write(configuration.output().resolve("chunks.csv"), evidence.chunks());
            Files.write(configuration.output().resolve("transect.csv"), evidence.transect());
            Files.write(configuration.output().resolve("sections.csv"), evidence.sections());
            Files.write(configuration.output().resolve("materials.csv"), evidence.materials());
            System.out.println("[transition-probe] PASS generated=" + bindings.terrain.size()
                    + " boundaryChunkCaptures=" + bindings.captures
                    + " timing=diagnostic-only platform=stub cutover=studio output=" + configuration.output());
        }
    }

    private static void generate(GenerationRequest request, Evidence evidence) throws Exception {
        require(System.nanoTime() < request.deadline(), "Probe exceeded ten-minute workload deadline");
        long key = ChunkGenerationOwnership.packChunk(request.x(), request.z());
        require(!request.bindings().terrain.containsKey(key), "Probe scheduled a duplicate chunk");
        GenerationHistoryRuntimeRouter router = request.engine().getGenerationHistoryRuntimeRouter().orElseThrow();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(request.x(), request.z());
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope scope = route.openRuntimeScope()) {
            TransitionGenerationPlan plan = route.transitionPlan();
            boolean band = plan != null && plan.hasTransitionAtChunk(request.x(), request.z());
            require(band == request.band(), "Unexpected transition membership at " + request.x() + "," + request.z());
            long started = System.nanoTime();
            RealPackProbeSupport.GeneratedChunk generated = RealPackProbeSupport.generateChunk(
                    request.engine(), request.x(), request.z());
            require(route.claimGeneratedSemantics((x, y, z) -> {
                NativeBlockState state = generated.blocks().getRaw(x & 15, y, z & 15);
                return state == null || state.isAir();
            }), "Generated semantic claim was rejected");
            SavedTerrainChunk terrain = route.naturalTerrain().orElseThrow();
            byte[] receipt = NativeTerrainReceipt.encode(terrain, route.activation().activationId(), route.epoch().epochId());
            NativeTerrainReceipt.Decoded decoded = NativeTerrainReceipt.decode(receipt, "minecraft:full");
            require(decoded.activationId() == route.activation().activationId()
                    && decoded.epochId().equals(route.epoch().epochId()), "Receipt changed generation ownership");
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    if (!terrain.hasColumn(localX, localZ)) {
                        continue;
                    }
                    TerrainBoundarySignature original = terrain.column(request.x() * 16 + localX, request.z() * 16 + localZ);
                    TerrainBoundarySignature restored = decoded.terrain().column(request.x() * 16 + localX, request.z() * 16 + localZ);
                    require(original.column().equals(restored.column()) && original.geometry().equals(restored.geometry()),
                            "Receipt changed captured terrain");
                }
            }
            request.bindings().terrain.put(key, terrain);
            writeReceipt(request.engine().getWorld().worldFolder().toPath(), request.x(), request.z(), receipt);
            require(router.history().semantics(request.x(), request.z()).orElseThrow().activationId()
                    == route.activation().activationId(), "Generated semantics lost activation ownership");
            require(router.biomes().snapshot(request.x(), request.z()).orElseThrow().activationId()
                    == route.activation().activationId(), "Generated saved biomes lost activation ownership");
            long elapsed = System.nanoTime() - started;
            GenerationOrderProbe.ChunkHash hash = GenerationOrderProbe.hashChunk(
                    new GenerationOrderProbe.ChunkCoordinate(request.x(), request.z()),
                    generated.blocks(), generated.biomes(), generated.height());
            String metadataHash = metadataHash(router.biomes().snapshot(request.x(), request.z()).orElseThrow(),
                    router.history().semantics(request.x(), request.z()).orElseThrow());
            for (Map.Entry<String, Integer> material : new TreeMap<>(hash.blockCounts()).entrySet()) {
                evidence.materials().add(request.phase() + "," + request.x() + "," + request.z() + ","
                        + material.getKey() + "," + material.getValue());
            }
            evidence.chunks().add(request.phase() + "," + request.x() + "," + request.z() + ","
                    + route.activation().activationId() + "," + band + "," + elapsed + "," + receipt.length
                    + "," + hash.blocks() + "," + hash.biomes() + ","
                    + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(receipt)) + "," + metadataHash);
            if (request.z() == 0) {
                for (int x = 0; x < 16; x++) {
                    int ground = -1;
                    int fluid = -1;
                    int solidSpans = 0;
                    boolean solidBelow = false;
                    String stateBelow = null;
                    int spanStart = 0;
                    for (int y = 0; y < generated.height(); y++) {
                        NativeBlockState state = generated.blocks().getRaw(x, y, 8);
                        String stateKey = state == null ? "minecraft:air" : state.key();
                        if (!stateKey.equals(stateBelow)) {
                            if (stateBelow != null) {
                                evidence.sections().add(request.phase() + "," + (request.x() * 16 + x) + ",8,"
                                        + (spanStart + request.engine().getMinHeight()) + ","
                                        + (y + request.engine().getMinHeight()) + "," + stateBelow);
                            }
                            stateBelow = stateKey;
                            spanStart = y;
                        }
                        boolean solid = state != null && !state.isAir() && !isFluid(state);
                        if (solid && !solidBelow) {
                            solidSpans++;
                        }
                        solidBelow = solid;
                        if (state != null && !state.isAir()) {
                            if (isFluid(state)) {
                                fluid = y + request.engine().getMinHeight();
                            } else {
                                ground = y + request.engine().getMinHeight();
                            }
                        }
                    }
                    evidence.sections().add(request.phase() + "," + (request.x() * 16 + x) + ",8,"
                            + (spanStart + request.engine().getMinHeight()) + ","
                            + (generated.height() + request.engine().getMinHeight()) + "," + stateBelow);
                    int fluidGaps = 0;
                    for (int y = ground + 1; y < fluid; y++) {
                        NativeBlockState state = generated.blocks().getRaw(x, y - request.engine().getMinHeight(), 8);
                        if (state == null || state.isAir()) {
                            fluidGaps++;
                        }
                    }
                    evidence.transect().add(request.phase() + "," + (request.x() * 16 + x) + ",8," + ground + "," + fluid
                            + "," + solidSpans + "," + fluidGaps);
                }
            }
        }
    }

    private static boolean isFluid(NativeBlockState state) {
        return state.isWater() || state.key().startsWith("minecraft:lava");
    }

    private static String metadataHash(SavedBiomeChunk biomes, ChunkGenerationSemantics semantics) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                digest.update(biomes.column(x, z).toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        for (Collection<?> values : List.of(semantics.surfaceBiomeKeys(), semantics.caveBiomeKeys(),
                semantics.regionKeys(), semantics.riverProfileKeys(), semantics.objectKeys(), semantics.riverFeatures(),
                semantics.pointsOfInterest(), semantics.structures())) {
            digest.update(values.stream().map(Object::toString).sorted().toList().toString().getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void writeReceipt(Path world, int chunkX, int chunkZ, byte[] receipt) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(bytes)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(3);
            nbt.writeUTF("xPos");
            nbt.writeInt(chunkX);
            nbt.writeByte(3);
            nbt.writeUTF("zPos");
            nbt.writeInt(chunkZ);
            nbt.writeByte(8);
            nbt.writeUTF("Status");
            nbt.writeUTF("minecraft:full");
            nbt.writeByte(7);
            nbt.writeUTF(NativeTerrainReceiptStorage.NBT_KEY);
            nbt.writeInt(receipt.length);
            nbt.write(receipt);
            nbt.writeByte(0);
        }
        Path region = Files.createDirectories(world.resolve("region")).resolve(
                "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        byte[] payload = bytes.toByteArray();
        int sectors = (payload.length + 5 + 4095) / 4096;
        require(sectors <= 255, "Receipt exceeds MCA sector limit");
        try (RandomAccessFile output = new RandomAccessFile(region.toFile(), "rw")) {
            if (output.length() == 0) {
                output.setLength(8192);
            }
            int offset = Math.toIntExact((output.length() + 4095) / 4096);
            output.setLength((long) (offset + sectors) * 4096);
            output.seek((long) offset * 4096);
            output.writeInt(payload.length + 1);
            output.writeByte(3);
            output.write(payload);
            output.seek((long) ((chunkX & 31) + (chunkZ & 31) * 32) * 4);
            output.writeInt(offset << 8 | sectors);
        }
    }

    private static void copyPack(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                        Files.createDirectory(destination.resolve(source.relativize(directory)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.copy(file, destination.resolve(source.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static void requireVanillaBiomes(IrisData data, IrisDimension dimension) {
        require(!dimension.getAllBiomes(() -> data).stream().anyMatch(biome -> biome.isCustom()),
                "Transition probe requires vanilla physical biomes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    record Configuration(Path packA, Path packB, String dimension, long seed, int width, int chunks, Path output, boolean reverse) {
        static Configuration parse(String[] arguments) {
            if (arguments.length != 7 && arguments.length != 8) {
                throw new IllegalArgumentException("Expected packA packB dimension seed width chunks output [reverse]");
            }
            int width = Integer.parseInt(arguments[4]);
            int chunks = Integer.parseInt(arguments[5]);
            if (width < 16 || width > 128 || width % 16 != 0 || chunks < 1 || chunks > 8 || chunks * 16 > width) {
                throw new IllegalArgumentException("Width must be 16..128 in multiples of 16; chunks must be 1..8 within width");
            }
            Path packA = Path.of(arguments[0]).toAbsolutePath().normalize();
            Path packB = Path.of(arguments[1]).toAbsolutePath().normalize();
            if (!Files.isDirectory(packA) || !Files.isDirectory(packB)) {
                throw new IllegalArgumentException("Both source packs must be existing directories");
            }
            Path output = Path.of(arguments[6]).toAbsolutePath().normalize();
            if (Files.exists(output) || !Files.isDirectory(output.getParent())) {
                throw new IllegalArgumentException("Output must be a new directory beneath an existing parent");
            }
            if (arguments[2].isBlank()) {
                throw new IllegalArgumentException("Dimension cannot be blank");
            }
            return new Configuration(packA, packB, arguments[2], Long.parseLong(arguments[3]),
                    width, chunks, output, arguments.length == 8
                    && RealPackProbeSupport.parseBoolean(arguments[7], "reverse"));
        }
    }

    private record Evidence(List<String> chunks, List<String> transect, List<String> sections, List<String> materials) {
    }

    private record GenerationRequest(IrisEngine engine, Bindings bindings, int x, int z, String phase,
                                     boolean band, long deadline) {
    }

    private static final class Bindings implements RealPackProbeSupport.RuntimeBindings {
        private final Map<Long, SavedTerrainChunk> terrain = new HashMap<>();
        private int captures;

        @Override
        public IrisPlatform create(File platformRoot) {
            return new StubPlatform(platformRoot);
        }

        @Override
        public void prepare(IrisData data, IrisDimension dimension) {
            requireVanillaBiomes(data, dimension);
        }

        @Override
        public GenerationHistory createHistory(RealPackProbeSupport.HistoryRequest request) throws IOException {
            Files.createDirectories(request.worldRoot());
            Path pack = request.data().getDataFolder().toPath();
            return GenerationHistory.create(request.worldRoot(), pack,
                    GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION), request.seed(),
                    GenerationEpochContractFactory.create(request.dimension(), request.dimension().getLoadKey(),
                            "iris:transition_probe"), GenerationRegistryContract.empty());
        }

        @Override
        public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(Engine engine, int chunkX, int chunkZ) {
            captures++;
            SavedTerrainChunk chunk = terrain.get(ChunkGenerationOwnership.packChunk(chunkX, chunkZ));
            return chunk == null ? CompletableFuture.failedFuture(new IOException("Missing generated historical chunk"))
                    : CompletableFuture.completedFuture(chunk);
        }

        @Override
        public CompletableFuture<Void> flushSavedTerrainCapture(Engine engine) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            terrain.clear();
        }
    }
}
