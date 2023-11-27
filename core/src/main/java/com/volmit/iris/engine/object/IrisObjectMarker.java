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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Snippet("object-marker")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find blocks to mark")
@Data
public class IrisObjectMarker {
    private final transient AtomicCache<KList<BlockData>> findData = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Required
    @Desc("Find block types to mark")
    private KList<IrisBlockData> mark = new KList<>();
    @MinNumber(1)
    @MaxNumber(16)
    @Desc("The maximum amount of markers to place. Use these sparingly!")
    private int maximumMarkers = 8;
    @Desc("If true, markers will only be placed if the block matches the mark list perfectly.")
    private boolean exact = false;
    @Required
    @RegistryListResource(IrisMarker.class)
    @Desc("The marker to add")
    private String marker;

    public KList<BlockData> getMark(IrisData rdata) {
        return findData.aquire(() ->
        {
            KList<BlockData> b = new KList<>();

            for (IrisBlockData i : mark) {
                BlockData bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }
}
