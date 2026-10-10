/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.builtin.carve.CaveDepthSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the carve module answers the host about cave biomes: the biome of the dimension's band at a height above the dimension's bottom, and the minimum depths. */
public class CaveBiomeBandsTest {
    private static final int MIN_HEIGHT = -64;
    private static final String BANDS = """
            [{"id": "shallow", "biome": "band/shallow", "worldYRange": {"min": -64, "max": 0}},
             {"id": "disabled", "enabled": false, "biome": "band/disabled", "worldYRange": {"min": 100, "max": 200}},
             {"id": "unloadable", "biome": "band/missing", "worldYRange": {"min": 300, "max": 310}},
             {"id": "padded", "biome": " band/padded ", "worldYRange": {"min": 500, "max": 600}}]
            """;

    private Engine engine;
    private final IrisBiome shallow = mock(IrisBiome.class);

    @Before
    @SuppressWarnings("unchecked")
    public void mockEngine() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(biomes.load("band/shallow")).thenReturn(shallow);
        when(biomes.load("band/padded")).thenReturn(mock(IrisBiome.class));
        when(biomes.load("band/disabled")).thenReturn(mock(IrisBiome.class));
        when(shallow.getModuleSettings()).thenReturn(BuiltInPack.settings(new CaveDepthSettings(30)));
        List<CarvingEntry> entries = new Gson().fromJson(BANDS, new com.google.gson.reflect.TypeToken<List<CarvingEntry>>() { }.getType());
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(new CarvingSettings(entries)));
        engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(MIN_HEIGHT).maxHeight(320).build());
        DecoratorTestEngines.withGeneration(engine);
    }

    @Test
    public void aPositionInsideAnEnabledBandGetsTheBandsBiomeKey() {
        CaveBiomeBands bands = new CaveBiomeBands();
        IrisPackWorld world = engine.getGeneration().world();

        assertEquals("world Y -54 is in the band from -64 to 0", Optional.of("band/shallow"), bands.overriddenBiome(world, 5, 10, 5));
        assertEquals("the band's end is in it", Optional.of("band/shallow"), bands.overriddenBiome(world, 5, 64, 5));
        assertEquals("above it", Optional.empty(), bands.overriddenBiome(world, 5, 65, 5));
        assertEquals("a disabled band overrides nothing", Optional.empty(), bands.overriddenBiome(world, 5, 200 - MIN_HEIGHT - 50, 5));
        assertEquals("a band whose biome the pack lacks overrides nothing", Optional.empty(), bands.overriddenBiome(world, 5, 305 - MIN_HEIGHT, 5));
        assertEquals("the key is trimmed as the loader is asked for it", Optional.of("band/padded"), bands.overriddenBiome(world, 5, 550 - MIN_HEIGHT, 5));
    }

    @Test
    public void aBiomeKeyHasTheMinimumDepthItsSettingsDeclareAndZeroWhenTheKeyIsUnknown() {
        CaveBiomeBands bands = new CaveBiomeBands();
        IrisPackWorld world = engine.getGeneration().world();

        assertEquals(30, bands.minDepthBelowSurface(world, "band/shallow"));
        assertEquals(0, bands.minDepthBelowSurface(world, "band/unknown"));
    }
}
