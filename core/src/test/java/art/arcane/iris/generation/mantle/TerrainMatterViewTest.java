package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TerrainMatterViewTest {
    @BeforeClass
    public static void registerMatter() {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    public void persistedJournalRestoresDeletedAndReplacedTerrainAndExcludesNewContent() throws IOException {
        Matter matter = new IrisMatter(16, 16, 16);
        MatterCavern natural = new MatterCavern(true, "iris:natural", (byte) 0);
        MatterCavern content = new MatterCavern(true, "iris:object", (byte) 0);
        matter.<MatterCavern>slice(MatterCavern.class).set(1, 3, 2, content);
        matter.<MatterCavern>slice(MatterCavern.class).set(2, 3, 2, content);
        matter.<MatterCavern>slice(MatterCavern.class).set(4, 3, 2, natural);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(1, 3, 2, PreObjectMatterCell.cavern(null));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(2, 3, 2, PreObjectMatterCell.cavern(natural));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(3, 3, 2, PreObjectMatterCell.cavern(natural));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.write(bytes);
        Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
        MantleChunk<Matter> chunk = chunk(restored);
        Map<Integer, MatterCavern> values = new HashMap<>();
        TerrainMatterView.iterate(chunk, MatterCavern.class, (x, y, z, cavern) -> {
            assertEquals(3, y.intValue());
            assertEquals(2, z.intValue());
            assertNull(values.put(x, cavern));
        });

        assertEquals(Map.of(2, natural, 3, natural, 4, natural), values);
        assertNull(TerrainMatterView.get(chunk, 1, 3, 2, MatterCavern.class));
        assertEquals(natural, TerrainMatterView.get(chunk, 3, 3, 2, MatterCavern.class));
    }

    @Test
    public void journaledTypesReuseIteratedValuesAndKeepOriginalIdentityAndOrder() {
        assertJournalIteration(MatterCavern.class,
                new MatterCavern(true, "iris:natural", (byte) 0),
                new MatterCavern(true, "iris:object", (byte) 0), PreObjectMatterCell::cavern);
        assertJournalIteration(String.class, "iris:natural", "iris:object", PreObjectMatterCell::string);
        assertJournalIteration(HydrologyCaveCell.class,
                HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR),
                HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE), PreObjectMatterCell::hydrology);
        assertJournalIteration(PlatformBlockState.class,
                mock(PlatformBlockState.class), mock(PlatformBlockState.class), PreObjectMatterCell::block);
    }

    @Test
    public void callbackFailuresEscapeOutsideTheChunkLock() {
        Matter matter = new IrisMatter(16, 16, 16);
        MatterCavern original = new MatterCavern(true, "iris:natural", (byte) 0);
        matter.<MatterCavern>slice(MatterCavern.class).set(1, 3, 2, original);
        MantleChunk<Matter> chunk = chunk(matter);
        IllegalStateException failure = new IllegalStateException("terrain callback");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> TerrainMatterView.iterate(chunk, MatterCavern.class, (x, y, z, value) -> {
                    assertFalse(Thread.holdsLock(chunk));
                    assertSame(original, value);
                    throw failure;
                })));
        List<MatterCavern> recovered = new ArrayList<>();
        TerrainMatterView.iterate(chunk, MatterCavern.class, (x, y, z, value) -> recovered.add(value));
        assertEquals(List.of(original), recovered);
    }

    @Test
    public void firstPassDoesNotReadTheSuppliedRawValuesAgain() {
        Matter matter = new IrisMatter(16, 16, 16);
        MatterCavern original = new MatterCavern(true, "iris:natural", (byte) 0);
        matter.<MatterCavern>slice(MatterCavern.class).set(1, 3, 2, original);
        matter.<MatterCavern>slice(MatterCavern.class).set(4, 3, 2, original);
        MantleChunk<Matter> chunk = chunk(matter);
        List<MatterCavern> values = new ArrayList<>();

        TerrainMatterView.iterate(chunk, MatterCavern.class, (x, y, z, value) -> values.add(value));

        assertEquals(List.of(original, original), values);
        verify(chunk, times(2)).get(0);
        verify(chunk, times(2)).exists(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unjournaledTypesKeepTheDirectIteratorPath() {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Consumer4<Integer, Integer, Integer, Integer> consumer = (x, y, z, value) -> {
            assertFalse(Thread.holdsLock(chunk));
            assertEquals(7, value.intValue());
        };
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, Integer> supplied = invocation.getArgument(1);
            assertSame(consumer, supplied);
            supplied.accept(1, 3, 2, 7);
            return null;
        }).when(chunk).iterate(Integer.class, consumer);

        TerrainMatterView.iterate(chunk, Integer.class, consumer);

        verify(chunk).iterate(Integer.class, consumer);
        verifyNoMoreInteractions(chunk);
    }

    private static <T> void assertJournalIteration(Class<T> type, T original, T content,
                                                   Function<T, PreObjectMatterCell> capture) {
        Matter matter = new IrisMatter(16, 16, 16);
        matter.<T>slice(type).set(1, 3, 2, content);
        matter.<T>slice(type).set(2, 3, 2, content);
        matter.<T>slice(type).set(4, 3, 2, original);
        matter.<T>slice(type).set(5, 3, 2, content);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(1, 3, 2, capture.apply(null));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(2, 3, 2, capture.apply(original));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(3, 3, 2, capture.apply(original));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(5, 3, 2,
                type == String.class ? PreObjectMatterCell.cavern(null) : PreObjectMatterCell.string(null));
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(6, 3, 2, capture.apply(null));
        MantleChunk<Matter> chunk = chunk(matter);
        List<Integer> positions = new ArrayList<>();
        List<T> values = new ArrayList<>();

        TerrainMatterView.iterate(chunk, type, (x, y, z, value) -> {
            assertFalse(Thread.holdsLock(chunk));
            assertEquals(3, y.intValue());
            assertEquals(2, z.intValue());
            positions.add(x);
            values.add(value);
            matter.<T>slice(type).set(4, 3, 2, content);
        });

        assertEquals(List.of(2, 4, 5, 3), positions);
        assertSame(original, values.get(0));
        assertSame(original, values.get(1));
        assertSame(content, values.get(2));
        assertSame(original, values.get(3));
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> chunk(Matter matter) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.exists(0)).thenReturn(true);
        when(chunk.get(0)).thenReturn(matter);
        doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(chunk));
            Class<Object> type = invocation.getArgument(0);
            Consumer4<Integer, Integer, Integer, Object> consumer = invocation.getArgument(1);
            if (!matter.hasSlice(type)) {
                return null;
            }
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        Object value = matter.getSlice(type).get(x, y, z);
                        if (value != null) {
                            consumer.accept(x, y, z, value);
                        }
                    }
                }
            }
            return null;
        }).when(chunk).iterate(any(), any());
        return chunk;
    }
}
