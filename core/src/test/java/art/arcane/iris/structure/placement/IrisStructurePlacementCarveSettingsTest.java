package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisStructurePlacementCarveSettingsTest {
    @Test
    public void defaultsToSourceTerrainWithBoxCarving() {
        IrisStructureTerrain terrain = new IrisStructureTerrain();

        assertEquals(IrisStructureTerrainMode.SOURCE, terrain.resolvedMode());
        assertEquals(IrisStructureCarveShape.BOX, terrain.getShape());
        assertEquals(IrisStructureCarveShape.BOX, terrain.resolvedShape());
        assertEquals(0.8D, terrain.getErosionStrength(), 0D);
        assertEquals(0.8D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, terrain.getErosionFrequency(), 0D);
        assertEquals(0.07D, terrain.resolvedErosionFrequency(), 0D);
        assertEquals(0D, terrain.getLobeFrequency(), 0D);
        assertEquals(0.021D, terrain.resolvedLobeFrequency(), 1.0E-12D);
        assertEquals(0.85D, terrain.getLobeStrength(), 0D);
        assertEquals(0.85D, terrain.resolvedLobeStrength(), 0D);
    }

    @Test
    public void lobeFrequencyAutoDerivesFromErosionFrequencyUntilAuthored() {
        IrisStructureTerrain terrain = new IrisStructureTerrain().setErosionFrequency(0.05D);

        assertEquals(0.015D, terrain.resolvedLobeFrequency(), 1.0E-12D);

        terrain.setLobeFrequency(Double.NaN);
        assertEquals(0.015D, terrain.resolvedLobeFrequency(), 1.0E-12D);

        terrain.setLobeFrequency(-1D);
        assertEquals(0.015D, terrain.resolvedLobeFrequency(), 1.0E-12D);

        terrain.setLobeFrequency(0.04D);
        assertEquals(0.04D, terrain.resolvedLobeFrequency(), 0D);

        terrain.setLobeFrequency(2D);
        assertEquals(1D, terrain.resolvedLobeFrequency(), 0D);
    }

    @Test
    public void lobeStrengthClampsToTheAuthoredBand() {
        IrisStructureTerrain terrain = new IrisStructureTerrain().setLobeStrength(Double.NaN);

        assertEquals(0.85D, terrain.resolvedLobeStrength(), 0D);

        terrain.setLobeStrength(-1D);
        assertEquals(0D, terrain.resolvedLobeStrength(), 0D);

        terrain.setLobeStrength(2D);
        assertEquals(1D, terrain.resolvedLobeStrength(), 0D);

        terrain.setLobeStrength(0.4D);
        assertEquals(0.4D, terrain.resolvedLobeStrength(), 0D);
    }

    @Test
    public void carveShapesExposeTheirMaximumCeilingScale() {
        assertEquals(1D, IrisStructureCarveShape.BOX.maximumCeilingScale(), 0D);
        assertEquals(1D, IrisStructureCarveShape.ROUNDED.maximumCeilingScale(), 0D);
        assertEquals(1.8D, IrisStructureCarveShape.ERODED.maximumCeilingScale(), 0D);
        assertEquals(10, IrisStructureCarveShape.BOX.maximumCeilingExtension(10));
        assertEquals(10, IrisStructureCarveShape.ROUNDED.maximumCeilingExtension(10));
        assertEquals(18, IrisStructureCarveShape.ERODED.maximumCeilingExtension(10));
        assertEquals(10, IrisStructureCarveShape.ERODED.maximumCeilingExtension(10, 0D));
        assertEquals(14, IrisStructureCarveShape.ERODED.maximumCeilingExtension(10, 0.5D));
        assertEquals(0, IrisStructureCarveShape.ERODED.maximumCeilingExtension(0));
    }

    @Test
    public void nullAndNonFiniteValuesResolveToDefaults() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setShape(null)
                .setErosionStrength(Double.NaN)
                .setErosionFrequency(Double.POSITIVE_INFINITY);

        assertEquals(IrisStructureCarveShape.BOX, terrain.resolvedShape());
        assertEquals(0.8D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, terrain.resolvedErosionFrequency(), 0D);

        terrain.setErosionStrength(Double.NEGATIVE_INFINITY);
        terrain.setErosionFrequency(Double.NaN);

        assertEquals(0.8D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.07D, terrain.resolvedErosionFrequency(), 0D);
    }

    @Test
    public void finiteValuesClampToAuthoredBounds() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setErosionStrength(-1D)
                .setErosionFrequency(0D);

        assertEquals(0D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(0.001D, terrain.resolvedErosionFrequency(), 0D);

        terrain.setErosionStrength(2D);
        terrain.setErosionFrequency(2D);

        assertEquals(1D, terrain.resolvedErosionStrength(), 0D);
        assertEquals(1D, terrain.resolvedErosionFrequency(), 0D);
    }

    @Test
    public void encaseModeCarriesAnOptionalPaletteOverride() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.ENCASE);

        assertEquals(IrisStructureTerrainMode.ENCASE, terrain.resolvedMode());
        assertNull(terrain.getEncasePalette());

        IrisMaterialPalette palette = new IrisMaterialPalette().qclear().qadd("minecraft:tuff");
        assertSame(palette, terrain.setEncasePalette(palette).getEncasePalette());
    }

    @Test
    public void numericSchemaBoundsMatchRuntimeBounds() throws NoSuchFieldException {
        assertBounds("erosionStrength", 0D, 1D);
        assertBounds("erosionFrequency", 0.001D, 1D);
        assertBounds("lobeFrequency", 0D, 1D);
        assertBounds("lobeStrength", 0D, 1D);
    }

    private void assertBounds(String fieldName, double expectedMinimum,
                              double expectedMaximum) throws NoSuchFieldException {
        Field field = IrisStructureTerrain.class.getDeclaredField(fieldName);
        MinNumber minimum = field.getAnnotation(MinNumber.class);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);

        assertNotNull(minimum);
        assertNotNull(maximum);
        assertEquals(expectedMinimum, minimum.value(), 0D);
        assertEquals(expectedMaximum, maximum.value(), 0D);
    }
}
