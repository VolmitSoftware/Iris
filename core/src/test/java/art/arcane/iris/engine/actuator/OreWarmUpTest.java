package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.terrain.deposit.IrisOreGenerator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ore generators keep the noise of the first random they are warmed with, so the order and the randoms of the warm-up are pinned. */
public class OreWarmUpTest {
    private static final long COMPONENT_SEED = 100L;
    private static final long ROOT_SEED = COMPONENT_SEED + 7777L;

    private final List<String> warmed = new ArrayList<>();

    @Test
    public void biomesByKeyThenRegionsByKeyThenTheDimensionWarmWithConsecutiveRandomsOfTheWorld() {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getComponent()).thenReturn(COMPONENT_SEED);
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(dimension);
        IrisBiome biomeB = biome("b", ore("b0"), null, ore("b1"));
        IrisBiome biomeA = biome("a", ore("a0"));
        IrisRegion regionY = region("y", ore("y0"));
        IrisRegion regionX = region("x", ore("x0"), ore("x1"));
        KList<IrisOreGenerator> dimensionOres = list(ore("d0"));
        when(engine.getAllBiomes()).thenReturn(new KList<>(List.of(biomeB, biomeA)));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>(List.of(regionY, regionX)));
        when(dimension.getOres()).thenReturn(dimensionOres);

        new OreWarmUp(engine).warm();

        assertEquals(List.of("a0@" + ROOT_SEED, "b0@" + (ROOT_SEED + 1), "b1@" + (ROOT_SEED + 2),
                "x0@" + (ROOT_SEED + 3), "x1@" + (ROOT_SEED + 4), "y0@" + (ROOT_SEED + 5), "d0@" + (ROOT_SEED + 6)), warmed);
    }

    private IrisOreGenerator ore(String name) {
        IrisOreGenerator ore = mock(IrisOreGenerator.class);
        doAnswer(call -> warmed.add(name + "@" + call.<RNG>getArgument(0).getSeed())).when(ore).warm(any(RNG.class), any());
        return ore;
    }

    private static IrisBiome biome(String key, IrisOreGenerator... ores) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        when(biome.getOres()).thenReturn(list(ores));
        return biome;
    }

    private static IrisRegion region(String key, IrisOreGenerator... ores) {
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn(key);
        when(region.getOres()).thenReturn(list(ores));
        return region;
    }

    private static KList<IrisOreGenerator> list(IrisOreGenerator... ores) {
        KList<IrisOreGenerator> list = new KList<>();
        for (IrisOreGenerator ore : ores) {
            list.add(ore);
        }
        return list;
    }
}
