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

package art.arcane.iris.engine.object.world.region;

import art.arcane.iris.builtin.carve.CarvingBiomeSettings;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.PackResourceFixture;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.volmit.IrisFeature;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The biomes of a region: the ones it lists, their children and the biomes their {@code carvingBiome} registers; not the targets of floating islands. */
public class IrisRegionBiomeTreeTest {
    @Test
    @SuppressWarnings("unchecked")
    public void aBiomesCarvingBiomeJoinsTheRegionAndAFloatingChildDoesNot() {
        IrisRegion region = new IrisRegion().setLandBiomes(new KList<>("plains"));
        FloatingChildBiome floating = new Gson().fromJson("{\"biome\": \"floating/target\", \"carving\": \"carving/pocket\"}", FloatingChildBiome.class);
        IrisBiome plains = biome("plains", new CarvingBiomeSettings("carving/mushroom"), new FloatingChildSettings(List.of(floating)))
                .setChildren(new KList<>("meadow"));
        IrisBiome meadow = biome("meadow");
        IrisBiome mushroom = biome("carving/mushroom", new CarvingBiomeSettings("carving/nested"));
        IrisBiome nested = biome("carving/nested");

        IrisPackResources resources = PackResourceFixture.empty(new File("pack"));
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(data.getPackResources()).thenReturn(resources);
        when(biomes.load("plains")).thenReturn(plains);
        when(biomes.load("meadow")).thenReturn(meadow);
        when(biomes.load("carving/mushroom")).thenReturn(mushroom);
        when(biomes.load("carving/nested")).thenReturn(nested);

        Set<String> keys = region.getAllBiomes(() -> data).stream().map(IrisBiome::getLoadKey).collect(Collectors.toSet());

        assertEquals(Set.of("plains", "meadow", "carving/mushroom", "carving/nested"), keys);
    }

    private static IrisBiome biome(String key, IrisFeature... settings) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        biome.setModuleSettings(BuiltInPack.settings(settings));
        return biome;
    }
}
