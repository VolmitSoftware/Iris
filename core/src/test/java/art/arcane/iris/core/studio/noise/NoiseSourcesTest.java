package art.arcane.iris.core.studio.noise;

import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NoiseSourcesTest {
    private static final long SEED = 12345L;
    private static final double EXACT = 0D;

    @Test
    public void listsEveryStyleGroupedInCategoryOrder() {
        List<NoiseSources.Source> sources = NoiseSources.builtIn().list();
        assertEquals(NoiseStyle.values().length, sources.size());
        List<String> categoryOrder = Arrays.stream(NoiseCategory.values()).map(NoiseCategory::label).toList();
        List<Integer> positions = sources.stream().map(source -> categoryOrder.indexOf(source.category())).toList();
        assertFalse(positions.contains(-1));
        assertEquals(positions.stream().sorted().toList(), positions);
        NoiseSources.Source simplex = sources.stream().filter(source -> source.id().equals("style:SIMPLEX")).findFirst().orElseThrow();
        assertEquals("Simplex", simplex.name());
        assertEquals("Simplex", simplex.category());
        assertTrue(simplex.usesSeed());
    }

    @Test
    public void aSeedPicksTheNoise() {
        NoiseSources sources = NoiseSources.builtIn();
        NoiseProvider first = sources.sampler("style:SIMPLEX", SEED);
        NoiseProvider again = sources.sampler("style:SIMPLEX", SEED);
        NoiseProvider other = sources.sampler("style:SIMPLEX", SEED + 1);
        assertEquals(first.noise(37.5, -12.25), again.noise(37.5, -12.25), EXACT);
        assertNotEquals(first.noise(37.5, -12.25), other.noise(37.5, -12.25), EXACT);
    }

    @Test
    public void anUnknownSourceIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> NoiseSources.builtIn().sampler("style:NOPE", SEED));
    }

    @Test
    public void tilesStartAtTheirCornerAndMeetTheirNeighbours() {
        NoiseProvider position = (x, z) -> x * 1000 + z;
        NoiseTile west = NoiseTile.sample(position, 0, 1, 0.5, 4, () -> false);
        NoiseTile east = NoiseTile.sample(position, 1, 1, 0.5, 4, () -> false);
        assertEquals(position.noise(0, 2), west.values()[0], EXACT);
        assertEquals(position.noise(1.5, 2.5), west.values()[4 + 3], EXACT);
        assertEquals(position.noise(2, 2), east.values()[0], EXACT);
    }

    @Test
    public void encodesLittleEndianFloats() {
        NoiseTile tile = new NoiseTile(1, new float[]{0.25F});
        float decoded = ByteBuffer.wrap(tile.encode()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        assertEquals(0.25F, decoded, EXACT);
    }

    @Test
    public void stopsWhenNoLongerWanted() {
        assertThrows(CancellationException.class, () -> NoiseTile.sample((x, z) -> 0, 0, 0, 1, 4, () -> true));
    }
}
