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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.world.entity.IrisMarker;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
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
@Description("Find blocks to mark")
@Data
public class IrisObjectMarker {
    private final transient AtomicCache<KList<PlatformBlockState>> findData = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Required
    @Description("Find block types to mark")
    private KList<IrisBlockData> mark = new KList<>();
    @MinNumber(1)
    @MaxNumber(16)
    @Description("The maximum amount of markers to place. Use these sparingly!")
    private int maximumMarkers = 8;
    @Description("If true, markers will only be placed if the block matches the mark list perfectly.")
    private boolean exact = false;
    @Required
    @RegistryListResource(IrisMarker.class)
    @Description("The marker to add")
    private String marker;

    public KList<PlatformBlockState> getMark(IrisData rdata) {
        return findData.aquire(() ->
        {
            KList<PlatformBlockState> b = new KList<>();

            for (IrisBlockData i : mark) {
                PlatformBlockState bx = i.getBlockDataOrPlaceholder(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }
}
