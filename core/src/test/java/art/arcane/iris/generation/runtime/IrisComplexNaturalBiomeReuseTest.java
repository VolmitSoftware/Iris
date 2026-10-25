package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisComplexNaturalBiomeReuseTest {
    @Test
    public void allActiveSelectionExpressionsAndFracturesDisableReuse() {
        IrisDimension dimension = new IrisDimension();
        IrisBiome leaf = new IrisBiome();
        assertTrue(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        for (IrisGeneratorStyle style : List.of(dimension.getRegionStyle(), dimension.getContinentalStyle(),
                dimension.getLandBiomeStyle(), dimension.getSeaBiomeStyle())) {
            style.setFracture(new IrisGeneratorStyle().setExpression("context"));
            assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
            style.setFracture(null);
        }
        leaf.setChildStyle(new IrisGeneratorStyle().setExpression("context"));
        assertTrue(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        leaf.setChildren(new KList<>("child"));
        assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        leaf.setChildStyle(new IrisGeneratorStyle().setFracture(new IrisGeneratorStyle().setExpression("context")));
        assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
    }
}
