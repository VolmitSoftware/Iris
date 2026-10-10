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

package art.arcane.iris.engine.object.marker;

import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

@Snippet("object-marker")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find blocks to mark")
@Data
public class IrisObjectMarker {
    private final transient AtomicCache<KList<PlatformBlockState>> findData = new AtomicCache<>();
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
    @RegistryListResource(SpawnMarker.class)
    @Desc("The marker to add")
    private String marker;

    public KList<PlatformBlockState> getMark(IrisData rdata) {
        return findData.aquire(() ->
        {
            KList<PlatformBlockState> b = new KList<>();

            for (IrisBlockData i : mark) {
                PlatformBlockState bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }
}
