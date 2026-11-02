package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.query.HydrologySearchTimeoutException;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyFeatureSearchTest {
    private static final long SEED = 1337L;
    private static final long CHANNEL = 0x4348414eL;
    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(30L);

    private final List<ExecutorService> pools = new ArrayList<>();
    private final List<HydrologyPlanCache> caches = new ArrayList<>();
    private final CountDownLatch gate = new CountDownLatch(1);

    @After
    public void shutDown() {
        gate.countDown();
        for (HydrologyPlanCache cache : caches) {
            cache.close();
        }
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    @Test(timeout = 20_000L)
    public void disabledTypeAnswersImmediately() throws Exception {
        HydrologySettings settings = settings(config -> config.getLakes().setEnabled(false));
        Planners planners = new Planners(gate);
        HydrologyPlanCache cache = cache(settings, planners);

        assertEquals(Optional.empty(), search(settings, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.LAKE))
                .nearest(LONG_TIMEOUT, () -> true));
        assertEquals(Optional.empty(), search(settings, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.LAKE, HydrologyFeatureType.DAM))
                .nearest(LONG_TIMEOUT, () -> true));
        assertEquals(0, planners.calls.get());
        assertEquals(Optional.of("hydrology.lakes is disabled"), HydrologyFeatureSearch.disabledReason(settings, HydrologyFeatureType.LAKE));
        assertEquals(Optional.of("hydrology.ponds.dams is disabled"), HydrologyFeatureSearch.disabledReason(settings, HydrologyFeatureType.DAM));
        assertEquals(Optional.empty(), HydrologyFeatureSearch.disabledReason(settings, HydrologyFeatureType.POND));
        assertEquals(Optional.empty(), HydrologyFeatureSearch.disabledReason(settings, HydrologyFeatureType.WATERFALL));

        HydrologySettings dry = settings(config -> {
            config.getPonds().getNatural().setEnabled(false);
            config.getPonds().getSeeded().setEnabled(false);
            config.getPonds().getDams().setEnabled(true);
            config.getUnderground().getSeaInlets().setEnabled(false);
            config.getUnderground().getCavePools().setEnabled(false);
        });
        String ponds = "hydrology.ponds.natural and hydrology.ponds.seeded are disabled";
        assertEquals(Optional.of(ponds), HydrologyFeatureSearch.disabledReason(dry, HydrologyFeatureType.POND));
        assertEquals(Optional.of(ponds), HydrologyFeatureSearch.disabledReason(dry, HydrologyFeatureType.DAM));
        assertEquals(Optional.of("hydrology.underground.seaInlets is disabled"),
                HydrologyFeatureSearch.disabledReason(dry, HydrologyFeatureType.SEA_INLET));
        assertEquals(Optional.of("hydrology.underground.cavePools is disabled"),
                HydrologyFeatureSearch.disabledReason(dry, HydrologyFeatureType.CAVE_POOL));
        assertEquals(Optional.empty(), HydrologyFeatureSearch.disabledReason(dry, HydrologyFeatureType.LAKE));
        assertEquals(Optional.of("hydrology is off"), FakeHydrology.disabled().unavailable(HydrologyFeatureType.RIVER));
    }

    @Test(timeout = 20_000L)
    public void searchStopsAtTheTimeout() {
        HydrologySettings settings = settings(config -> {
        });
        Planners planners = new Planners(gate);
        HydrologyPlanCache cache = cache(settings, planners);
        Duration timeout = Duration.ofMillis(200L);
        HydrologyFeatureSearch search = search(settings, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.POND));

        long started = System.nanoTime();
        HydrologySearchTimeoutException stopped = assertThrows(HydrologySearchTimeoutException.class,
                () -> search.nearest(timeout, () -> true));

        assertTrue(System.nanoTime() - started >= timeout.toNanos());
        assertTrue(planners.calls.get() > 0);
        assertEquals(Optional.empty(), stopped.nearestSoFar());
    }

    @Test(timeout = 20_000L)
    public void timedOutSearchKeepsTheNearestFeatureFoundSoFar() {
        HydrologySettings settings = settings(config -> {
        });
        Planners planners = new Planners(gate);
        planners.channelTier = settings.tiers().getFirst();
        HydrologyPlanCache cache = cache(settings, planners);

        HydrologySearchTimeoutException stopped = assertThrows(HydrologySearchTimeoutException.class,
                () -> search(settings, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.STREAM)).nearest(Duration.ofMillis(200L), () -> true));

        assertEquals(Optional.of(new HydrologyFeatureRef(CHANNEL, HydrologyFeatureType.STREAM, 100, 70, 100)), stopped.nearestSoFar());
    }

    @Test(timeout = 20_000L)
    public void cancelledSearchStopsWaitingForPlans() {
        HydrologySettings settings = settings(config -> {
        });
        Planners planners = new Planners(gate);
        HydrologyPlanCache cache = cache(settings, planners);
        AtomicInteger checks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> search(settings, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.RIVER))
                .nearest(LONG_TIMEOUT, () -> checks.incrementAndGet() < 3));
        assertTrue(checks.get() >= 3);
    }

    @Test(timeout = 20_000L)
    public void oneTierPacksMatchStream() throws Exception {
        HydrologySettings oneTier = settings(config -> config.setTiers(new KList<>(new IrisHydrologyTier())));
        HydrologyTier only = oneTier.tiers().getFirst();
        Planners planners = new Planners(null);
        planners.channelTier = only;
        HydrologyPlanCache cache = cache(oneTier, planners);

        assertEquals(HydrologyFeatureType.STREAM, HydrologyFeatureSearch.channelType(oneTier, only));
        assertEquals(new HydrologyFeatureRef(CHANNEL, HydrologyFeatureType.STREAM, 100, 70, 100),
                search(oneTier, cache, HydrologyFeatureQuery.of(HydrologyFeatureType.STREAM)).nearest(LONG_TIMEOUT, () -> true).orElseThrow());
        assertEquals(Optional.of("it has no river tier"), HydrologyFeatureSearch.disabledReason(oneTier, HydrologyFeatureType.RIVER));
        assertEquals(Optional.of("it has no major river tier"), HydrologyFeatureSearch.disabledReason(oneTier, HydrologyFeatureType.MAJOR_RIVER));

        HydrologySettings creek = settings(config -> config.setTiers(new KList<>(new IrisHydrologyTier().setName("creek"))));
        assertEquals(HydrologyFeatureType.STREAM, HydrologyFeatureSearch.channelType(creek, creek.tiers().getFirst()));

        HydrologySettings twoTier = settings(config -> config.setTiers(new KList<>(new IrisHydrologyTier(),
                new IrisHydrologyTier().setName("river").setMinCatchment(20000D))));
        assertEquals(HydrologyFeatureType.STREAM, HydrologyFeatureSearch.channelType(twoTier, twoTier.tiers().getFirst()));
        assertEquals(HydrologyFeatureType.RIVER, HydrologyFeatureSearch.channelType(twoTier, twoTier.majorTier()));
        assertEquals(Optional.of("it has no major river tier"), HydrologyFeatureSearch.disabledReason(twoTier, HydrologyFeatureType.MAJOR_RIVER));

        HydrologySettings ranked = settings(config -> config.setTiers(new KList<>(new IrisHydrologyTier().setName("brook"),
                new IrisHydrologyTier().setName("channel").setMinCatchment(20000D),
                new IrisHydrologyTier().setName("trunk").setMinCatchment(320000D))));
        assertEquals(HydrologyFeatureType.STREAM, HydrologyFeatureSearch.channelType(ranked, ranked.tiers().get(0)));
        assertEquals(HydrologyFeatureType.RIVER, HydrologyFeatureSearch.channelType(ranked, ranked.tiers().get(1)));
        assertEquals(HydrologyFeatureType.MAJOR_RIVER, HydrologyFeatureSearch.channelType(ranked, ranked.tiers().get(2)));
    }

    private HydrologyPlanCache cache(HydrologySettings settings, Planners planners) {
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "feature-search-planner");
            thread.setDaemon(true);
            return thread;
        });
        pools.add(pool);
        HydrologyPlanCache cache = new HydrologyPlanCache(settings, planners::region, planners::window, pool, () -> false);
        caches.add(cache);
        return cache;
    }

    private static HydrologyFeatureSearch search(HydrologySettings settings, HydrologyPlanCache cache, HydrologyFeatureQuery query) {
        return new HydrologyFeatureSearch(settings, cache, 0, 0, query, feature -> true, 2048);
    }

    private static HydrologySettings settings(Consumer<IrisHydrology> change) {
        IrisHydrology config = new IrisHydrology().setEnabled(true).setPersist(false);
        change.accept(config);
        return HydrologySettings.compile(config, SEED, 40, 0, 384);
    }

    private static final class Planners {
        private final CountDownLatch gate;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile HydrologyTier channelTier;

        private Planners(CountDownLatch gate) {
            this.gate = gate;
        }

        RegionPlan region(RegionKey key) {
            calls.incrementAndGet();
            return new RegionPlan(key, List.of(), List.of());
        }

        WindowPlan window(WindowKey key, List<RegionPlan> plans) {
            calls.incrementAndGet();
            if (channelTier != null && key.equals(new WindowKey(0, 0))) {
                return new WindowPlan(key, List.of(channel(channelTier)), List.of(), List.of());
            }
            hold();
            return new WindowPlan(key, List.of(), List.of(), List.of());
        }

        private void hold() {
            if (gate == null) {
                return;
            }
            try {
                gate.await(60L, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Planning interrupted.");
            }
        }

        private static Reach channel(HydrologyTier tier) {
            int points = 3;
            float[] xs = new float[points];
            float[] zs = new float[points];
            int[] grade = new int[points];
            float[] halfWidth = new float[points];
            float[] depth = new float[points];
            ReachStyle[] style = new ReachStyle[points];
            for (int index = 0; index < points; index++) {
                xs[index] = 100f + index * 50f;
                zs[index] = 100f;
                grade[index] = 70;
                halfWidth[index] = 1f;
                depth[index] = 1f;
                style[index] = ReachStyle.RUN;
            }
            return new Reach(CHANNEL, tier, 0L, xs, zs, grade, halfWidth, depth, style, 2000f);
        }
    }
}
