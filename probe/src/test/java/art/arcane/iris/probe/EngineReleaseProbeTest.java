package art.arcane.iris.probe;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixed;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixedRule;
import art.arcane.iris.testsupport.IrisRuntimeState;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A closed engine must become unreachable while everything that outlives it stays alive: the pack objects it read and
 * the pooled generation workers that ran it. Anything engine-derived parked on either one pins the engine and, in a
 * server, the whole world it generated.
 */
public class EngineReleaseProbeTest {
    private static final long SEED = 7_331L;
    private static final String DIMENSION = "release";
    private static final int CHUNK_SPAN = 4;
    private static final int COLLECTION_ATTEMPTS = 40;
    private static final long COLLECTION_PAUSE_MILLIS = 50L;
    private static final long WORKER_TASK_TIMEOUT_SECONDS = 60L;
    private static final String HEAP_DUMP_ENVIRONMENT = "IRIS_ENGINE_RELEASE_HEAP_DUMP";

    @Rule
    public final ExpectedToFailUntilFixedRule expectedFailures = new ExpectedToFailUntilFixedRule();

    @After
    public void resetRuntime() {
        IrisRuntimeState.reset();
    }

    @Test(timeout = 300_000)
    @ExpectedToFailUntilFixed("pack objects and worker threads still cache engine-derived state")
    public void closedEngineIsCollectableWhilePackObjectsAndWorkersStayAlive() throws Exception {
        List<Object> survivingPackObjects = new ArrayList<>();
        WeakReference<Engine> closedEngine;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                fixturePack().toFile(), DIMENSION, "[engine-release]")) {
            closedEngine = generateAndClose(workspace, survivingPackObjects);
        }
        ForkJoinPool workers = generationWorkers();
        assertTrue("Generation workers must still be alive for the check to mean anything",
                workers.getPoolSize() > 0);
        boolean released = collected(closedEngine);
        if (!released) {
            dumpHeapWhenRequested();
        }
        assertTrue("A closed engine is still reachable from pack objects or worker threads", released);
        assertTrue("Pack objects must outlive the engine", !survivingPackObjects.isEmpty());
    }

    private static WeakReference<Engine> generateAndClose(
            RealPackProbeSupport.Workspace workspace,
            List<Object> survivingPackObjects
    ) throws Exception {
        try (RealPackProbeSupport.EngineSession session = workspace.openEngine(SEED, true, "release")) {
            Engine engine = session.engine();
            retainPackObjects(engine.getData(), engine.getDimension(), survivingPackObjects);
            generateOnCallerAndWorkers(engine);
            RealPackProbeSupport.settleAndDrain(engine);
            return new WeakReference<>(engine);
        }
    }

    private static void retainPackObjects(IrisData data, IrisDimension dimension, List<Object> survivors) {
        survivors.add(dimension);
        for (String regionKey : dimension.getRegions()) {
            IrisRegion region = data.getRegionLoader().load(regionKey);
            assertNotNull("Fixture region " + regionKey, region);
            survivors.add(region);
            for (String biomeKey : region.getLandBiomes()) {
                IrisBiome biome = data.getBiomeLoader().load(biomeKey);
                assertNotNull("Fixture biome " + biomeKey, biome);
                survivors.add(biome);
            }
        }
        IrisGenerator generator = data.getGeneratorLoader().load("terrain");
        assertNotNull("Fixture generator", generator);
        survivors.add(generator);
    }

    private static void generateOnCallerAndWorkers(Engine engine) throws Exception {
        List<Future<?>> workerChunks = new ArrayList<>();
        for (int chunkX = 0; chunkX < CHUNK_SPAN; chunkX++) {
            for (int chunkZ = 0; chunkZ < CHUNK_SPAN; chunkZ++) {
                if (((chunkX + chunkZ) & 1) == 0) {
                    RealPackProbeSupport.generateChunk(engine, chunkX, chunkZ);
                    continue;
                }
                int x = chunkX;
                int z = chunkZ;
                workerChunks.add(MultiBurst.burst.submit(() -> RealPackProbeSupport.generateChunk(engine, x, z)));
            }
        }
        for (Future<?> chunk : workerChunks) {
            chunk.get(WORKER_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static ForkJoinPool generationWorkers() throws Exception {
        return MultiBurst.burst.submit((Callable<ForkJoinPool>) ForkJoinTask::getPool)
                .get(WORKER_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static boolean collected(WeakReference<?> reference) throws InterruptedException {
        for (int attempt = 0; attempt < COLLECTION_ATTEMPTS && reference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(COLLECTION_PAUSE_MILLIS);
        }
        return reference.get() == null;
    }

    private static void dumpHeapWhenRequested() throws Exception {
        String destination = System.getenv(HEAP_DUMP_ENVIRONMENT);
        if (destination == null || destination.isBlank()) {
            return;
        }
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(destination, true);
    }

    private static Path fixturePack() throws Exception {
        URL resource = Objects.requireNonNull(EngineReleaseProbeTest.class.getResource("/engine-release"));
        return Path.of(resource.toURI());
    }
}
