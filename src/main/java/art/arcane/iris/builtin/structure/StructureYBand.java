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

package art.arcane.iris.builtin.structure;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/**
 * A band of absolute world heights a platform structure is moved into, as packs write it: {"min": -120, "max": -20}.
 * Unlike the API's heights these are world Y, as the platform's own structure definitions use them. Reversed bounds
 * mean the same band.
 */
@Desc("An absolute world Y band a native structure is relocated into. Reversed bounds are normalized.")
public final class StructureYBand {
    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Lowest absolute world Y of the band.")
    private int min = 0;

    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Highest absolute world Y of the band.")
    private int max = 0;

    public StructureYBand() {
    }

    public int resolvedMin() {
        return Math.min(min, max);
    }

    public int resolvedMax() {
        return Math.max(min, max);
    }
}
