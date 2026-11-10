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

package art.arcane.iris.modules.carve;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.world.Weighted;

/** A child of a {@link CarvingEntry}: the id of another carving entry and the weight it has in the child noise's pick. */
@Desc("A carving entry in the list the child noise picks from")
public final class CarvingChild {
    @Required
    @Desc("The id of the carving entry. List the parent's own id where it should stay itself.")
    private String id = "";

    @MinNumber(Weighted.MIN_WEIGHT)
    @MaxNumber(Weighted.MAX_WEIGHT)
    @Desc("A weight of two is just as if this entry were listed twice in a row, making it more common. The noise picks along the list in the order it is written.")
    private int weight = 1;

    private CarvingChild() {
    }

    public CarvingChild(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }

    public String id() {
        return id == null ? "" : id;
    }

    public int weight() {
        return weight;
    }
}
