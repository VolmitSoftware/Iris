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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Snippet("object-block-replacer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find and replace object materials")
@Data
public class IrisObjectReplace {
    private final transient AtomicCache<CNG> replaceGen = new AtomicCache<>();
    private final transient AtomicCache<KList<BlockData>> findData = new AtomicCache<>();
    private final transient AtomicCache<KList<BlockData>> replaceData = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Required
    @Desc("Find this block")
    private KList<IrisBlockData> find = new KList<>();
    @Required
    @Desc("Replace it with this block palette")
    private IrisMaterialPalette replace = new IrisMaterialPalette();
    @Desc("Exactly match the block data or not")
    private boolean exact = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Modifies the chance the block is replaced")
    private float chance = 1;

    public KList<BlockData> getFind(IrisData rdata) {
        return findData.aquire(() ->
        {
            KList<BlockData> b = new KList<>();

            for (IrisBlockData i : find) {
                BlockData bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }

    public BlockData getReplace(RNG seed, double x, double y, double z, IrisData rdata) {
        return getReplace().get(seed, x, y, z, rdata);
    }
}
