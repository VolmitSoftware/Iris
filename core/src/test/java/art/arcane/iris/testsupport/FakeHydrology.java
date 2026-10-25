package art.arcane.iris.testsupport;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntBinaryOperator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class FakeHydrology implements AutoCloseable {
    public static final long SEED = 1337L;
    public static final long POND = 0x504f4e44L;
    public static final String FAILURE = "fake window planner exploded";
    private static final long TIMEOUT_SECONDS = 60L;
    private static final int NODES = 8;
    private static final int ORIGIN = 2;

    public final AtomicBoolean failing = new AtomicBoolean();
    public final AtomicInteger windowCalls = new AtomicInteger();
    public final HydrologyRuntime runtime;
    private final int pondLevel;
    private final List<Reach> reaches;
    private final List<Pond> ponds;
    private final List<Pond> lakes;
    private final List<SeaInlet> inlets;
    private final ExecutorService pool;
    private volatile CountDownLatch gate;

    public FakeHydrology(BooleanSupplier mainThread, IntBinaryOperator naturalHeight, int seaLevel, int pondLevel) {
        this(mainThread, naturalHeight, seaLevel, pondLevel, List.of(), null, List.of(), List.of());
    }

    public FakeHydrology(BooleanSupplier mainThread, IntBinaryOperator naturalHeight, int seaLevel, List<Reach> reaches, List<Pond> ponds) {
        this(mainThread, naturalHeight, seaLevel, 0, reaches, ponds, List.of(), List.of());
    }

    public FakeHydrology(BooleanSupplier mainThread, IntBinaryOperator naturalHeight, int seaLevel, List<Reach> reaches, List<Pond> ponds,
                         List<Pond> lakes) {
        this(mainThread, naturalHeight, seaLevel, 0, reaches, ponds, lakes, List.of());
    }

    public FakeHydrology(BooleanSupplier mainThread, IntBinaryOperator naturalHeight, int seaLevel, List<Reach> reaches, List<Pond> ponds,
                         List<Pond> lakes, List<SeaInlet> inlets) {
        this(mainThread, naturalHeight, seaLevel, 0, reaches, ponds, lakes, inlets);
    }

    private FakeHydrology(BooleanSupplier mainThread, IntBinaryOperator naturalHeight, int seaLevel, int pondLevel, List<Reach> reaches,
                          List<Pond> ponds, List<Pond> lakes, List<SeaInlet> inlets) {
        this.pondLevel = pondLevel;
        this.reaches = List.copyOf(reaches);
        this.ponds = ponds == null ? List.of(pond()) : List.copyOf(ponds);
        this.lakes = List.copyOf(lakes);
        this.inlets = List.copyOf(inlets);
        AtomicInteger threads = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "fake-hydrology-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        HydrologySettings settings = settings(seaLevel);
        HydrologyPlanCache cache = new HydrologyPlanCache(settings, this::region, this::window, pool, mainThread);
        this.runtime = new HydrologyRuntime(settings, cache, naturalHeight, mainThread, System::nanoTime);
    }

    public static HydrologySettings settings(int seaLevel) {
        return HydrologySettings.compile(new IrisHydrology().setEnabled(true).setPersist(false).setPrefetchRings(0), SEED, seaLevel, 0, 384);
    }

    public static HydrologyRuntime disabled() {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        return HydrologyRuntime.create(engine, null);
    }

    public void hold() {
        gate = new CountDownLatch(1);
    }

    public void release() {
        CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    public ChunkWaterRaster awaitSnapshot(int chunkX, int chunkZ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        ChunkWaterRaster raster = runtime.rasterSnapshot(chunkX, chunkZ);
        while (raster.provisional()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("The queued water raster for chunk " + chunkX + "," + chunkZ + " never landed");
            }
            Thread.sleep(10L);
            raster = runtime.rasterSnapshot(chunkX, chunkZ);
        }
        return raster;
    }

    @Override
    public void close() {
        release();
        runtime.close();
        pool.shutdownNow();
    }

    private RegionPlan region(RegionKey key) {
        return new RegionPlan(key, List.of(), key.equals(new RegionKey(0, 0)) ? lakes : List.of());
    }

    private WindowPlan window(WindowKey key, List<RegionPlan> regions) {
        windowCalls.incrementAndGet();
        await(gate);
        if (!key.equals(new WindowKey(0, 0))) {
            return new WindowPlan(key, List.of(), List.of(), List.of());
        }
        if (failing.get()) {
            throw new IllegalStateException(FAILURE);
        }
        return new WindowPlan(key, reaches, ponds, inlets);
    }

    private Pond pond() {
        long[] bits = new long[(NODES * NODES + 63) >>> 6];
        for (int index = 0; index < NODES * NODES; index++) {
            bits[index >>> 6] |= 1L << index;
        }
        PondMask mask = new PondMask(ORIGIN, ORIGIN, 4, NODES, NODES, bits);
        int centre = ORIGIN + (NODES - 1) * 2;
        return new Pond(POND, PondKind.NATURAL, pondLevel, pondLevel - 6, ORIGIN + NODES * 4, ORIGIN, pondLevel + 1f, mask, 0L, 0, "water",
                centre, centre);
    }

    private static void await(CountDownLatch latch) {
        if (latch == null) {
            return;
        }
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The fake hydrology gate never opened");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Fake hydrology planning was interrupted");
        }
    }
}
