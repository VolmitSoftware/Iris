package art.arcane.iris.builtin.structure;

import com.google.gson.Gson;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The terrain fit, stilts and Y band settings of {@code iris:structure} as packs write them. */
public class StructureFitSettingsTest {
    private static final Gson JSON = new Gson();

    @Test
    public void terrainDefaultsToSourceWithBoxCarving() {
        StructureTerrain terrain = terrain("{}");

        assertEquals(StructureTerrainMode.SOURCE, terrain.resolvedMode());
        assertEquals(StructureCarveShape.BOX, terrain.resolvedShape());
        assertEquals(0.8D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, terrain.resolvedErosionFrequency(), 0D);
        assertEquals(0.021D, terrain.resolvedLobeFrequency(), 1.0E-12D);
        assertEquals(0.85D, terrain.resolvedLobeStrength(), 0D);
        assertTrue(terrain.encasePalette().isEmpty());
    }

    @Test
    public void lobeFrequencyAutoDerivesFromErosionFrequencyUntilAuthored() {
        assertEquals(0.015D, terrain("{\"erosionFrequency\":0.05}").resolvedLobeFrequency(), 1.0E-12D);
        assertEquals(0.015D, terrain("{\"erosionFrequency\":0.05,\"lobeFrequency\":\"NaN\"}").resolvedLobeFrequency(), 1.0E-12D);
        assertEquals(0.015D, terrain("{\"erosionFrequency\":0.05,\"lobeFrequency\":-1}").resolvedLobeFrequency(), 1.0E-12D);
        assertEquals(0.04D, terrain("{\"erosionFrequency\":0.05,\"lobeFrequency\":0.04}").resolvedLobeFrequency(), 0D);
        assertEquals(1D, terrain("{\"erosionFrequency\":0.05,\"lobeFrequency\":2}").resolvedLobeFrequency(), 0D);
    }

    @Test
    public void lobeStrengthClampsToTheAuthoredBand() {
        assertEquals(0.85D, terrain("{\"lobeStrength\":\"NaN\"}").resolvedLobeStrength(), 0D);
        assertEquals(0D, terrain("{\"lobeStrength\":-1}").resolvedLobeStrength(), 0D);
        assertEquals(1D, terrain("{\"lobeStrength\":2}").resolvedLobeStrength(), 0D);
        assertEquals(0.4D, terrain("{\"lobeStrength\":0.4}").resolvedLobeStrength(), 0D);
    }

    @Test
    public void carveShapesExposeTheirMaximumCeilingScale() {
        assertEquals(1D, StructureCarveShape.BOX.maximumCeilingScale(), 0D);
        assertEquals(1D, StructureCarveShape.ROUNDED.maximumCeilingScale(), 0D);
        assertEquals(1.8D, StructureCarveShape.ERODED.maximumCeilingScale(), 0D);
        assertEquals(10, StructureCarveShape.BOX.maximumCeilingExtension(10));
        assertEquals(10, StructureCarveShape.ROUNDED.maximumCeilingExtension(10));
        assertEquals(18, StructureCarveShape.ERODED.maximumCeilingExtension(10));
        assertEquals(10, StructureCarveShape.ERODED.maximumCeilingExtension(10, 0D));
        assertEquals(14, StructureCarveShape.ERODED.maximumCeilingExtension(10, 0.5D));
        assertEquals(0, StructureCarveShape.ERODED.maximumCeilingExtension(0));
    }

    @Test
    public void nullAndNonFiniteValuesResolveToDefaults() {
        StructureTerrain terrain = terrain("{\"shape\":null,\"erosionStrength\":\"NaN\",\"erosionFrequency\":\"Infinity\"}");

        assertEquals(StructureCarveShape.BOX, terrain.resolvedShape());
        assertEquals(0.8D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, terrain.resolvedErosionFrequency(), 0D);
        StructureTerrain negative = terrain("{\"erosionStrength\":\"-Infinity\",\"erosionFrequency\":\"NaN\"}");
        assertEquals(0.8D, negative.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, negative.resolvedErosionFrequency(), 0D);
    }

    @Test
    public void finiteValuesClampToAuthoredBounds() {
        StructureTerrain low = terrain("{\"erosionStrength\":-1,\"erosionFrequency\":0}");
        StructureTerrain high = terrain("{\"erosionStrength\":2,\"erosionFrequency\":2}");

        assertEquals(0D, low.resolvedErosionStrength(), 0D);
        assertEquals(0.001D, low.resolvedErosionFrequency(), 0D);
        assertEquals(1D, high.resolvedErosionStrength(), 0D);
        assertEquals(1D, high.resolvedErosionFrequency(), 0D);
    }

    @Test
    public void encaseModeCarriesAnOptionalPaletteOverride() {
        StructureTerrain encase = terrain("{\"mode\":\"ENCASE\",\"encasePalette\":{\"palette\":[{\"block\":\"minecraft:tuff\"}]}}");

        assertEquals(StructureTerrainMode.ENCASE, encase.resolvedMode());
        assertEquals("minecraft:tuff", encase.encasePalette().orElseThrow().palette().getFirst().block());
    }

    @Test
    public void stiltsDefaultToACobblestoneFoundation() {
        StructureStilts stilts = new StructureStilts();

        assertEquals(64, stilts.maxDepth());
        assertEquals(1, stilts.spacing());
        assertFalse(stilts.supportNonOccluding());
        assertEquals(1, stilts.palette().orElseThrow().palette().size());
        assertEquals("minecraft:cobblestone", stilts.palette().orElseThrow().palette().getFirst().block());
    }

    @Test
    public void bandBoundsResolveInOrderEvenWhenReversed() {
        StructureYBand band = JSON.fromJson("{\"min\":-20,\"max\":-120}", StructureYBand.class);
        StructureYBand collapsed = JSON.fromJson("{\"min\":-64,\"max\":-64}", StructureYBand.class);

        assertEquals(-120, band.resolvedMin());
        assertEquals(-20, band.resolvedMax());
        assertEquals(-64, collapsed.resolvedMin());
        assertEquals(-64, collapsed.resolvedMax());
    }

    @Test
    public void numericSchemaBoundsMatchRuntimeBounds() throws NoSuchFieldException {
        assertBounds(StructureTerrain.class, "erosionStrength", 0D, 1D);
        assertBounds(StructureTerrain.class, "erosionFrequency", 0.001D, 1D);
        assertBounds(StructureTerrain.class, "lobeFrequency", 0D, 1D);
        assertBounds(StructureTerrain.class, "lobeStrength", 0D, 1D);
        assertBounds(StructureStilts.class, "maxDepth", 1D, 4064D);
        assertBounds(StructureStilts.class, "spacing", 1D, 64D);
        assertBounds(StructureYBand.class, "min", -4064D, 4064D);
        assertBounds(StructureYBand.class, "max", -4064D, 4064D);
    }

    private static StructureTerrain terrain(String json) {
        return JSON.fromJson(json, StructureTerrain.class);
    }

    private static void assertBounds(Class<?> type, String fieldName, double expectedMinimum, double expectedMaximum)
            throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        MinNumber minimum = field.getAnnotation(MinNumber.class);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);

        assertNotNull(minimum);
        assertNotNull(maximum);
        assertEquals(expectedMinimum, minimum.value(), 0D);
        assertEquals(expectedMaximum, maximum.value(), 0D);
    }
}
