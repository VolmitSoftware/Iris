package art.arcane.iris.generation.stream;

import art.arcane.iris.pack.value.Weighted;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightedStreamTest {
    @Test
    public void picksTheSlotTheNoiseFallsInWithEdgesGoingToTheEarlierEntry() {
        List<Weighted<String>> entries = List.of(new Weighted<>("barren", 3), new Weighted<>("forest", 1), new Weighted<>("lake", 2));
        List<String> copies = new ArrayList<>();
        entries.forEach(entry -> {
            for (int copy = 0; copy < entry.weight(); copy++) {
                copies.add(entry.value());
            }
        });
        double[] noise = new double[1];
        WeightedStream<String> stream = new WeightedStream<>(ProceduralStream.ofDouble((x, z) -> noise[0]), entries);

        for (int step = -20; step <= 1020; step++) {
            noise[0] = step / 1000D;
            int slot = (int) Math.ceil(noise[0] * copies.size()) - 1;
            String expected = copies.get(Math.max(0, Math.min(copies.size() - 1, slot)));
            assertEquals("noise " + noise[0], expected, stream.get(3D, -4D));
        }
    }

    @Test
    public void everyWholeSlotBoundaryBelongsToTheEarlierEntry() {
        List<Weighted<String>> entries = List.of(new Weighted<>("first", 1), new Weighted<>("second", 1));
        double[] noise = {0.5D};
        WeightedStream<String> stream = new WeightedStream<>(ProceduralStream.ofDouble((x, z) -> noise[0]), entries);

        assertEquals("first", stream.get(0D, 0D));
        noise[0] = Math.nextUp(0.5D);
        assertEquals("second", stream.get(0D, 0D));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void readsTheNoiseInThreeDimensionsToo() {
        List<Weighted<String>> entries = List.of(new Weighted<>("low", 1), new Weighted<>("high", 3));
        ProceduralStream<Double> noise = mock(ProceduralStream.class);
        when(noise.getDouble(0D, 10D, 0D)).thenReturn(0.1D);
        when(noise.getDouble(0D, 90D, 0D)).thenReturn(0.9D);
        WeightedStream<String> stream = new WeightedStream<>(noise, entries);

        assertEquals("low", stream.get(0D, 10D, 0D));
        assertEquals("high", stream.get(0D, 90D, 0D));
    }

    @Test
    public void anEmptyListPicksNothing() {
        WeightedStream<String> stream = new WeightedStream<>(ProceduralStream.ofDouble((x, z) -> 0.5D), List.of());

        assertNull(stream.get(0D, 0D));
    }
}
