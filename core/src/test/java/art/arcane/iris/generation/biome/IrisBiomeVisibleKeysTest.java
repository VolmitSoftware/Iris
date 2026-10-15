package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeVisibleKeysTest {
    @Test
    public void visibleKeysCoverTheDerivativeOnlyWhenNoGroundScatterReplacesIt() {
        Engine engine = engine();
        IrisBiome plain = new IrisBiome().setDerivative("terralith:mirage_isles");
        IrisBiome scattered = new IrisBiome().setDerivative("minecraft:plains")
                .setBiomeScatter(new KList<>("minecraft:forest", "birch_forest"))
                .setBiomeSkyScatter(new KList<>("example:custom"));

        assertEquals(new KList<>("terralith:mirage_isles"), plain.getVisibleBiomeKeys(engine));
        assertEquals(new KList<>("minecraft:forest", "minecraft:birch_forest", "physical:custom"),
                scattered.getVisibleBiomeKeys(engine));
    }

    @Test
    public void visibleKeysContainEverySelectedSkyAndGroundKey() {
        Engine engine = engine();
        IrisBiome single = new IrisBiome().setDerivative("minecraft:plains")
                .setBiomeScatter(new KList<>("minecraft:forest"))
                .setBiomeSkyScatter(new KList<>("example:custom"));
        RNG rng = new RNG(7L);

        assertTrue(single.getVisibleBiomeKeys(engine).contains(single.getGroundBiomeKey(rng, engine, 4, 8, 12)));
        assertTrue(single.getVisibleBiomeKeys(engine).contains(single.getSkyBiomeKey(rng, engine, 4, 8, 12)));
    }

    private static Engine engine() {
        IrisDimension dimension = new IrisDimension();
        IrisData data = mock(IrisData.class);
        when(data.physicalBiomeResourceKey(eq(dimension), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            return key.equals("example:custom") ? "physical:custom" : key;
        });
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        return engine;
    }
}
