package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
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
        PlatformBlockState air = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(registries.air()).thenReturn(air);
        when(registries.block(anyString())).thenReturn(air);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisMatterSupport.ensureRegistered();

        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        matter = new IrisMatter(16, 16, 16);
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
    public void firstMutationCapturesEachSupportedOriginalIndependently() {
        PlatformBlockState originalBlock = mock(PlatformBlockState.class);
        PlatformBlockState firstBlock = mock(PlatformBlockState.class);
        PlatformBlockState secondBlock = mock(PlatformBlockState.class);
        MatterCavern originalCavern = new MatterCavern(true, "old", (byte) 1);
        MatterCavern replacementCavern = new MatterCavern(true, "new", (byte) 2);
        matter.<PlatformBlockState>slice(PlatformBlockState.class).set(X, Y, Z, originalBlock);
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y, Z, originalCavern);

        writer.withComponentPriority(2, () -> {
            writer.setData(X, Y, Z, firstBlock);
            writer.setData(X, Y, Z, "new-marker");
            writer.setData(X, Y, Z, replacementCavern);
            writer.setData(X, Y, Z, secondBlock);
            writer.setData(X, Y, Z, "newer-marker");
        });

        assertSame(originalBlock, writer.getPrerequisiteDataIfPresent(X, Y, Z, PlatformBlockState.class));
        assertNull(writer.getPrerequisiteDataIfPresent(X, Y, Z, String.class));
        assertEquals(originalCavern, writer.getPrerequisiteDataIfPresent(X, Y, Z, MatterCavern.class));
        assertSame(secondBlock, writer.getDataIfPresent(X, Y, Z, PlatformBlockState.class));
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
        PlatformBlockState originalBlock = mock(PlatformBlockState.class);
        PlatformBlockState replacementBlock = mock(PlatformBlockState.class);
        MatterCavern originalCavern = new MatterCavern(true, "old", (byte) 1);
        MatterCavern replacementCavern = new MatterCavern(true, "new", (byte) 2);
        matter.<PlatformBlockState>slice(PlatformBlockState.class).set(X, Y, Z, originalBlock);
        matter.<MatterCavern>slice(MatterCavern.class).set(X, Y, Z, originalCavern);
        writer.withComponentPriority(2, () -> {
            writer.setData(X, Y, Z, replacementBlock);
            writer.setData(X, Y, Z, "marker");
            writer.setData(X, Y, Z, replacementCavern);
        });

        assertTrue(writer.restorePrerequisiteData(X, Y, Z, String.class));
        assertNull(writer.getDataIfPresent(X, Y, Z, String.class));
        assertTrue(writer.restorePrerequisiteCell(X, Y, Z));
        assertSame(originalBlock, writer.getDataIfPresent(X, Y, Z, PlatformBlockState.class));
        assertEquals(originalCavern, writer.getDataIfPresent(X, Y, Z, MatterCavern.class));
        assertFalse(writer.restorePrerequisiteData(X, Y, Z, Integer.class));
        assertFalse(writer.restorePrerequisiteCell(X + 1, Y, Z));
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
