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

package art.arcane.iris.engine.object.world.biome;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.region.IrisRegionEntry;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.volmit.world.Weighted;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lists of biomes and regions are weighted entries in the order the pack writes them; a parent is itself only where its own entry is listed. */
public class IrisBiomeWeightedChildrenTest {
    @Test
    @SuppressWarnings("unchecked")
    public void childOptionsKeepTheListOrderTheWeightsAndTheParentEntry() {
        IrisBiome plains = biome("plains");
        IrisBiome meadow = biome("meadow");
        IrisBiome glade = biome("glade");
        plains.setChildren(new KList<>(List.of(
                new IrisBiomeEntry("meadow", 1), new IrisBiomeEntry("plains", 4), new IrisBiomeEntry("missing", 2), new IrisBiomeEntry("glade", 3))));
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        when(loader.load("plains")).thenReturn(plains);
        when(loader.load("meadow")).thenReturn(meadow);
        when(loader.load("glade")).thenReturn(glade);
        IrisData data = mock(IrisData.class);
        when(data.getBiomeLoader()).thenReturn(loader);

        KList<Weighted<IrisBiome>> options = plains.getChildOptions(() -> data);

        assertEquals(List.of("meadow", "plains", "glade"), options.stream().map(option -> option.value().getLoadKey()).toList());
        assertEquals(List.of(1, 4, 3), options.stream().map(Weighted::weight).toList());
        assertSame("the parent entry is the parent", plains, options.get(1).value());
    }

    @Test
    public void childKeysLeaveOutTheParentItself() {
        IrisBiome plains = biome("plains");
        plains.setChildren(new KList<>(List.of(new IrisBiomeEntry("meadow", 1), new IrisBiomeEntry("plains", 4), new IrisBiomeEntry("glade", 3))));

        assertEquals(List.of("meadow", "glade"), plains.getChildKeys());
    }

    @Test
    public void aBiomeWithoutChildrenHasNoOptions() {
        IrisBiome plains = biome("plains");
        IrisData data = mock(IrisData.class);

        assertEquals(List.of(), plains.getChildOptions(() -> data));
    }

    @Test
    public void entriesReadTheKeyAndTheWeightPacksWrite() {
        IrisRegion region = new Gson().fromJson("{\"name\": \"Temperate\", \"landBiomes\": [{\"biome\": \"plains\", \"weight\": 3}, {\"biome\": \"forest\"}]}", IrisRegion.class);

        assertEquals(List.of(new IrisBiomeEntry("plains", 3), new IrisBiomeEntry("forest", 1)), region.getLandBiomes());
        assertEquals(Set.of("plains", "forest"), region.getAllBiomeIds());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void regionEntriesLeaveOutRegionsThatDoNotLoad() {
        IrisRegion temperate = new IrisRegion();
        ResourceLoader<IrisRegion> loader = mock(ResourceLoader.class);
        when(loader.load("temperate")).thenReturn(temperate);

        KList<Weighted<IrisRegion>> loaded = IrisRegionEntry.load(
                List.of(new IrisRegionEntry("missing", 2), new IrisRegionEntry("temperate", 5)), loader);

        assertEquals(1, loaded.size());
        assertSame(temperate, loaded.getFirst().value());
        assertEquals(5, loaded.getFirst().weight());
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }
}
