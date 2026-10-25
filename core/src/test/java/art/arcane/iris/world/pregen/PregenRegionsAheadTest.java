package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.hydrology.engine.HydrologyPregeneration;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PregenRegionsAheadTest {
    @Test
    public void announcesEachRegionOnceAtItsStartOrOneRegionAhead() {
        List<long[]> order = regions(6);
        List<String> events = new ArrayList<>();
        PregenRegionsAhead ahead = new PregenRegionsAhead(order.iterator(), region -> {
            events.add("check:" + region[0]);
            return false;
        });

        List<long[]> visited = walk(ahead, events);

        assertRegions(order, visited);
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L), announced(events));
        assertEquals(List.of("start:0", "check:0", "announce:0", "first:0", "check:1", "announce:1", "rest:0", "start:1", "first:1"),
                events.subList(0, 9));
        assertCurrentRegionStartsBeforeAnyLookAhead(events);
        assertNull(ahead.next());
    }

    @Test
    public void resumedPregenNeverAnnouncesGeneratedRegionsAndReadsAheadOnlyAfterTheCurrentRegionStarts() {
        List<long[]> order = regions(8);
        Set<Long> generated = Set.of(0L, 2L, 3L, 7L);
        List<String> events = new ArrayList<>();
        PregenRegionsAhead ahead = new PregenRegionsAhead(order.iterator(), region -> {
            events.add("check:" + region[0]);
            return generated.contains(region[0]);
        });

        List<long[]> visited = walk(ahead, events);

        assertRegions(order, visited);
        assertEquals(List.of(1L, 4L, 5L, 6L), announced(events));
        assertCurrentRegionStartsBeforeAnyLookAhead(events);
        for (long region = 0; region < 8; region++) {
            assertEquals("region " + region + " is read once", 1, events.stream().filter(("check:" + region)::equals).count());
        }
    }

    private static List<long[]> walk(PregenRegionsAhead ahead, List<String> events) {
        List<long[]> visited = new ArrayList<>();
        for (long[] region = ahead.next(); region != null; region = ahead.next()) {
            events.add("start:" + region[0]);
            for (long[] announced : ahead.starting()) {
                events.add("announce:" + announced[0]);
            }
            events.add("first:" + region[0]);
            for (long[] announced : ahead.following()) {
                events.add("announce:" + announced[0]);
            }
            events.add("rest:" + region[0]);
            visited.add(region);
        }
        return visited;
    }

    private static List<Long> announced(List<String> events) {
        List<Long> announced = new ArrayList<>();
        for (String event : events) {
            if (event.startsWith("announce:")) {
                announced.add(Long.parseLong(event.substring("announce:".length())));
            }
        }
        return announced;
    }

    private static void assertCurrentRegionStartsBeforeAnyLookAhead(List<String> events) {
        String current = null;
        for (String event : events) {
            if (event.startsWith("start:")) {
                current = event.substring("start:".length());
            } else if (event.startsWith("first:")) {
                current = null;
            } else if (current != null && event.startsWith("check:")) {
                assertEquals("only the current region is read before it starts", "check:" + current, event);
            }
        }
    }

    @Test
    public void pregenTaskRegionOrderMatchesItsSpiral() {
        PregenTask task = PregenTask.builder().center(new Position2(700, -300)).radiusX(2000).radiusZ(1500).build();
        List<long[]> spiral = new ArrayList<>();
        task.iterateRegions((x, z) -> spiral.add(new long[]{x, z}));
        List<long[]> order = new ArrayList<>();
        Iterator<long[]> regions = task.regionOrder();
        while (regions.hasNext()) {
            order.add(regions.next());
        }

        assertRegions(spiral, order);
        assertEquals(1L, order.getFirst()[0]);
        assertEquals(-1L, order.getFirst()[1]);
    }

    @Test
    public void epochIsOpenedOnceAndClosingWithdrawsIt() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        HydrologyPregeneration epoch = mock(HydrologyPregeneration.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.openPregeneration()).thenReturn(epoch);
        PregenHydrology hydrology = new PregenHydrology(() -> engine);
        List<long[]> first = regions(2);
        List<long[]> second = regions(1);

        hydrology.prepare(first);
        hydrology.prepare(second);
        hydrology.close();
        hydrology.prepare(first);

        verify(runtime, times(1)).openPregeneration();
        InOrder calls = inOrder(epoch);
        calls.verify(epoch).prepare(first);
        calls.verify(epoch).prepare(second);
        calls.verify(epoch).close();
        verify(epoch, times(2)).prepare(anyList());
    }

    @Test
    public void aHotloadedRuntimeGetsANewEpochAndTheOldOneIsWithdrawn() {
        Engine engine = mock(Engine.class);
        IrisComplex before = mock(IrisComplex.class);
        IrisComplex after = mock(IrisComplex.class);
        HydrologyRuntime stale = mock(HydrologyRuntime.class);
        HydrologyRuntime fresh = mock(HydrologyRuntime.class);
        HydrologyPregeneration staleEpoch = mock(HydrologyPregeneration.class);
        HydrologyPregeneration freshEpoch = mock(HydrologyPregeneration.class);
        when(engine.getComplex()).thenReturn(before, before, after);
        when(before.getHydrologyRuntime()).thenReturn(stale);
        when(after.getHydrologyRuntime()).thenReturn(fresh);
        when(stale.openPregeneration()).thenReturn(staleEpoch);
        when(fresh.openPregeneration()).thenReturn(freshEpoch);
        PregenHydrology hydrology = new PregenHydrology(() -> engine);
        List<long[]> first = regions(2);
        List<long[]> second = regions(1);
        List<long[]> third = regions(3);

        hydrology.prepare(first);
        hydrology.prepare(second);
        hydrology.prepare(third);
        hydrology.close();

        InOrder calls = inOrder(staleEpoch, freshEpoch);
        calls.verify(staleEpoch).prepare(first);
        calls.verify(staleEpoch).prepare(second);
        calls.verify(staleEpoch).close();
        calls.verify(freshEpoch).prepare(third);
        calls.verify(freshEpoch).close();
        verify(stale, times(1)).openPregeneration();
        verify(fresh, times(1)).openPregeneration();
    }

    @Test
    public void closedOrEngineLessHydrologyNeverOpensAnEpoch() {
        Engine engine = mock(Engine.class);
        PregenHydrology closed = new PregenHydrology(() -> engine);
        closed.close();
        closed.prepare(regions(1));
        new PregenHydrology(() -> null).prepare(regions(1));

        verifyNoInteractions(engine);
    }

    @Test
    public void preparesLargeRegionListsInBatchesSoTheFirstRegionsKeepThePlannerFirst() {
        HydrologyPregeneration epoch = mock(HydrologyPregeneration.class);
        int batch = HydrologyRuntime.MAX_PREGENERATION_REGIONS;
        List<long[]> order = regions(batch + 904);

        PregenHydrology.prepareAll(epoch, order);
        PregenHydrology.prepareAll(null, order);

        InOrder calls = inOrder(epoch);
        calls.verify(epoch).prepare(order.subList(904, order.size()));
        calls.verify(epoch).prepare(order.subList(0, 904));
        verify(epoch, times(2)).prepare(anyList());
        verify(epoch, never()).close();
    }

    private static List<long[]> regions(int count) {
        List<long[]> regions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            regions.add(new long[]{index, -index});
        }
        return regions;
    }

    private static void assertRegions(List<long[]> expected, List<long[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index)[0], actual.get(index)[0]);
            assertEquals(expected.get(index)[1], actual.get(index)[1]);
        }
    }
}
