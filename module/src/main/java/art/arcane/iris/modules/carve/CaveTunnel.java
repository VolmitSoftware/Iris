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
import com.volmit.config.Range;

/** The tunnels of a {@link Cave}: long winding passages. Thin ones link other caves together. */
@Desc("Long winding passages.")
public final class CaveTunnel {
    @Desc("Tunnel width in blocks. A tunnel varies between the two along its length.")
    private Range width = Range.of(4, 7);

    @MinNumber(16)
    @MaxNumber(512)
    @Desc("Roughly how many blocks a tunnel runs between turns and junctions. Larger gives longer, straighter and sparser tunnels.")
    private int stretch = 96;

    @MinNumber(1)
    @MaxNumber(4)
    @Desc("How level tunnels run. 1 lets them go in any direction, higher keeps them closer to horizontal.")
    private double flatness = 1.4;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Share of the underground that has tunnels at all. 1 puts them everywhere, lower leaves areas without any.")
    private double coverage = 1;

    public CaveTunnel() {
    }

    public Range width() {
        return width == null ? Range.of(4, 7) : width;
    }

    public int stretch() {
        return stretch;
    }

    public double flatness() {
        return flatness;
    }

    public double coverage() {
        return coverage;
    }
}
