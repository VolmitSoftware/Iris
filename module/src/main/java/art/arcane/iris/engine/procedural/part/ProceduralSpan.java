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
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

@Desc("A stretch between two points from 0 to 1, such as from the base (0) to the tip (1) of a part.")
public final class ProceduralSpan {
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Where the stretch starts.")
    private double from = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Where the stretch ends.")
    private double to = 1;

    public ProceduralSpan() {
    }

    public ProceduralSpan(double from, double to) {
        this.from = from;
        this.to = to;
    }

    public double from() {
        return Math.min(from, to);
    }

    public double to() {
        return Math.max(from, to);
    }

    public boolean contains(double t) {
        return t >= from() - 1e-9 && t <= to() + 1e-9;
    }
}
