/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.loot;

import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;
import com.volmit.item.ItemDrop;

import java.util.List;

@Snippet("block-drops")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a block drop list")
@Data
public class IrisBlockDrops {
    private final transient AtomicCache<KList<PlatformBlockState>> data = new AtomicCache<>();
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The blocks that drop loot")
    private KList<IrisBlockData> blocks = new KList<>();
    @Desc("If exact blocks is set to true, minecraft:barrel[axis=x] will only drop for that axis. When exact is false (default) any barrel will drop the defined drops.")
    private boolean exactBlocks = false;
    @Desc("Add in specific items to drop")
    @ArrayType(min = 1, type = IrisLoot.class)
    private KList<IrisLoot> drops = new KList<>();
    @Desc("If this is in a biome, setting skipParents to true will ignore the drops in the region and dimension for this block type. The default (false) will allow all three nodes to fire and add to a list of drops.")
    private boolean skipParents = false;
    @Desc("Removes the default vanilla block drops and only drops the given items & any parent loot tables specified for this block type.")
    private boolean replaceVanillaDrops = false;

    public boolean shouldDropFor(PlatformBlockState broken, IrisData rdata) {
        KList<PlatformBlockState> list = this.data.aquire(() -> {
            KList<PlatformBlockState> b = new KList<>();

            for (IrisBlockData i : getBlocks()) {
                PlatformBlockState state = i.getBlockData(rdata);

                if (state != null) {
                    b.add(state);
                }
            }

            return b.removeDuplicates();
        });

        for (PlatformBlockState i : list) {
            if (exactBlocks ? i.key().equals(broken.key()) : materialOf(i).equals(materialOf(broken))) {
                return true;
            }
        }

        return false;
    }

    /** Adds the items of the drops that pass their rarity to {@code d}. */
    public void fillDrops(LootItems items, List<ItemDrop> d) {
        for (IrisLoot i : getDrops()) {
            if (!LootResolver.oneIn(RNG.r, i.getRarity())) {
                continue;
            }
            ItemDrop drop = items.give(i, RNG.r);
            if (drop != null) {
                d.add(drop);
            }
        }
    }

    private static String materialOf(PlatformBlockState state) {
        String key = state.key();
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
    }
}
