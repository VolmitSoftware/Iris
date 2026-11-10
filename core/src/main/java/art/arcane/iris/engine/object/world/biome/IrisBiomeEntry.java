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

import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.world.Weighted;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A biome in a list a noise picks from")
@Data
public class IrisBiomeEntry {
    @Required
    @RegistryListResource(IrisBiome.class)
    @Desc("The biome (file name without .json)")
    private String biome;
    @MinNumber(Weighted.MIN_WEIGHT)
    @MaxNumber(Weighted.MAX_WEIGHT)
    @Desc("A weight of two is just as if this entry were listed twice in a row, making the biome more common. The noise picks along the list in the order it is written.")
    private int weight = 1;

    public static KList<String> keys(List<IrisBiomeEntry> entries) {
        KList<String> keys = new KList<>();
        entries.forEach(entry -> keys.add(entry.getBiome()));
        return keys;
    }

    /**
     * The entries' biomes with their weights, in list order, leaving out biomes that don't load.
     */
    public static KList<Weighted<IrisBiome>> load(List<IrisBiomeEntry> entries, ResourceLoader<IrisBiome> loader) {
        KList<Weighted<IrisBiome>> loaded = new KList<>();
        for (IrisBiomeEntry entry : entries) {
            IrisBiome biome = loader.load(entry.getBiome());
            if (biome != null) {
                loaded.add(new Weighted<>(biome, entry.getWeight()));
            }
        }
        return loaded;
    }
}
