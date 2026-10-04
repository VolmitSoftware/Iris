package art.arcane.iris.probe;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.testsupport.IrisRuntimeState;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.IrisBoundarySignatureSampler;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ObjectContinuationProbeTest {
    private static final long SEED = 1338;
    private static final Fixture TREE = new Fixture("boundary-tree", "trees/boundary", Set.of("minecraft:oak_log", "minecraft:oak_leaves"), true);
    private static final Fixture STRUCTURE = new Fixture("boundary-structure", "buildings/boundary", Set.of("minecraft:bricks", "minecraft:oak_planks", "minecraft:chest"), false);

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @AfterClass
    public static void resetRuntime() {
        IrisRuntimeState.reset();
    }

    @Test(timeout = 120_000)
    public void removedTreeCompletesOnlyItsPublishedPlacementsAfterRestart() throws Exception {
        verifyCompletion(false, TREE);
    }

    @Test(timeout = 120_000)
    public void originalTreeSurvivesTwoUpdatesBeforeItsTailGenerates() throws Exception {
        verifyCompletion(true, TREE);
    }

    @Test(timeout = 120_000)
    public void removedStructureCompletesItsCarveAndFoundationAfterRestart() throws Exception {
        verifyCompletion(false, STRUCTURE);
    }

    private void verifyCompletion(boolean secondUpdate, Fixture fixture) throws Exception {
        Map<Position, ObjectVoxel> controlOld;
        Map<Position, ObjectVoxel> controlNew;
        GeneratedSnapshot nativeSource;
        GeneratedSnapshot nativeDestination;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                fixture(fixture.pack()).toFile(), "transition", "[object-control]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(SEED, true, "control")) {
            nativeSource = generatedObjects(session.engine(), 0, fixture);
            controlOld = nativeSource.objects();
            nativeDestination = generatedObjects(session.engine(), 1, fixture);
            controlNew = nativeDestination.objects();
        }
        Set<String> historicalPlacements = new HashSet<>();
        for (ObjectVoxel voxel : controlOld.values()) {
            historicalPlacements.add(voxel.marker());
        }
        Map<Position, ObjectVoxel> expectedTail = new HashMap<>();
        Map<Position, ObjectVoxel> unrelated = new HashMap<>();
        for (Map.Entry<Position, ObjectVoxel> entry : controlNew.entrySet()) {
            (historicalPlacements.contains(entry.getValue().marker()) ? expectedTail : unrelated)
                    .put(entry.getKey(), entry.getValue());
        }
        assertFalse("Fixture must have old object blocks in the saved chunk", controlOld.isEmpty());
        assertFalse("Fixture must have an old placement crossing into the next chunk", expectedTail.isEmpty());
        if (fixture.tree()) {
            assertFalse("Fixture must include old placements wholly inside the new area", unrelated.isEmpty());
            Map<String, Position> centers = new HashMap<>();
            Map<Position, ObjectVoxel> control = new HashMap<>(controlOld);
            control.putAll(controlNew);
            int trunkY = control.keySet().stream().mapToInt(Position::y).min().orElseThrow();
            for (Map.Entry<Position, ObjectVoxel> entry : control.entrySet()) {
                if (entry.getKey().y() == trunkY) {
                    centers.put(entry.getValue().marker(), entry.getKey());
                }
            }
            boolean crossingFromNewSource = false;
            boolean unrelatedFromNewSource = false;
            for (Map.Entry<String, Position> entry : centers.entrySet()) {
                if (entry.getValue().x() >= 16 && entry.getValue().x() < 32) {
                    crossingFromNewSource |= historicalPlacements.contains(entry.getKey());
                    unrelatedFromNewSource |= !historicalPlacements.contains(entry.getKey());
                }
            }
            assertTrue("Fixture must distinguish two placements from the same new source chunk: " + centers,
                    crossingFromNewSource && unrelatedFromNewSource);
        } else {
            assertTrue("Structure fixture must place foundations", nativeDestination.foundations() > 0);
            assertTrue("Structure fixture must carve solid ground", nativeDestination.carvedGround() > 0);
        }

        Bindings bindings = new Bindings();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                new RealPackProbeSupport.WorkspaceOptions(fixture(fixture.pack()).toFile(), "transition",
                        "[object-update]", bindings, temporary.getRoot().toPath()))) {
            IrisWorld world;
            byte[] oldReceipt;
            long originalActivation;
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(SEED, true, "original")) {
                IrisEngine engine = (IrisEngine) session.engine();
                GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElseThrow();
                originalActivation = router.history().activeActivation().activationId();
                assertEquals(controlOld, generateClaimed(engine, bindings, 0, fixture).objects());
                world = engine.getWorld();
                oldReceipt = SavedTerrainChunk.readReceipt(world.worldFolder().toPath(), 0, 0);
                Path replacement = workspace.pack().toPath().getParent().resolve("replacement");
                copyPack(fixture("lowland"), replacement);
                IrisData replacementData = IrisData.openRuntime(replacement.toFile());
                try {
                    IrisDimension dimension = replacementData.getDimensionLoader().load("transition");
                    assertNotNull(dimension);
                    router.history().stageUpdate(replacement,
                            GenerationPackFingerprint.compute(replacement, GenerationPackFingerprint.CURRENT_VERSION),
                            GenerationEpochContractFactory.create(dimension, "transition", "iris:object_probe"),
                            GenerationRegistryContract.empty(), 32);
                } finally {
                    replacementData.close();
                }
                try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(30_000)) {
                    assertTrue(cutover.promotePending().activationId() > originalActivation);
                }
            }
            GenerationHistory reopened = GenerationHistory.open(world.worldFolder().toPath(), SEED);
            if (secondUpdate) {
                Path third = workspace.pack().toPath().getParent().resolve("third-pack");
                copyPack(fixture("mountain"), third);
                IrisData thirdData = IrisData.openRuntime(third.toFile());
                try {
                    IrisDimension dimension = thirdData.getDimensionLoader().load("transition");
                    assertNotNull(dimension);
                    reopened.stageUpdate(third,
                            GenerationPackFingerprint.compute(third, GenerationPackFingerprint.CURRENT_VERSION),
                            GenerationEpochContractFactory.create(dimension, "transition", "iris:object_probe"),
                            GenerationRegistryContract.empty(), 32);
                } finally {
                    thirdData.close();
                }
                reopened.prepareCurrentGenerator(32);
                reopened = GenerationHistory.open(world.worldFolder().toPath(), SEED);
                assertTrue(reopened.activeActivation().activationId() > originalActivation + 1);
            }
            IrisData data = IrisData.openRuntime(reopened.activePackRoot().toFile());
            try {
                data.bindGenerationRegistryContract(reopened.activeEpoch().registryContract());
                IrisDimension dimension = data.getDimensionLoader().load("transition");
                assertNotNull(dimension);
                IrisEngine engine = new IrisEngine(new EngineTarget(world, dimension, data),
                        IrisEngine.InitializationMode.RUNTIME,
                        reopened.paths().activationMantleRoot(reopened.activeActivation().activationId()),
                        reopened.activeEpoch().kernelVersion(), reopened.transitionPlan(reopened.activeActivation().activationId()));
                try {
                    GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                            engine, reopened, IrisBoundarySignatureSampler.INSTANCE);
                    router.preloadActiveRuntimes();
                    assertEquals("Old object blocks and metadata must reload before neighboring generation",
                            controlOld, historicalObjects(engine, fixture));
                    assertHistoricalEdits(engine, nativeSource.edits());
                    GeneratedSnapshot result = generateClaimed(engine, bindings, 1, fixture);
                    Map<Position, ObjectVoxel> completed = result.objects();
                    if (!fixture.tree()) {
                        for (Map.Entry<Position, String> edit : nativeDestination.edits().entrySet()) {
                            assertEquals("Structure carve/foundation at " + edit.getKey(), edit.getValue(),
                                    result.blocks().get(edit.getKey()));
                        }
                        assertTrue("Completed storage blocks must retain structure-aware placement markers",
                                completed.values().stream().anyMatch(voxel -> voxel.marker() != null));
                    }
                    assertEquals("Complete only placements with a published old portion", expectedTail, completed);
                    for (Map.Entry<Position, ObjectVoxel> entry : unrelated.entrySet()) {
                        assertFalse("Unrelated old placement was imported at " + entry.getKey(),
                                completed.containsKey(entry.getKey()));
                    }
                    assertEquals("Old geometry and metadata must survive neighboring generation",
                            controlOld, historicalObjects(engine, fixture));
                    assertHistoricalEdits(engine, nativeSource.edits());
                    assertArrayEquals(oldReceipt, SavedTerrainChunk.readReceipt(world.worldFolder().toPath(), 0, 0));
                    assertEquals(originalActivation, reopened.resolveActivation(0, 0).activationId());
                    assertTrue(reopened.semantics(1, 0).orElseThrow().objectKeys().contains(fixture.objectKey()));
                    assertTrue("Generation must not report swallowed errors", RealPackProbeSupport.settleAndDrain(engine).isEmpty());
                } finally {
                    engine.close();
                }
            } finally {
                data.close();
            }
        }
    }

    private static GeneratedSnapshot generateClaimed(IrisEngine engine, Bindings bindings, int chunkX, Fixture fixture)
            throws Exception {
        GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElseThrow();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(chunkX, 0);
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope scope = route.openRuntimeScope()) {
            GeneratedSnapshot result = generatedObjects(engine, chunkX, fixture);
            assertTrue(route.claimGeneratedSemantics());
            SavedTerrainChunk terrain = route.naturalTerrain().orElseThrow();
            bindings.terrain.put(chunkX, terrain);
            TransitionProbe.writeReceipt(engine.getWorld().worldFolder().toPath(), chunkX, 0,
                    NativeTerrainReceipt.encode(terrain, route.activation().activationId(), route.epoch().epochId()));
            return result;
        }
    }

    private static GeneratedSnapshot generatedObjects(Engine engine, int chunkX, Fixture fixture) {
        RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, chunkX, 0);
        Map<Position, ObjectVoxel> result = objectMetadata(engine, chunkX, fixture);
        for (Map.Entry<Position, ObjectVoxel> entry : result.entrySet()) {
            Position position = entry.getKey();
            NativeBlockState state = chunk.blocks().getRaw(position.x() & 15, position.y(), position.z());
            assertNotNull("Object metadata must correspond to a generated block", state);
            assertEquals(entry.getValue().block(), state.key());
        }
        Map<Position, String> blocks = new HashMap<>();
        Map<Position, String> edits = new HashMap<>();
        int objectBlocks = 0;
        int foundations = 0;
        int carvedGround = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < chunk.height(); y++) {
                for (int z = 0; z < 16; z++) {
                    NativeBlockState state = chunk.blocks().getRaw(x, y, z);
                    if (state != null && fixture.materials().contains(state.key().split("\\[", 2)[0])) {
                        objectBlocks++;
                    }
                    if (!fixture.tree()) {
                        Position position = new Position(chunkX * 16 + x, y, z);
                        String block = state == null || state.isAir() ? "minecraft:air" : state.key();
                        blocks.put(position, block);
                        if (state != null && state.key().contains("cobblestone")) {
                            foundations++;
                            edits.put(position, block);
                        }
                        if (y > 1 && y <= 72 && (state == null || state.isAir())) {
                            carvedGround++;
                            edits.put(position, block);
                        }
                    }
                }
            }
        }
        assertEquals("Every generated object block must keep placement metadata", objectBlocks, result.size());
        return new GeneratedSnapshot(result, blocks, edits, foundations, carvedGround);
    }

    private static Map<Position, ObjectVoxel> historicalObjects(IrisEngine engine, Fixture fixture) throws Exception {
        GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElseThrow();
        try (GenerationHistoryRuntimeRouter.SavedChunkMantle saved = router.openSavedChunkMantle(0, 0)) {
            return objectMetadata(saved.mantle().getChunk(0, 0), 0, engine.getHeight(), fixture);
        }
    }

    private static void assertHistoricalEdits(IrisEngine engine, Map<Position, String> edits) throws Exception {
        if (edits.isEmpty()) {
            return;
        }
        GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElseThrow();
        try (GenerationHistoryRuntimeRouter.SavedChunkMantle saved = router.openSavedChunkMantle(0, 0)) {
            MantleChunk<Matter> chunk = saved.mantle().useChunk(0, 0);
            try {
                for (Map.Entry<Position, String> edit : edits.entrySet()) {
                    Position position = edit.getKey();
                    NativeBlockState block = chunk.get(position.x(), position.y(), position.z(), NativeBlockState.class);
                    assertNotNull("Historical carve/foundation must reload at " + position, block);
                    assertEquals("Historical carve/foundation changed at " + position, edit.getValue(),
                            block.isAir() ? "minecraft:air" : block.key());
                }
            } finally {
                chunk.release();
            }
        }
    }

    private static Map<Position, ObjectVoxel> objectMetadata(Engine engine, int chunkX, Fixture fixture) {
        return objectMetadata(engine.getMantle().getMantle().getChunk(chunkX, 0), chunkX, engine.getHeight(), fixture);
    }

    private static Map<Position, ObjectVoxel> objectMetadata(MantleChunk<Matter> chunk, int chunkX, int height, Fixture fixture) {
        Map<Position, ObjectVoxel> result = new HashMap<>();
        chunk.use();
        try {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < 16; z++) {
                        String marker = chunk.get(x, y, z, String.class);
                        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
                        NativeBlockState block = chunk.get(x, y, z, NativeBlockState.class);
                        boolean objectBlock = block != null && fixture.materials().contains(block.key().split("\\[", 2)[0]);
                        if (decoded != null && decoded.objectKey().equals(fixture.objectKey()) || !fixture.tree() && objectBlock) {
                            TreeBlockMaterial material = chunk.get(x, y, z, TreeBlockMaterial.class);
                            assertNotNull("Placement marker must retain its object block", block);
                            if (fixture.tree()) {
                                assertNotNull("Tree material metadata must survive", material);
                                assertTrue(material.matches(block.key()));
                            } else if (block.isStorageChest()) {
                                assertNotNull("Structure storage blocks must have placement metadata", decoded);
                                assertEquals("boundary", decoded.structureKey());
                            }
                            result.put(new Position(chunkX * 16 + x, y, z), new ObjectVoxel(block.key(), marker, material));
                        }
                    }
                }
            }
        } finally {
            chunk.release();
        }
        return result;
    }

    private static Path fixture(String name) throws Exception {
        URL resource = Objects.requireNonNull(ObjectContinuationProbeTest.class.getResource("/transition-packs/" + name));
        return Path.of(resource.toURI());
    }

    private static void copyPack(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
    }

    private record Fixture(String pack, String objectKey, Set<String> materials, boolean tree) {
    }

    private record GeneratedSnapshot(Map<Position, ObjectVoxel> objects, Map<Position, String> blocks,
                                     Map<Position, String> edits, int foundations, int carvedGround) {
    }

    private record Position(int x, int y, int z) {
    }

    private record ObjectVoxel(String block, String marker, TreeBlockMaterial material) {
    }

    private static final class Bindings implements RealPackProbeSupport.RuntimeBindings {
        private final Map<Integer, SavedTerrainChunk> terrain = new HashMap<>();

        @Override
        public IrisPlatform create(File platformRoot) {
            return new StubPlatform(platformRoot);
        }

        @Override
        public void prepare(IrisData data, IrisDimension dimension) {
        }

        @Override
        public GenerationHistory createHistory(RealPackProbeSupport.HistoryRequest request) throws IOException {
            Files.createDirectories(request.worldRoot());
            Path pack = request.data().getDataFolder().toPath();
            return GenerationHistory.create(request.worldRoot(), pack,
                    GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION), request.seed(),
                    GenerationEpochContractFactory.create(request.dimension(), "transition", "iris:object_probe"),
                    GenerationRegistryContract.empty());
        }

        @Override
        public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(Engine engine, int chunkX, int chunkZ) {
            SavedTerrainChunk chunk = chunkZ == 0 ? terrain.get(chunkX) : null;
            return chunk == null ? CompletableFuture.failedFuture(new IOException("Missing historical chunk"))
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
