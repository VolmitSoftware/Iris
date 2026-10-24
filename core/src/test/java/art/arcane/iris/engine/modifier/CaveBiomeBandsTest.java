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
import art.arcane.iris.modules.carve.CarvingSettings;
import art.arcane.iris.modules.carve.CaveDepthSettings;
import art.arcane.iris.core.modules.CarveTestWorld;
import com.google.gson.Gson;
import com.volmit.config.Level;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/** What the carve module answers the host about cave biomes: the biome of the dimension's band at a height above the dimension's bottom, and the minimum depths. */
public class CaveBiomeBandsTest {
    private static final int MIN_HEIGHT = CarveTestWorld.MIN_HEIGHT;
    private static final String BANDS = """
            [{"id": "shallow", "biome": "band/shallow", "worldYRange": {"min": -64, "max": 0}},
             {"id": "disabled", "enabled": false, "biome": "band/disabled", "worldYRange": {"min": 100, "max": 200}},
             {"id": "unloadable", "biome": "band/missing", "worldYRange": {"min": 300, "max": 310}},
             {"id": "padded", "biome": " band/padded ", "worldYRange": {"min": 500, "max": 600}}]
            """;

    private CarveTestWorld world;

    @Before
    public void declareBands() {
        List<CarvingEntry> entries = new Gson().fromJson(BANDS, new com.google.gson.reflect.TypeToken<List<CarvingEntry>>() { }.getType());
        world = new CarveTestWorld()
                .biome("band/shallow", 1).biome("band/padded", 1).biome("band/disabled", 1)
                .dimension(new CarvingSettings(entries))
                .declare(Level.BIOME, "band/shallow", new CaveDepthSettings(30));
    }

    @Test
    public void aPositionInsideAnEnabledBandGetsTheBandsBiomeKey() {
        CaveBiomeBands bands = new CaveBiomeBands();

        assertEquals("world Y -54 is in the band from -64 to 0", Optional.of(CarveTestWorld.id("band/shallow")), bands.overriddenBiome(world, 5, 10, 5));
        assertEquals("the band's end is in it", Optional.of(CarveTestWorld.id("band/shallow")), bands.overriddenBiome(world, 5, 64, 5));
        assertEquals("above it", Optional.empty(), bands.overriddenBiome(world, 5, 65, 5));
        assertEquals("a disabled band overrides nothing", Optional.empty(), bands.overriddenBiome(world, 5, 200 - MIN_HEIGHT - 50, 5));
        assertEquals("a band whose biome the pack lacks overrides nothing", Optional.empty(), bands.overriddenBiome(world, 5, 305 - MIN_HEIGHT, 5));
        assertEquals("the key is trimmed as the pack is asked for it", Optional.of(CarveTestWorld.id("band/padded")), bands.overriddenBiome(world, 5, 550 - MIN_HEIGHT, 5));
    }

    @Test
    public void aBiomeKeyHasTheMinimumDepthItsSettingsDeclareAndZeroWhenTheKeyIsUnknown() {
        CaveBiomeBands bands = new CaveBiomeBands();

        assertEquals(30, bands.minDepthBelowSurface(world, CarveTestWorld.id("band/shallow")));
        assertEquals(0, bands.minDepthBelowSurface(world, CarveTestWorld.id("band/unknown")));
    }
}
