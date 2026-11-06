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

import com.volmit.config.Desc;
import com.volmit.config.ResourceRef;

import java.util.Collections;
import java.util.List;

/** A marker of the pack, one file of {@code markers/}: object placements put markers in the world and a marker names the spawners that fire there. */
@Desc("Represents a marker")
public final class SpawnMarker {
    @Desc("A list of spawners to add to anywhere this marker is.")
    @ResourceRef(Spawner.class)
    private List<String> spawners = List.of();

    @Desc("Remove this marker when the block it's assigned to is changed.")
    private boolean removeOnChange = true;

    @Desc("If true, markers will only be placed here if there is 2 air blocks above it.")
    private boolean emptyAbove = true;

    @Desc("If this marker is used, what is the chance it removes itself. For example 25% (0.25) would mean that on average 4 uses will remove a specific marker. Set this below 0 (-1) to never exhaust & set this to 1 or higher to always exhaust on first use.")
    private double exhaustionChance = 0;

    public SpawnMarker() {
    }

    /** The keys of the spawners that fire at the marker. */
    public List<String> spawners() {
        return spawners == null ? List.of() : Collections.unmodifiableList(spawners);
    }

    public boolean removeOnChange() {
        return removeOnChange;
    }

    public boolean emptyAbove() {
        return emptyAbove;
    }

    public double exhaustionChance() {
        return exhaustionChance;
    }
}
