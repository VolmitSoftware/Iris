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

package art.arcane.iris.generation.cave;

import art.arcane.volmlib.util.documentation.Description;

/** The world Y range a {@link IrisCave} occurs in. Either end may vary across the world. */
@Description("World Y range a cave occurs in.")
public final class CaveDepth {
    @Description("Lowest world Y. A range changes over about " + IrisCave.VALUE_CHANGE_BLOCKS + " blocks. Default 16.")
    private CaveValue min = CaveValue.of(16);

    @Description("Highest world Y. A range changes over about " + IrisCave.VALUE_CHANGE_BLOCKS + " blocks. Default 32.")
    private CaveValue max = CaveValue.of(32);

    public CaveDepth() {
    }

    public CaveValue min() {
        return min;
    }

    public CaveValue max() {
        return max;
    }
}
