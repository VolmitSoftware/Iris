package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisBiomeGenLinksTest {
    @Test
    public void duplicateGeneratorKeysUseTheLastLinkForAllQueries() {
        IrisBiomeGeneratorLink first = link("terrain", -20, 30);
        IrisBiomeGeneratorLink last = link("terrain", -40, 60);
        IrisBiome biome = new IrisBiome().setGenerators(new KList<>(first, last));

        assertEquals(60D, biome.getGenLinkMax("terrain"), 0D);
        assertEquals(-40D, biome.getGenLinkMin("terrain"), 0D);
        assertSame(last, biome.getGenLink("terrain"));
    }

    @Test
    public void independentKeysPreserveNegativeBounds() {
        IrisBiome biome = new IrisBiome().setGenerators(new KList<>(
                link("ocean", -40, -10), link("land", 5, 80)));

        assertEquals(-40D, biome.getGenLinkMin("ocean"), 0D);
        assertEquals(-10D, biome.getGenLinkMax("ocean"), 0D);
        assertEquals(5D, biome.getGenLinkMin("land"), 0D);
        assertEquals(80D, biome.getGenLinkMax("land"), 0D);
    }

    @Test
    public void missingAndBlankKeysHaveNoBoundsOrLink() {
        IrisBiome biome = new IrisBiome().setGenerators(new KList<>(
                link(null, -40, 60), link(" ", -20, 30), link("terrain", 5, 80)));

        for (String key : new String[]{null, "", " ", "missing"}) {
            assertNull(biome.getGenLink(key));
            assertEquals(0D, biome.getGenLinkMin(key), 0D);
            assertEquals(0D, biome.getGenLinkMax(key), 0D);
        }
    }

    private static IrisBiomeGeneratorLink link(String key, int minimum, int maximum) {
        return new IrisBiomeGeneratorLink().setGenerator(key).setMin(minimum).setMax(maximum);
    }
}
