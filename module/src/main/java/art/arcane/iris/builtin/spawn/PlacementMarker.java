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

package art.arcane.iris.builtin.spawn;

import com.volmit.block.BlockSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.ResourceRef;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/** Blocks of an object placement that get a {@link SpawnMarker}, an entry of the placement's {@code markers}. */
@Snippet("iris/spawn/object-marker")
@Desc("Find blocks to mark")
public final class PlacementMarker {
    @Required
    @Desc("Find block types to mark")
    private List<BlockSpec> mark = List.of();
    @MinNumber(1)
    @MaxNumber(16)
    @Desc("The maximum amount of markers to place. Use these sparingly!")
    private int maximumMarkers = 8;
    @Desc("If true, markers will only be placed if the block matches the mark list perfectly.")
    private boolean exact = false;
    @Required
    @ResourceRef(SpawnMarker.class)
    @Desc("The marker to add")
    private String marker;

    public PlacementMarker() {
    }

    /** The blocks that get a marker. */
    public List<BlockSpec> mark() {
        return mark == null ? List.of() : Collections.unmodifiableList(mark);
    }

    public int maximumMarkers() {
        return maximumMarkers;
    }

    public boolean exact() {
        return exact;
    }

    /** The key of the {@link SpawnMarker} to place. */
    public String marker() {
        return marker;
    }
}
