package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ore generators keep the noise of the first seed they are warmed with, so the order and the seeds of the warm-up are pinned. */
public class OreWarmUpTest {
    private static final long COMPONENT_SEED = 100L;
    private static final long ROOT_SEED = COMPONENT_SEED + 7777L;

    private final List<String> warmed = new ArrayList<>();

    @Test
    public void biomesByKeyThenRegionsByKeyThenTheDimensionWarmWithConsecutiveSeedsOfTheWorld() {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getComponent()).thenReturn(COMPONENT_SEED);
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getDimension()).thenReturn(dimension);
        TerrainOres ores = mock(TerrainOres.class);
        IrisBiome biomeB = biome("b", ores, "b0", "b1");
        IrisBiome biomeA = biome("a", ores, "a0");
        IrisRegion regionY = region("y", ores, "y0");
        IrisRegion regionX = region("x", ores, "x0", "x1");
        ResolvedOres dimensionOres = resolved("d0");
        when(ores.of(dimension)).thenReturn(dimensionOres);
        when(engine.getAllBiomes()).thenReturn(new KList<>(List.of(biomeB, biomeA)));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>(List.of(regionY, regionX)));

        new OreWarmUp(engine, ores).warm();

        assertEquals(List.of("a0@" + ROOT_SEED, "b0@" + (ROOT_SEED + 1), "b1@" + (ROOT_SEED + 2),
                "x0@" + (ROOT_SEED + 3), "x1@" + (ROOT_SEED + 4), "y0@" + (ROOT_SEED + 5), "d0@" + (ROOT_SEED + 6)), warmed);
    }

    private ResolvedOres resolved(String... names) {
        List<ResolvedOre> ores = new ArrayList<>();
        for (String name : names) {
            ResolvedOre ore = mock(ResolvedOre.class);
            doAnswer(call -> warmed.add(name + "@" + call.<Long>getArgument(0))).when(ore).warm(anyLong());
            ores.add(ore);
        }
        return new ResolvedOres(ores);
    }

    private IrisBiome biome(String key, TerrainOres ores, String... oreNames) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        ResolvedOres declared = resolved(oreNames);
        when(ores.of(biome)).thenReturn(declared);
        return biome;
    }

    private IrisRegion region(String key, TerrainOres ores, String... oreNames) {
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn(key);
        ResolvedOres declared = resolved(oreNames);
        when(ores.of(region)).thenReturn(declared);
        return region;
    }
}
