package art.arcane.iris.integration;

import art.arcane.iris.api.pregen.IrisPregenPhase;
import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.iris.api.terrain.IrisTerrainService;
import org.bukkit.World;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class IrisPapiStateTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String DASH = "---";
    private static final long POSITION_INTERVAL = IrisPapiState.POSITION_INTERVAL_MS;

    private static IrisPregenProgress progress(double percent, long etaMillis, boolean paused) {
        return new IrisPregenProgress(
                "sandbox",
                "sandbox:identity",
                percent,
                1234L,
                4096L,
                2862L,
                0L,
                12.5D,
                etaMillis,
                60_000L,
                "async",
                paused);
    }

    @Test
    public void availableReportsWhetherTheTerrainServiceIsRegistered() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        IrisPapiState absent = new IrisPapiState(() -> null, new IrisPapiTestSupport.Clock());
        IrisPapiState present = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        assertEquals("false", absent.available(PLAYER));
        assertEquals("true", present.available(PLAYER));
    }

    @Test
    public void worldKeysAreUnavailableUntilAPositionIsTracked() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        assertEquals("false", state.worldAvailable(PLAYER));
        assertEquals(DASH, state.biome(PLAYER));
        assertEquals(DASH, state.biomeKey(PLAYER));
        assertEquals(DASH, state.region(PLAYER));
        assertEquals(DASH, state.regionKey(PLAYER));
        assertEquals(DASH, state.dimension(PLAYER));
        assertEquals(0, terrain.builds());
    }

    @Test
    public void worldKeysResolveTheBiomeRegionAndDimensionAtTheTrackedColumn() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        state.trackPosition(PLAYER, world, 128, -512);

        assertEquals("true", state.worldAvailable(PLAYER));
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));
        assertEquals("desert/hot-dunes", state.biomeKey(PLAYER));
        assertEquals("Scorched Expanse", state.region(PLAYER));
        assertEquals("scorched", state.regionKey(PLAYER));
        assertEquals("overworld", state.dimension(PLAYER));
    }

    @Test
    public void aNonIrisWorldAnswersFalseAndDashesWithoutQueryingTerrain() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World hub = IrisPapiTestSupport.world("hub");
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        state.trackPosition(PLAYER, hub, 0, 0);

        assertEquals("false", state.worldAvailable(PLAYER));
        assertEquals(DASH, state.biome(PLAYER));
        assertEquals(DASH, state.biomeKey(PLAYER));
        assertEquals(DASH, state.region(PLAYER));
        assertEquals(DASH, state.regionKey(PLAYER));
        assertEquals(DASH, state.dimension(PLAYER));
        assertEquals(0, terrain.builds());
    }

    @Test
    public void aWholeBoardOfKeysBuildsTheViewExactlyOncePerSecond() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);

        for (int pass = 0; pass < 6; pass++) {
            state.worldAvailable(PLAYER);
            state.biome(PLAYER);
            state.biomeKey(PLAYER);
            state.region(PLAYER);
            state.regionKey(PLAYER);
            state.dimension(PLAYER);
        }

        assertEquals(1, terrain.builds());
    }

    @Test
    public void theViewIsRebuiltOnceTheOneSecondTtlElapses() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        clock.advance(IrisPapiState.VIEW_TTL_MS);
        assertEquals("Frozen Shelf", state.biome(PLAYER));
        assertEquals(2, terrain.builds());
    }

    @Test
    public void asecondColumnWithinTheSameSecondDoesNotRepublishThePosition() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");

        for (int step = 1; step <= 30; step++) {
            clock.advance(40L);
            state.trackPosition(PLAYER, world, 10 + step, 10);
            state.biome(PLAYER);
        }

        assertEquals("a sprinting player must not force more than one view build per second",
                2, terrain.builds());
    }

    @Test
    public void aMoveInsideTheSameBlockColumnNeverRepublishesThePosition() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);
        clock.advance(POSITION_INTERVAL / 2L);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");
        clock.advance(POSITION_INTERVAL / 2L);
        state.trackPosition(PLAYER, world, 10, 10);
        clock.advance(POSITION_INTERVAL / 5L);

        assertEquals("a look around inside one block column must not invalidate the memoised view",
                "Hot Desert Dunes", state.biome(PLAYER));
        assertEquals(1, terrain.builds());
    }

    @Test
    public void anImmediatePublishIgnoresTheOneSecondPositionGate() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));
        assertEquals("10,10", terrain.sampledColumn());

        terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");
        state.trackPositionNow(PLAYER, world, 2000, -2000);

        assertEquals("a discrete jump must not be swallowed by the move rate limiter",
                "Frozen Shelf", state.biome(PLAYER));
        assertEquals("2000,-2000", terrain.sampledColumn());
    }

    @Test
    public void anImmediatePublishInsideTheSameBlockColumnStillCostsNothing() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");
        state.trackPositionNow(PLAYER, world, 10, 10);

        assertEquals("Hot Desert Dunes", state.biome(PLAYER));
        assertEquals(1, terrain.builds());
    }

    @Test
    public void crossingIntoAnotherWorldRepublishesImmediately() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World sandbox = IrisPapiTestSupport.world("sandbox");
        World hub = IrisPapiTestSupport.world("hub");
        terrain.addIrisWorld(sandbox);
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> terrain, clock);

        state.trackPosition(PLAYER, sandbox, 10, 10);
        assertEquals("true", state.worldAvailable(PLAYER));

        state.trackPosition(PLAYER, hub, 0, 0);
        assertEquals("false", state.worldAvailable(PLAYER));
        assertEquals(DASH, state.biome(PLAYER));
    }

    @Test
    public void releasingAPlayerClearsTheTrackedPositionAndView() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        state.release(PLAYER);

        assertEquals("false", state.worldAvailable(PLAYER));
        assertEquals(DASH, state.biome(PLAYER));
        assertEquals(DASH, state.dimension(PLAYER));
    }

    @Test
    public void clearingTheStateDropsEveryTrackedPlayerAndTheLatchedPregen() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        state.trackPosition(PLAYER, world, 10, 10);
        state.publishPregen(IrisPregenPhase.TICK, progress(42.5D, 125_000L, false));
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));
        assertEquals("true", state.pregenAvailable(PLAYER));

        state.clear();

        assertEquals(DASH, state.biome(PLAYER));
        assertEquals("false", state.pregenAvailable(PLAYER));
    }

    @Test
    public void pregenKeysAreUnavailableUntilAnEventArrives() {
        IrisPapiState state = new IrisPapiState(FakeIrisTerrainService::new, new IrisPapiTestSupport.Clock());

        assertEquals("false", state.pregenAvailable(PLAYER));
        assertEquals(DASH, state.pregenWorld(PLAYER));
        assertEquals(DASH, state.pregenPercent(PLAYER));
        assertEquals(DASH, state.pregenEta(PLAYER));
        assertEquals(DASH, state.pregenEtaText(PLAYER));
        assertEquals(DASH, state.pregenChunks(PLAYER));
        assertEquals(DASH, state.pregenTotal(PLAYER));
        assertEquals(DASH, state.pregenChunksPerSecond(PLAYER));
        assertEquals(DASH, state.pregenPaused(PLAYER));
    }

    @Test
    public void pregenKeysRenderTheLatchedProgress() {
        IrisPapiState state = new IrisPapiState(FakeIrisTerrainService::new, new IrisPapiTestSupport.Clock());

        state.publishPregen(IrisPregenPhase.TICK, progress(42.5D, 125_000L, false));

        assertEquals("true", state.pregenAvailable(PLAYER));
        assertEquals("sandbox", state.pregenWorld(PLAYER));
        assertEquals("42.50", state.pregenPercent(PLAYER));
        assertEquals("125", state.pregenEta(PLAYER));
        assertEquals("2m 5s", state.pregenEtaText(PLAYER));
        assertEquals("1234", state.pregenChunks(PLAYER));
        assertEquals("4096", state.pregenTotal(PLAYER));
        assertEquals("12.50", state.pregenChunksPerSecond(PLAYER));
        assertEquals("false", state.pregenPaused(PLAYER));
    }

    @Test
    public void aPausedPregenReportsPaused() {
        IrisPapiState state = new IrisPapiState(FakeIrisTerrainService::new, new IrisPapiTestSupport.Clock());

        state.publishPregen(IrisPregenPhase.PAUSED, progress(42.5D, 125_000L, true));

        assertEquals("true", state.pregenPaused(PLAYER));
    }

    @Test
    public void pregenIsClearedWhenTheJobFinishesOrIsCancelled() {
        for (IrisPregenPhase terminal : List.of(IrisPregenPhase.COMPLETED, IrisPregenPhase.CANCELLED)) {
            IrisPapiState state = new IrisPapiState(FakeIrisTerrainService::new, new IrisPapiTestSupport.Clock());
            state.publishPregen(IrisPregenPhase.TICK, progress(99.0D, 1_000L, false));
            assertEquals("true", state.pregenAvailable(PLAYER));

            state.publishPregen(terminal, progress(100.0D, 0L, false));

            assertEquals(terminal + " must retire the pregen snapshot", "false", state.pregenAvailable(PLAYER));
            assertEquals(DASH, state.pregenPercent(PLAYER));
        }
    }

    @Test
    public void noPublishedValueEverCarriesAPercentOrSectionCharacter() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        terrain.describe("over%world", "Hot \u00A7cDesert", "hot%key", "Region\u00A7a", "reg%ion");
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        state.trackPosition(PLAYER, world, 0, 0);
        state.publishPregen(IrisPregenPhase.TICK, progress(100.0D, 3_600_000L, false));

        List<Function<UUID, String>> resolvers = List.of(
                state::available,
                state::worldAvailable,
                state::biome,
                state::biomeKey,
                state::region,
                state::regionKey,
                state::dimension,
                state::pregenAvailable,
                state::pregenWorld,
                state::pregenPercent,
                state::pregenEta,
                state::pregenEtaText,
                state::pregenChunks,
                state::pregenTotal,
                state::pregenChunksPerSecond,
                state::pregenPaused);

        for (Function<UUID, String> resolver : resolvers) {
            String value = resolver.apply(PLAYER);
            assertFalse("a placeholder value may never contain '%': " + value, value.indexOf('%') >= 0);
            assertFalse("a placeholder value may never contain a legacy colour code: " + value,
                    value.indexOf('\u00A7') >= 0);
        }

        assertNotEquals("Hot \u00A7cDesert", state.biome(PLAYER));
    }

    @Test
    public void etaTextCollapsesSecondsMinutesAndHours() {
        assertEquals("0s", IrisPapiPregenView.duration(0L));
        assertEquals("45s", IrisPapiPregenView.duration(45_000L));
        assertEquals("2m 5s", IrisPapiPregenView.duration(125_000L));
        assertEquals("1h 0m", IrisPapiPregenView.duration(3_600_000L));
        assertEquals("2h 3m", IrisPapiPregenView.duration(7_380_000L));
    }

    @Test
    public void aTerrainServiceThatDisappearsStopsAnsweringWithoutThrowing() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisTerrainService[] holder = new IrisTerrainService[]{terrain};
        IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        IrisPapiState state = new IrisPapiState(() -> holder[0], clock);

        state.trackPosition(PLAYER, world, 10, 10);
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));

        holder[0] = null;
        clock.advance(IrisPapiState.VIEW_TTL_MS);

        assertEquals("false", state.available(PLAYER));
        assertEquals("false", state.worldAvailable(PLAYER));
        assertEquals(DASH, state.biome(PLAYER));
    }
}
