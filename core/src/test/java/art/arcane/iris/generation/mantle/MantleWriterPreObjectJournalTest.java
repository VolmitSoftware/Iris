package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleWriterPreObjectJournalTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int X = 2;
    private static final int Y = 5;
    private static final int Z = 4;

    private MantleWriter writer;
    private Matter matter;

    @BeforeClass
    public static void setUpBukkit() {
        BukkitTestServer.install();
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        IrisPlatforms.unbind();
        NativeBlockState air = mock(NativeBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(registries.air()).thenReturn(air);
        when(registries.block(anyString())).thenReturn(air);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisMatterSupport.ensureRegistered();

        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getComplex()).thenReturn(mock(IrisComplex.class));
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        matter = spy(new IrisMatter(16, 16, 16));
        when(mantle.getWorldHeight()).thenReturn(32);
        when(mantle.getChunk(0, 0)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(chunk.exists(0)).thenReturn(true);
        when(chunk.getOrCreate(0)).thenReturn(matter);
        when(chunk.get(0)).thenReturn(matter);
        writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
    }

    @After
    public void tearDown() {
        if (writer != null) {
            writer.close();
        }
        IrisPlatforms.unbind();
    }

    @Test
    public void concurrentTransactionsKeepOverlaysPrivateDuringObjectPublication() throws Exception {
        NativeBlockState original = mock(NativeBlockState.class);
        NativeBlockState published = mock(NativeBlockState.class);
        NativeBlockState firstOverlay = mock(NativeBlockState.class);
        NativeBlockState secondOverlay = mock(NativeBlockState.class);
        matter.<NativeBlockState>slice(NativeBlockState.class).set(X, Y, Z, original);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> verifyTransactionReads(original, firstOverlay, ready, start));
            Future<?> second = executor.submit(() -> verifyTransactionReads(original, secondOverlay, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            writer.withComponentPriority(2, () -> {
                for (int pass = 0; pass < 100; pass++) {
                    writer.setData(X, Y, Z, published);
                }
            });
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertSame(published, writer.getDataIfPresent(X, Y, Z, NativeBlockState.class));
            assertSame(original, writer.getPrerequisiteBlock(X, Y, Z));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void integerPrerequisitesDoNotReadTheObjectJournal() {
        matter.<Integer>slice(Integer.class).set(X, Y, Z, 17);
        clearInvocations(matter);

        assertEquals(Integer.valueOf(17), writer.getPrerequisiteDataIfPresent(X, Y, Z, Integer.class));

        verify(matter, never()).getSlice(PreObjectMatterCell.class);
    }

    @Test
    public void hydrologyPrerequisitesHonorCapturedOriginalsAndNulls() {
        HydrologyCaveCell original = HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR);
        HydrologyCaveCell live = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class).set(X, Y, Z, live);
        matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class).set(X, Y + 1, Z, live);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class)
                .set(X, Y, Z, PreObjectMatterCell.hydrology(original));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class)
                .set(X, Y + 1, Z, PreObjectMatterCell.hydrology(null));

        assertSame(original, writer.getPrerequisiteDataIfPresent(X, Y, Z, HydrologyCaveCell.class));
        assertNull(writer.getPrerequisiteDataIfPresent(X, Y + 1, Z, HydrologyCaveCell.class));
        assertSame(live, writer.getDataIfPresent(X, Y, Z, HydrologyCaveCell.class));
        assertSame(live, writer.getDataIfPresent(X, Y + 1, Z, HydrologyCaveCell.class));
    }

    @Test
    public void carvedColumnsHonorCapturedHydrologyBeforeLiveCells() {
        HydrologyCaveCell original = HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR);
        HydrologyCaveCell live = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        MatterSlice<HydrologyCaveCell> hydrology = matter.slice(HydrologyCaveCell.class);
        MatterSlice<PreObjectMatterCell> journal = matter.slice(PreObjectMatterCell.class);
        hydrology.set(X, Y, Z, live);
        hydrology.set(X, Y + 1, Z, live);
        hydrology.set(X, Y + 2, Z, original);
        journal.set(X, Y, Z, PreObjectMatterCell.hydrology(original));
        journal.set(X, Y + 1, Z, PreObjectMatterCell.hydrology(null));
        journal.set(X, Y + 2, Z, PreObjectMatterCell.hydrology(live));
        matter.<MatterCavern>slice(MatterCavern.class)
                .set(X, Y + 1, Z, new MatterCavern(true, "cave", (byte) 0));

        byte[] carved = writer.getPrerequisiteCarvedColumn(X, Z, 32);

        assertEquals(1, carved[Y]);
        assertEquals(1, carved[Y + 1]);
        assertEquals(0, carved[Y + 2]);
        for (int y = 0; y < carved.length; y++) {
            assertEquals(writer.isPrerequisiteCarved(X, y, Z), carved[y] != 0);
        }
    }

    @Test
    public void firstMutationCapturesEachSupportedOriginalIndependently() {
        NativeBlockState originalBlock = mock(NativeBlockState.class);
        NativeBlockState firstBlock = mock(NativeBlockState.class);
        NativeBlockState secondBlock = mock(NativeBlockState.class);
        MatterCavern originalCavern = new MatterCavern(true, "old", (byte) 1);
        MatterCavern replacementCavern = new MatterCavern(true, "new", (byte) 2);
        matter.<NativeBlockState>slice(NativeBlockState.class).set(X, Y, Z, originalBlock);
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y, Z, originalCavern);

        writer.withComponentPriority(2, () -> {
            writer.setData(X, Y, Z, firstBlock);
            writer.setData(X, Y, Z, "new-marker");
            writer.setData(X, Y, Z, replacementCavern);
            writer.setData(X, Y, Z, secondBlock);
            writer.setData(X, Y, Z, "newer-marker");
        });

        assertSame(originalBlock, writer.getPrerequisiteDataIfPresent(X, Y, Z, NativeBlockState.class));
        assertNull(writer.getPrerequisiteDataIfPresent(X, Y, Z, String.class));
        assertEquals(originalCavern, writer.getPrerequisiteDataIfPresent(X, Y, Z, MatterCavern.class));
        assertSame(secondBlock, writer.getDataIfPresent(X, Y, Z, NativeBlockState.class));
        assertEquals("newer-marker", writer.getDataIfPresent(X, Y, Z, String.class));
        assertEquals(replacementCavern, writer.getDataIfPresent(X, Y, Z, MatterCavern.class));

        PreObjectMatterCell journal = matter.<PreObjectMatterCell>getSlice(PreObjectMatterCell.class)
                .get(X, Y, Z);
        assertTrue(journal.blockCaptured());
        assertTrue(journal.stringCaptured());
        assertTrue(journal.cavernCaptured());
    }

    @Test
    public void priorityScopeIsNestedAndThreadLocal() throws Exception {
        writer.withComponentPriority(2, () -> {
            writer.withComponentPriority(1, () -> writer.setData(X, Y, Z, "uncaptured-inner"));
            writer.setData(X + 1, Y, Z, "captured-outer");
        });
        assertThrows(IllegalStateException.class, () -> writer.withComponentPriority(2, () -> {
            throw new IllegalStateException("scope failure");
        }));
        writer.setData(X + 2, Y, Z, "uncaptured-after-failure");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> scoped = executor.submit(() -> {
                await(start);
                writer.withComponentPriority(2, () -> writer.setData(X + 3, Y, Z, "captured-thread"));
            });
            Future<?> unscoped = executor.submit(() -> {
                await(start);
                writer.setData(X + 4, Y, Z, "uncaptured-thread");
            });
            start.countDown();
            scoped.get(5, TimeUnit.SECONDS);
            unscoped.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        MatterSlice<PreObjectMatterCell> journal = matter.getSlice(PreObjectMatterCell.class);
        assertNull(journal.get(X, Y, Z));
        assertTrue(journal.get(X + 1, Y, Z).stringCaptured());
        assertNull(journal.get(X + 2, Y, Z));
        assertTrue(journal.get(X + 3, Y, Z).stringCaptured());
        assertNull(journal.get(X + 4, Y, Z));
    }

    @Test
    public void prerequisiteCarvingUsesJournalTombstonesAndHydrology() {
        MatterCavern original = new MatterCavern(true, "", (byte) 0);
        MatterCavern replacement = new MatterCavern(true, "", (byte) 1);
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y + 1, Z, original);
        writer.withComponentPriority(2, () -> {
            writer.setData(X, Y, Z, replacement);
            writer.clearData(X, Y + 1, Z, MatterCavern.class);
        });
        matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class)
                .set(X, Y + 2, Z, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y + 2, Z, original);
        matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class)
                .set(X, Y + 3, Z, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR));

        byte[] column = writer.getPrerequisiteCarvedColumn(X, Z, Y + 4);

        assertEquals(0, column[Y]);
        assertEquals(1, column[Y + 1]);
        assertEquals(0, column[Y + 2]);
        assertEquals(1, column[Y + 3]);
        assertFalse(writer.isPrerequisiteCarved(X, Y, Z));
        assertTrue(writer.isPrerequisiteCarved(X, Y + 1, Z));
    }

    @Test
    public void restorationWritesCapturedValuesAndNullsWithoutRecapture() {
        NativeBlockState originalBlock = mock(NativeBlockState.class);
        NativeBlockState replacementBlock = mock(NativeBlockState.class);
        MatterCavern originalCavern = new MatterCavern(true, "old", (byte) 1);
        MatterCavern replacementCavern = new MatterCavern(true, "new", (byte) 2);
        matter.<NativeBlockState>slice(NativeBlockState.class).set(X, Y, Z, originalBlock);
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y, Z, originalCavern);
        writer.withComponentPriority(2, () -> {
            writer.setData(X, Y, Z, replacementBlock);
            writer.setData(X, Y, Z, "marker");
            writer.setData(X, Y, Z, replacementCavern);
        });

        assertTrue(writer.restorePrerequisiteData(X, Y, Z, String.class));
        assertNull(writer.getDataIfPresent(X, Y, Z, String.class));
        assertTrue(writer.restorePrerequisiteCell(X, Y, Z));
        assertSame(originalBlock, writer.getDataIfPresent(X, Y, Z, NativeBlockState.class));
        assertEquals(originalCavern, writer.getDataIfPresent(X, Y, Z, MatterCavern.class));
        assertFalse(writer.restorePrerequisiteData(X, Y, Z, Integer.class));
        assertFalse(writer.restorePrerequisiteCell(X + 1, Y, Z));
    }

    private void verifyTransactionReads(
            NativeBlockState original,
            NativeBlockState overlay,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);
        ready.countDown();
        await(start);
        for (int pass = 0; pass < 100; pass++) {
            assertSame(original, transaction.get(X, Y, Z));
        }
        transaction.setData(X, Y, Z, overlay);
        assertSame(overlay, transaction.get(X, Y, Z));
        assertSame(original, writer.getPrerequisiteBlock(X, Y, Z));
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
