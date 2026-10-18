package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeferredPlacementRegenerationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final Identifier ID = Identifier.fromString("craftengine:example/chair");
    private static final CustomBlockOwnership.Entry ORIGINAL = new CustomBlockOwnership.Entry(ID, "original", false);
    private static final NativeBlockPoint POSITION = new NativeBlockPoint(3, 70, 5);

    private Mantle<Matter> mantle;
    private MantleChunk<Matter> chunk;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
        chunk = spy(new MantleChunk<>(24, 1, -1,
                IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class)), MantleHooks.NONE));
        mantle = mock(Mantle.class);
        when(mantle.useChunk(1, -1)).thenAnswer(invocation -> chunk.use());
        chunk.getOrCreate(POSITION.y() >> 4).slice(CustomBlockOwnership.class)
                .set(POSITION.x(), POSITION.y() & 15, POSITION.z(), new CustomBlockOwnership(List.of(ORIGINAL)));
        chunk.getOrCreate(POSITION.y() >> 4).slice(Identifier.class)
                .set(POSITION.x(), POSITION.y() & 15, POSITION.z(), ID);
        chunk.flag(MantleFlag.OBJECT, true);
        chunk.flag(MantleFlag.of(200), true);
        chunk.flag(MantleFlag.TILE, true);
        chunk.flag(MantleFlag.CUSTOM, true);
        chunk.flag(MantleFlag.UPDATE, true);
        chunk.flag(MantleFlag.ETCHED, true);
        chunk.flag(MantleFlag.INITIAL_SPAWNED_MARKER, true);
    }

    @Test
    public void marginResetKeepsOwnershipAndPreventsDuplicateMaterialization() {
        CustomBlockOwnership before = ownership();

        DeferredPlacementRecords.resetMantleChunk(mantle, 1, -1, false);

        assertSame(before, ownership());
        assertNull(chunk.get(POSITION.x(), POSITION.y(), POSITION.z(), Identifier.class));
        assertFalse(chunk.isFlagged(MantleFlag.OBJECT));
        assertFalse(chunk.isFlagged(MantleFlag.of(200)));
        assertTrue(chunk.isFlagged(MantleFlag.TILE));
        assertTrue(chunk.isFlagged(MantleFlag.CUSTOM));
        assertTrue(chunk.isFlagged(MantleFlag.UPDATE));
        assertTrue(chunk.isFlagged(MantleFlag.INITIAL_SPAWNED_MARKER));
        AtomicInteger placements = new AtomicInteger();
        chunk.raiseFlagUnchecked(MantleFlag.ETCHED, placements::incrementAndGet);
        assertEquals(0, placements.get());
        verify(mantle, never()).deleteChunk(1, -1);
    }

    @Test
    public void targetResetKeepsOwnershipUntilSuccessfulLiveReplacement() {
        DeferredPlacementRecords.resetMantleChunk(mantle, 1, -1, true);

        assertEquals(List.of(ORIGINAL), ownership().entries());
        assertFalse(chunk.isFlagged(MantleFlag.TILE));
        assertFalse(chunk.isFlagged(MantleFlag.CUSTOM));
        assertFalse(chunk.isFlagged(MantleFlag.UPDATE));
        assertFalse(chunk.isFlagged(MantleFlag.ETCHED));
        assertFalse(chunk.isFlagged(MantleFlag.INITIAL_SPAWNED_MARKER));
    }

    @Test(timeout = 5000)
    public void successfulTargetReplacementInvalidatesOldTokensWithoutDeletingNewRecords() {
        DeferredPlacementRecords.resetMantleChunk(mantle, 1, -1, true);
        chunk.getOrCreate(POSITION.y() >> 4).slice(Identifier.class)
                .set(POSITION.x(), POSITION.y() & 15, POSITION.z(), ID);

        DeferredPlacementRecords.invalidateCustomBlockOwnership(chunk);

        assertEquals(List.of(new CustomBlockOwnership.Entry(ID, "original", true)), ownership().entries());
        assertEquals(ID, chunk.get(POSITION.x(), POSITION.y(), POSITION.z(), Identifier.class));
    }

    @Test
    public void ownershipAddedDuringResetSurvivesInTheSameChunk() throws Exception {
        CountDownLatch resetting = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean firstRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstRead.compareAndSet(true, false)) {
                resetting.countDown();
                assertTrue(resume.await(5, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(chunk).get(0);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(mock(IrisWorld.class));
        CustomBlockOwnership.Entry concurrent = new CustomBlockOwnership.Entry(ID, "concurrent", false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> reset = executor.submit(() -> DeferredPlacementRecords.resetMantleChunk(mantle, 1, -1, false));
            assertTrue(resetting.await(5, TimeUnit.SECONDS));
            Future<?> remember = executor.submit(() -> DeferredPlacementRecords.remember(engine, chunk, POSITION, concurrent));
            resume.countDown();
            reset.get(5, TimeUnit.SECONDS);
            remember.get(5, TimeUnit.SECONDS);

            assertEquals(List.of(ORIGINAL, new CustomBlockOwnership.Entry(ID, "concurrent", true)), ownership().entries());
            assertFalse(chunk.inUse());
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    private CustomBlockOwnership ownership() {
        return chunk.get(POSITION.x(), POSITION.y(), POSITION.z(), CustomBlockOwnership.class);
    }
}
