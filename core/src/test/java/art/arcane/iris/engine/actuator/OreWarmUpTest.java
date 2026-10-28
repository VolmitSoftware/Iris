package art.arcane.iris.engine.actuator;

import art.arcane.iris.modules.terrain.OreSettings;
import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.iris.engine.terrain.TestPackWorld;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
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
        TerrainOres ores = mock(TerrainOres.class);
        List<Entry<OreSettings>> declared = new ArrayList<>();
        // The pack lists the dimension first, then regions and biomes, in no particular order.
        declared.add(entry(Level.DIMENSION, "dimension/overworld", ores, "d0"));
        declared.add(entry(Level.REGION, "region/y", ores, "y0"));
        declared.add(entry(Level.BIOME, "biome/b", ores, "b0", "b1"));
        declared.add(entry(Level.REGION, "region/x", ores, "x0", "x1"));
        declared.add(entry(Level.BIOME, "biome/a", ores, "a0"));
        PackSettings settings = new PackSettings() {
            @Override
            public com.volmit.resource.PackResources resources() {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                return SettingsStack.of((List<Entry<F>>) (List<?>) declared);
            }
        };
        TestPackWorld world = new TestPackWorld(new ApiChunk(0, 0, 16).world().resources(), settings, COMPONENT_SEED);

        new OreWarmUp(world, ores).warm();

        assertEquals(List.of("a0@" + ROOT_SEED, "b0@" + (ROOT_SEED + 1), "b1@" + (ROOT_SEED + 2),
                "x0@" + (ROOT_SEED + 3), "x1@" + (ROOT_SEED + 4), "y0@" + (ROOT_SEED + 5), "d0@" + (ROOT_SEED + 6)), warmed);
    }

    private Entry<OreSettings> entry(Level level, String source, TerrainOres ores, String... oreNames) {
        OreSettings settings = new OreSettings(List.of());
        ResolvedOres declared = resolved(oreNames);
        when(ores.of(settings)).thenReturn(declared);
        return new Entry<>(level, ResourceId.parse("test:" + source), settings);
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
}
