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

/** How far apart the tunnels of a {@link CaveTunnel} run, side by side and stacked. */
@Desc("Blocks between neighbouring tunnels.")
public final class CaveTunnelSpacing {
    @MinNumber(16)
    @MaxNumber(512)
    @Desc("Blocks between tunnels side by side. Larger gives longer, straighter and sparser tunnels.")
    private int horizontal = 96;

    @MinNumber(8)
    @MaxNumber(512)
    @Desc("Blocks between tunnels stacked above each other. Below horizontal keeps tunnels closer to level.")
    private int vertical = 69;

    public CaveTunnelSpacing() {
    }

    public int horizontal() {
        return horizontal;
    }

    public int vertical() {
        return vertical;
    }
}
