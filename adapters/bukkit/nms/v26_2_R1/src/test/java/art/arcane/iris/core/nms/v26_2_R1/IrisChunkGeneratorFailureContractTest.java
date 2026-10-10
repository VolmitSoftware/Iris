package art.arcane.iris.core.nms.v26_2_R1;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisChunkGeneratorFailureContractTest {
    @Test
    public void structureLocateDoesNotCatchAndFallThroughToAnotherImplementation() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int locateStart = source.indexOf("public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure");
        int locateEnd = source.indexOf("private HolderSet<Structure> filterReachableStructures", locateStart);
        String locate = source.substring(locateStart, locateEnd);
        int reachabilityStart = source.indexOf("private Set<String> reachableStructureKeys");
        int reachabilityEnd = source.indexOf("protected MapCodec", reachabilityStart);
        String reachability = source.substring(reachabilityStart, reachabilityEnd);
        int bootstrapGate = locate.indexOf("!platformGenerator.shouldGenerateStructures()");
        int generatorIdentity = locate.indexOf("level.getChunkSource().getGenerator() != this");
        int lease = locate.indexOf("requireGenerationLease(\"bukkit_nms_structure_locate\")");
        int nativePrediction = locate.indexOf("NativeStructureVanillaLocator.predict(");

        assertTrue(locate.contains("reached its safety limit"));
        assertTrue(locate.contains("unregistered structure holder"));
        assertTrue(bootstrapGate >= 0);
        assertTrue(generatorIdentity > bootstrapGate);
        assertTrue(lease > generatorIdentity);
        assertTrue(nativePrediction > lease);
        assertFalse(locate.contains("catch (Throwable"));
        assertFalse(locate.contains("IrisLogging.reportError"));
        assertFalse(reachability.contains("catch (Throwable"));
        assertFalse(reachability.contains("reachable = Set.of()"));
    }

    @Test
    public void structureGenerationAbortsInsteadOfLoggingAndContinuing() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int adjustmentStart = source.indexOf("private void adjustGeneratedStructures");
        int adjustmentEnd = source.indexOf("public ChunkGeneratorStructureState createState", adjustmentStart);
        String adjustment = source.substring(adjustmentStart, adjustmentEnd);
        int placementStart = source.indexOf("private void placeVanillaStructures");
        int placementEnd = source.indexOf("private void placeVanillaStructure(", placementStart + 1);
        String placement = source.substring(placementStart, placementEnd);

        assertTrue(adjustment.contains("NativeStructureGenerationException.failure("));
        assertTrue(adjustment.contains("\"vertical adjustment\""));
        assertFalse(adjustment.contains("IrisLogging.reportError"));
        assertTrue(placement.contains("\"resolution\""));
        assertTrue(placement.contains("\"terrain fitting\""));
        assertFalse(placement.contains("\"terrain carving\""));
        assertTrue(placement.contains("\"vegetation cleanup\""));
        assertTrue(placement.contains("\"placement\""));
        assertTrue(placement.contains("because structure generation is disabled outside the pack"));
        assertTrue(placement.contains(".beforePieces("));
        assertTrue(placement.contains("clearIntersectingVegetation"));
        assertTrue(placement.indexOf("clearIntersectingVegetation")
                < placement.indexOf(".beforePieces("));
        assertTrue(placement.indexOf(".beforePieces(")
                < placement.indexOf("for (NativePlacementGroup group"));
        assertTrue(placement.indexOf("fit.afterPieces(")
                > placement.indexOf("for (NativePlacementGroup group"));
        assertFalse(placement.contains("IrisLogging.reportError"));
    }

    @Test
    public void heightmapRuntimeReadsAreGenerationLeased() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");

        assertTrue(source.contains("engine.acquireGenerationLease(\"bukkit_nms_heightmaps\")"));
        assertTrue(source.contains("engine.acquireGenerationLease(\"bukkit_nms_base_height\")"));
        assertTrue(source.contains("engine.acquireGenerationLease(\"bukkit_nms_base_column\")"));
        assertTrue(source.contains("catch (GenerationSessionException e)"));
    }

    @Test
    public void structureReferenceRepairIsGenerationLeased() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int referencesStart = source.indexOf("public void createReferences");
        int referencesEnd = source.indexOf("public CompletableFuture<ChunkAccess> createBiomes", referencesStart);
        String references = source.substring(referencesStart, referencesEnd);

        int nativeStructureGate = references.indexOf("!platformGenerator.shouldGenerateStructures()");
        int generatorIdentity = references.indexOf("runtimeLevel.getChunkSource().getGenerator() != this");
        int stage = references.indexOf("requireGenerationStage(\"bukkit_nms_create_references\")");
        int lease = references.indexOf("requireGenerationLease(\"bukkit_nms_create_references\")");
        int repair = references.indexOf("NativeStructureReferenceRepair.createReferences(");

        assertTrue(nativeStructureGate >= 0);
        assertTrue(generatorIdentity > nativeStructureGate);
        assertTrue(stage > generatorIdentity);
        assertTrue(lease > stage);
        assertTrue(repair > lease);
        assertTrue(references.contains("IrisContext.open(engine, lease.sessionId(), null)"));
        assertFalse(references.contains("delegate.createReferences("));
        assertFalse(references.contains("catch ("));
    }

    @Test
    public void structureGenerationAcquiresBootstrapGateBeforeUsingPublishedState() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int generationStart = source.indexOf("public void createStructures(");
        int generationEnd = source.indexOf("private void adjustGeneratedStructures", generationStart);
        String generation = source.substring(generationStart, generationEnd);
        int gate = generation.indexOf("!platformGenerator.shouldGenerateStructures()");
        int currentGenerator = generation.indexOf(
                "runtimeLevel.getChunkSource().getGenerator() != this");
        int stateIdentity = generation.indexOf(
                "runtimeLevel.getChunkSource().getGeneratorState() != structureState");
        int stage = generation.indexOf(
                "requireGenerationStage(\"bukkit_nms_create_structures\")");
        int lease = generation.indexOf(
                "requireGenerationLease(\"bukkit_nms_create_structures\")");
        int generationCall = generation.indexOf(
                "super.createStructures(registryAccess, structureState");

        assertTrue(gate >= 0);
        assertTrue(currentGenerator > gate);
        assertTrue(stateIdentity > currentGenerator);
        assertTrue(stage > stateIdentity);
        assertTrue(lease > stage);
        assertTrue(generationCall > lease);
        assertTrue(generation.contains("structureState,\n                            structureManager"));
        assertFalse(generation.contains("resolvePublishedStructureState"));
    }

    @Test
    public void nativeRingBootstrapTracksTheExactMinecraftFutures() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int initializeStart = source.indexOf("private CompletableFuture<Void> initializeStructureState(");
        int initializeEnd = source.indexOf("private Map<?, ?> structureRingPositions", initializeStart);
        String initialize = source.substring(initializeStart, initializeEnd);
        int completionStart = source.indexOf(
                "private CompletableFuture<Void> structureRingCompletion", initializeEnd);
        int completionEnd = source.indexOf("private void requireCurrentStructureOwner", completionStart);
        String completion = source.substring(completionStart, completionEnd);

        assertBefore(initialize,
                "structureRingPositions(structureState)",
                "structureState.ensureStructuresGenerated()");
        assertBefore(initialize,
                "structureState.ensureStructuresGenerated()",
                "structureRingCompletion(ringPositions)");
        assertFalse(initialize.contains("catch ("));
        assertTrue(completion.contains("for (Object candidate : ringPositions.values())"));
        assertTrue(completion.contains("CompletableFuture.allOf(completions)"));
        assertTrue(completion.contains("throw new IllegalStateException("));
        assertFalse(completion.contains("join()"));
        assertFalse(completion.contains("get()"));
    }

    @Test
    public void standardActivationClaimsTheExactPublishedStateBeforeInitialization() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int activationStart = source.indexOf("CompletableFuture<Void> activateStudioStructureState(");
        int activationEnd = source.indexOf(
                "private CompletableFuture<Void> startStructureStateBootstrap", activationStart);
        String activation = source.substring(activationStart, activationEnd);
        int bootstrapStart = activationEnd;
        int bootstrapEnd = source.indexOf(
                "private CompletableFuture<Void> initializeStructureState", bootstrapStart);
        String bootstrap = source.substring(bootstrapStart, bootstrapEnd);

        assertTrue(activation.contains("retained.structureState()"));
        assertBefore(activation,
                "retainedStudioStructureState(",
                "claimStudioStructureState(retained)");
        assertBefore(bootstrap,
                "claim,",
                "initializeStructureState(");
        assertBefore(bootstrap,
                "initializeStructureState(",
                "activation.run()");
        assertFalse(activation.contains("publishStructureState("));
        assertFalse(source.contains("ChunkGeneratorStructureState fullState"));
    }

    @Test
    public void terrainWritesPrimeTheWorldgenHeightmapsForEveryChunk() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int fillStart = source.indexOf("public CompletableFuture<ChunkAccess> fillFromNoise");
        int fillEnd = source.indexOf("public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt", fillStart);
        String fill = source.substring(fillStart, fillEnd);
        int decorationStart = source.indexOf("public void addVanillaDecorations");
        int decorationEnd = source.indexOf("public void spawnOriginalMobs", decorationStart);
        String decorations = source.substring(decorationStart, decorationEnd);
        int placementStart = source.indexOf("private void placeVanillaStructures");
        int placementEnd = source.indexOf("private static String nativeStructureBatchContext", placementStart);
        String placement = source.substring(placementStart, placementEnd);

        assertTrue(fill.contains("thenApply"));
        assertTrue(fill.contains("primeWorldgenHeightmaps"));
        assertTrue(decorations.contains("primeWorldgenHeightmaps"));
        assertFalse(decorations.contains("Heightmap.Types.WORLD_SURFACE_WG"));
        assertFalse(decorations.contains("Heightmap.Types.OCEAN_FLOOR_WG"));
        assertEquals(1, occurrences(source, "WorldgenTerrainHeightmaps.primeTerrain("));
        assertTrue(placement.contains("WorldgenTerrainHeightmaps.primeStructurePlacement("));
        assertTrue(placement.indexOf("WorldgenTerrainHeightmaps.primeStructurePlacement(")
                < placement.indexOf(".beforePieces("));
        assertTrue(fill.contains("requireGenerationLease(\"bukkit_nms_chunk_pipeline\")"));
        assertTrue(source.contains("int minY = chunk.getMinY() + 1;"));
    }

    @Test
    public void everyChunkUsesTheProductionGenerationStages() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource")))
                .replace("\r\n", "\n");
        String createBiomes = method(
                source,
                "public CompletableFuture<ChunkAccess> createBiomes",
                "public void buildSurface");
        String buildSurface = method(
                source,
                "public void buildSurface",
                "public void applyCarvers");
        String carvers = method(
                source,
                "public void applyCarvers",
                "public CompletableFuture<ChunkAccess> fillFromNoise");
        String noise = method(
                source,
                "public CompletableFuture<ChunkAccess> fillFromNoise",
                "private static boolean isCancellationFailure");
        String decoration = method(
                source,
                "public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla)",
                "public BiomeGenerationSettings getBiomeGenerationSettings");
        String spawning = method(
                source,
                "public void spawnOriginalMobs",
                "private static WeightedList<MobSpawnSettings.SpawnerData> mergeSpawnTables");
        String baseHeight = method(
                source,
                "public int getBaseHeight",
                "public NoiseColumn getBaseColumn");
        String baseColumn = method(
                source,
                "public NoiseColumn getBaseColumn",
                "private GenerationSessionLease requireGenerationLease");

        assertTrue(createBiomes.contains("requireGenerationStage(\"bukkit_nms_create_biomes\")"));
        assertTrue(createBiomes.contains("customBiomeSource.prepareVisibleBiomeBatch()"));
        assertTrue(createBiomes.contains("getVisibleNoiseBiomeWithActiveGenerationLease("));
        assertTrue(buildSurface.contains("delegate.buildSurface("));
        assertTrue(carvers.contains("delegate.applyCarvers("));
        assertTrue(noise.contains("requireNoiseGenerationStage("));
        assertTrue(noise.contains("\"bukkit_nms_chunk_pipeline\""));
        assertTrue(noise.contains("stage.close()"));
        assertTrue(noise.contains("completion.cancel(false)"));
        assertTrue(decoration.contains("requireGenerationStage(\"bukkit_nms_biome_decoration\")"));
        assertTrue(spawning.contains("NaturalSpawner.spawnMobsForChunkGeneration("));
        assertTrue(baseHeight.contains("engine.getHeight("));
        assertTrue(baseColumn.contains("engine.getHeight("));
        assertFalse(source.contains("synthetic_entry"));
        assertFalse(source.contains("StudioEntryChunkGenerator"));
    }

    @Test
    public void fillFromNoiseLeaseSpansTheDelegateAndHeightmapPipeline() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int fillStart = source.indexOf("public CompletableFuture<ChunkAccess> fillFromNoise");
        int fillEnd = source.indexOf("private static boolean isCancellationFailure", fillStart);
        String fill = source.substring(fillStart, fillEnd);
        int productionStart = fill.indexOf(
                "        BukkitChunkGenerator.GenerationStagePermit stage = requireNoiseGenerationStage(",
                0);
        assertTrue(productionStart >= 0);
        String productionFill = fill.substring(productionStart);
        int completionStart = productionFill.indexOf("pipeline.whenComplete(");
        int completionEnd = productionFill.indexOf("            return completion;", completionStart);
        assertTrue(completionStart >= 0);
        assertTrue(completionEnd > completionStart);
        String completion = productionFill.substring(completionStart, completionEnd);

        assertBefore(productionFill,
                "BukkitChunkGenerator.GenerationStagePermit stage = requireNoiseGenerationStage(",
                "GenerationSessionLease lease");
        assertBefore(productionFill,
                "requireNoiseGenerationStage(",
                "\"bukkit_nms_chunk_pipeline\")");
        assertBefore(productionFill,
                "lease = requireGenerationLease(\"bukkit_nms_chunk_pipeline\")",
                ".fillFromNoise(blender, randomstate, structuremanager, ichunkaccess)");
        assertTrue(productionFill.contains("IrisContext.open(engine, lease.sessionId(), null)"));
        assertBefore(productionFill, "primeWorldgenHeightmaps(filled)", "pipeline.whenComplete(");
        assertTrue(productionFill.contains("CompletableFuture<ChunkAccess> completion = new CompletableFuture<>()"));
        assertBefore(completion, "boolean cancelled = isCancellationFailure(failure);", "lease.close();");
        assertBefore(completion, "lease.close();", "stage.close();");
        assertBefore(completion, "stage.close();", "completion.complete(filled)");
        assertBefore(completion, "stage.close();", "completion.cancel(false)");
        assertBefore(completion, "stage.close();", "completion.completeExceptionally(failure)");
        assertTrue(completion.contains("else if (cancelled)"));
        assertFalse(completion.contains("pipeline.isCancelled()"));
        assertFalse(completion.contains("finally"));
        assertTrue(productionFill.contains("catch (RuntimeException | Error failure)"));
        assertTrue(productionFill.contains("lease.close();\n            stage.close();\n            throw failure;"));
        assertTrue(productionFill.contains("return completion;"));
        assertFalse(productionFill.contains("return pipeline;"));
        assertFalse(productionFill.contains("pipeline.cancel("));
        assertFalse(productionFill.contains("bukkit_nms_worldgen_heightmaps"));
    }

    @Test
    public void releasedNoiseAdmissionLetsSynchronousDependentWaitForQueuedExclusive() throws Exception {
        Semaphore admission = new Semaphore(1, true);
        admission.acquire();
        CountDownLatch exclusiveEntered = new CountDownLatch(1);
        CountDownLatch releaseExclusive = new CountDownLatch(1);
        AtomicBoolean dependentFinished = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        boolean stageReleased = false;

        try {
            Future<?> exclusive = executor.submit(() -> {
                boolean acquired = false;
                try {
                    admission.acquire();
                    acquired = true;
                    exclusiveEntered.countDown();
                    assertTrue(releaseExclusive.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    if (acquired) {
                        admission.release();
                    }
                }
            });
            awaitQueueLength(admission, 1);
            CompletableFuture<Void> outward = new CompletableFuture<>();
            outward.thenRun(() -> {
                try {
                    assertTrue(exclusiveEntered.await(2, TimeUnit.SECONDS));
                    dependentFinished.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });

            admission.release();
            stageReleased = true;
            assertTrue(outward.complete(null));
            assertTrue(dependentFinished.get());
            releaseExclusive.countDown();
            exclusive.get(2, TimeUnit.SECONDS);
            assertEquals(1, admission.availablePermits());
        } finally {
            if (!stageReleased) {
                admission.release();
            }
            releaseExclusive.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void transformedDelegateCancellationIsDetectedThroughItsCompletionFailure() throws IOException {
        CompletableFuture<Void> delegate = new CompletableFuture<>();
        CompletableFuture<Void> transformed = delegate.thenApply(value -> value);

        assertTrue(delegate.cancel(false));
        assertFalse(transformed.isCancelled());
        CompletionException failure = assertThrows(CompletionException.class, transformed::join);
        assertTrue(failure.getCause() instanceof CancellationException);

        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        String cancellation = method(
                source,
                "private static boolean isCancellationFailure",
                "private void primeWorldgenHeightmaps");
        assertTrue(cancellation.contains("current instanceof CompletionException"));
        assertTrue(cancellation.contains("current instanceof CancellationException"));
    }

    @Test
    public void noiseAdmissionCanPrepareStudioBeforeAcquiringTheGenerationLease() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        String fill = method(
                source,
                "public CompletableFuture<ChunkAccess> fillFromNoise",
                "private static boolean isCancellationFailure");
        String admission = method(
                source,
                "private BukkitChunkGenerator.GenerationStagePermit requireNoiseGenerationStage",
                "public Optional<Identifier> getTypeNameForDataFixer");

        assertBefore(fill,
                "requireNoiseGenerationStage(",
                "requireGenerationLease(\"bukkit_nms_chunk_pipeline\")");
        assertTrue(admission.contains("platformGenerator.acquireNoiseGenerationStage("));
        assertBefore(admission, "engine,", "chunkPos.x(),");
        assertBefore(admission, "chunkPos.x(),", "chunkPos.z(),");
        assertFalse(fill.contains("requireGenerationStage(\"bukkit_nms_chunk_pipeline\")"));
    }

    @Test
    public void everyTopLevelMoonriseStageEntersTheFairGateBeforeItsGenerationLease() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        String structures = method(source, "public void createStructures(", "private void adjustGeneratedStructures");
        String references = method(source, "public void createReferences(", "public CompletableFuture<ChunkAccess> createBiomes");
        String biomes = method(source, "public CompletableFuture<ChunkAccess> createBiomes", "public void buildSurface");
        String noise = method(source, "public CompletableFuture<ChunkAccess> fillFromNoise", "private static boolean isCancellationFailure");
        String decoration = method(source,
                "public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla)",
                "public BiomeGenerationSettings getBiomeGenerationSettings");

        assertStageBeforeLease(structures, "bukkit_nms_create_structures");
        assertStageBeforeLease(references, "bukkit_nms_create_references");
        assertStageBeforeLease(biomes, "bukkit_nms_create_biomes");
        assertBefore(noise,
                "requireNoiseGenerationStage(",
                "requireGenerationLease(\"bukkit_nms_chunk_pipeline\")");
        assertStageBeforeLease(decoration, "bukkit_nms_biome_decoration");
        assertBefore(decoration,
                "requireGenerationLease(\"bukkit_nms_biome_decoration\")",
                "importedFeatures.prepare(generatoraccessseed)");
        assertEquals(4, occurrences(source, "requireGenerationStage(\"bukkit_nms_"));
        assertEquals(2, occurrences(source, "requireNoiseGenerationStage("));
        assertFalse(source.contains("GenerationSessionManager"));
    }

    @Test
    public void worldgenHeightmapPrimingLivesInTheSharedNativegenSources() throws IOException {
        Path nativegen = Path.of(System.getProperty("iris.nativeStructureFitBridgeSource")).getParent();
        Path shared = nativegen.resolve("WorldgenTerrainHeightmaps.java");

        assertTrue("Worldgen heightmap priming must be shared with the modded loaders through "
                + nativegen, Files.isRegularFile(shared));

        String heightmaps = Files.readString(shared).replace("\r\n", "\n");

        assertTrue(heightmaps.contains("package art.arcane.iris.nativegen;"));
        assertTrue(heightmaps.contains("public static void primeTerrain("));
        assertTrue(heightmaps.contains("public static void primeStructurePlacement("));
        assertFalse(heightmaps.contains("org.bukkit"));
        assertFalse(heightmaps.contains("craftbukkit"));

        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");

        assertTrue(source.contains("import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;"));
    }

    @Test
    public void onlySidecarOwnedInjectedStartsUsePersistedPlacementPolicy() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int placementStart = source.indexOf("private void placeVanillaStructures");
        int placementEnd = source.indexOf("private static String nativeStructureBatchContext", placementStart);
        String placement = source.substring(placementStart, placementEnd);
        int ownershipResolution = placement.indexOf(
                "NativeStructureOwnershipRecovery.resolve(");
        int persistedDecision = placement.indexOf("ownership.restoredMemo()", ownershipResolution);
        int adjustmentStart = source.indexOf("private void adjustGeneratedStructures");
        int adjustmentEnd = source.indexOf("public ChunkGeneratorStructureState createState", adjustmentStart);
        String adjustment = source.substring(adjustmentStart, adjustmentEnd);
        Path nativegen = Path.of(System.getProperty("iris.nativeStructureFitBridgeSource")).getParent();
        String factory = Files.readString(nativegen.resolve("NativeStructureFactory.java")).replace("\r\n", "\n");
        String injector = Files.readString(nativegen.resolve("NativeStructureStartInjector.java")).replace("\r\n", "\n");
        String recovery = Files.readString(nativegen.resolve("NativeStructureOwnershipRecovery.java")).replace("\r\n", "\n");
        int ownershipRecord = injector.indexOf("NativeStructureOwnershipStore.record(");
        int startPublication = injector.indexOf(
                "context.structureManager().setStartForStructure(", ownershipRecord);

        assertTrue(ownershipResolution >= 0);
        assertTrue(persistedDecision > ownershipResolution);
        assertTrue(recovery.contains("NativeStructureOwnershipStore.findPersisted("));
        assertTrue(recovery.contains("NativeStructureOwnershipStore.record("));
        assertTrue(ownershipRecord >= 0);
        assertTrue(startPublication > ownershipRecord);
        assertTrue(factory.contains("NativeStructureReferenceEnvelope.wrapForPublication("));
        assertTrue(adjustment.contains("NativeStructureReferenceEnvelope.wrapForPublication("));
        assertFalse(source.contains("wrapManaged("));
        assertFalse(source.contains("isIrisManagedStart("));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static void awaitQueueLength(Semaphore semaphore, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (semaphore.getQueueLength() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue("Expected at least " + expected + " queued semaphore threads",
                semaphore.getQueueLength() >= expected);
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue("Missing source contract token: " + startToken, start >= 0);
        assertTrue("Missing source contract token: " + endToken, end > start);
        return source.substring(start, end);
    }

    private static void assertStageBeforeLease(String source, String operation) {
        assertBefore(source,
                "requireGenerationStage(\"" + operation + "\")",
                "requireGenerationLease(\"" + operation + "\")");
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue("Missing source contract token: " + first, firstIndex >= 0);
        assertTrue("Missing source contract token: " + second, secondIndex >= 0);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }

    @Test
    public void vanillaChunkGenerationMobsUseTheVisibleBiomesVanillaDerivative() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int spawnStart = source.indexOf("public void spawnOriginalMobs");
        int spawnEnd = source.indexOf("private static WeightedList", spawnStart);
        String spawn = source.substring(spawnStart, spawnEnd);

        assertTrue(spawn.contains("customBiomeSource.getVanillaSpawnBiome(visibleBiome)"));
        assertTrue(spawn.contains("NaturalSpawner.spawnMobsForChunkGeneration("));
        assertTrue(spawn.contains("region.getBiome(center.getWorldPosition().atY(region.getMaxY()))"));
        assertTrue(spawn.contains("new LegacyRandomSource(RandomSupport.generateUniqueSeed())"));
        assertTrue(spawn.contains("random.setDecorationSeed(region.getSeed()"));
        assertFalse(spawn.contains("delegate.spawnOriginalMobs"));
    }
}
