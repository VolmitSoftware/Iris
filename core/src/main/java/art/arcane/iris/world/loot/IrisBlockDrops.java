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

package art.arcane.iris.world.loot;

import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

@Snippet("block-drops")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a block drop list")
@Data
public class IrisBlockDrops {
    private final transient AtomicCache<KList<BlockData>> data = new AtomicCache<>();
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The blocks that drop loot")
    private KList<IrisBlockData> blocks = new KList<>();
    @Description("If exact blocks is set to true, minecraft:barrel[axis=x] will only drop for that axis. When exact is false (default) any barrel will drop the defined drops.")
    private boolean exactBlocks = false;
    @Description("Add in specific items to drop")
    @ArrayType(min = 1, type = IrisLoot.class)
    private KList<IrisLoot> drops = new KList<>();
    @Description("If this is in a biome, setting skipParents to true will ignore the drops in the region and dimension for this block type. The default (false) will allow all three nodes to fire and add to a list of drops.")
    private boolean skipParents = false;
    @Description("Removes the default vanilla block drops and only drops the given items & any parent loot tables specified for this block type.")
    private boolean replaceVanillaDrops = false;

    public boolean shouldDropFor(BlockData data, IrisData rdata) {
        KList<BlockData> list = this.data.aquire(() -> {
            KList<BlockData> b = new KList<>();

            for (IrisBlockData i : getBlocks()) {
                PlatformBlockState state = i.getBlockData(rdata);

                if (state != null) {
                    b.add((BlockData) state.nativeHandle());
                }
            }

            return b.removeDuplicates();
        });

        for (BlockData i : list) {
            if (exactBlocks ? i.equals(data) : i.getMaterial().equals(data.getMaterial())) {
                return true;
            }
        }

        return false;
    }

    public void fillDrops(boolean debug, KList<ItemStack> d) {
        for (IrisLoot i : getDrops()) {
            if (LootResolver.oneIn(RNG.r, i.getRarity())) {
                d.add(i.get(debug, RNG.r));
            }
        }
    }
}
