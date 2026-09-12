package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TerrainBoundarySignatureTest {
    @Test
    public void storesPlatformNeutralTerrainSamples() {
        ArrayList<String> palette = new ArrayList<>(List.of("iris:forest", "iris:cave"));
        short[] biomeIndices = {0, 1, 1};

        TerrainBoundarySignature.BiomeEncoding biomeEncoding =
                new TerrainBoundarySignature.BiomeEncoding(palette, biomeIndices);
        TerrainBoundarySignature signature = new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(-1, 32, 91, 48, OptionalInt.of(63), OptionalInt.of(48)),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(-64, 32, 3),
                        biomeEncoding
                )
        , BoundaryColumnGeometry.empty());

        palette.set(0, "changed");
        biomeIndices[0] = 1;

        assertEquals(-1, signature.blockX());
        assertEquals(32, signature.blockZ());
        assertEquals(91, signature.surfaceHeight());
        assertEquals(48, signature.oceanFloorHeight());
        assertEquals(63, signature.fluidHeight().orElseThrow());
        assertEquals(3, signature.sampleCount());
        assertEquals(-64, signature.sampleY(0));
        assertEquals(0, signature.sampleY(2));
        assertEquals("iris:forest", signature.biomeAtSample(0));
    }

    @Test
    public void returnedCompactSamplesCannotMutateSignature() {
        TerrainBoundarySignature.BiomeEncoding biomes = new TerrainBoundarySignature.BiomeEncoding(
                List.of("iris:forest"), new short[]{0}
        );

        short[] exportedBiomes = biomes.paletteIndices();
        exportedBiomes[0] = -1;

        assertEquals("iris:forest", biomes.biomeAtSample(0));
        assertThrows(UnsupportedOperationException.class, () -> biomes.palette().add("iris:other"));
    }

    @Test
    public void supportsColumnsWithoutFluid() {
        TerrainBoundarySignature.Column column =
                new TerrainBoundarySignature.Column(0, 0, 80, 50, OptionalInt.empty(), OptionalInt.empty());
        assertTrue(column.fluidHeight().isEmpty());
        assertTrue(column.upperCeilingDepth().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new TerrainBoundarySignature.Column(
                0, 0, 80, 50, OptionalInt.empty(), OptionalInt.of(0)));
    }

    @Test
    public void rejectsInvalidSampleEncodings() {
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainBoundarySignature.VerticalLayout(0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainBoundarySignature.BiomeEncoding(List.of("iris:forest"), new short[]{1}));

        TerrainBoundarySignature.VerticalLayout layout =
                new TerrainBoundarySignature.VerticalLayout(0, 4, 2);
        TerrainBoundarySignature.BiomeEncoding oneBiome =
                new TerrainBoundarySignature.BiomeEncoding(List.of("iris:forest"), new short[]{0});
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainBoundarySignature.Samples(layout, oneBiome));
    }
}
