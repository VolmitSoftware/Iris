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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.Desc;
import com.volmit.config.Required;

@Desc("Places copies of a part around a center: columns in a causeway, peaks on an iceberg, shards on a crystal. Each copy leans away from the center unless its stem says otherwise.")
public final class PartCluster {
    @Desc("How many copies to place.")
    private ProceduralValue count = ProceduralValue.constant(5);

    @Desc("How far from the center copies are placed, in blocks.")
    private ProceduralValue radius = ProceduralValue.constant(2);

    @Desc("How the copies are laid out.")
    private ClusterLayout layout = ClusterLayout.RANDOM;

    @Desc("How far each copy tilts away from the center, in degrees from straight up. A range gives each copy its own tilt; a curve runs from the center (0) to the edge (1).")
    private ProceduralValue tilt = ProceduralValue.constant(0);

    @Required
    @Desc("The part each copy is made of.")
    private ProceduralPart part;

    public ProceduralValue count() {
        return count == null ? ProceduralValue.constant(5) : count;
    }

    public ProceduralValue radius() {
        return radius == null ? ProceduralValue.constant(2) : radius;
    }

    public ClusterLayout layout() {
        return layout == null ? ClusterLayout.RANDOM : layout;
    }

    public ProceduralValue tilt() {
        return tilt == null ? ProceduralValue.constant(0) : tilt;
    }

    public ProceduralPart part() {
        return part;
    }
}
