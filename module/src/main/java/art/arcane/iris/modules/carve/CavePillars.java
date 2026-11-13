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
import com.volmit.noise.NoiseStyle;

import java.util.Optional;
import java.util.OptionalInt;

/** The pillars of a {@link CaveCavern}: columns of rock left standing from floor to ceiling. */
@Desc("Rock columns left standing inside caverns.")
public final class CavePillars {
    @MinNumber(6)
    @MaxNumber(256)
    @Desc("Rough distance between pillars in blocks.")
    private int spacing = 22;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Rough thickness of a pillar at its middle, in blocks. Pillars widen towards floor and ceiling.")
    private int thickness = 4;

    @MinNumber(1)
    @MaxNumber(16)
    @Desc("How many times more ground a pillar covers at floor and ceiling than at its middle. 1 gives straight columns.")
    private double flare = 4;

    @MinNumber(8)
    @MaxNumber(512)
    @Desc("Blocks over which the pillars change with height, so they lean, pinch and break off. Detail under about 12 blocks is lost. Leave out for columns that run straight from floor to ceiling.")
    private Integer vertical;

    @Desc("Noise that places the pillars. Scaled to spacing, so its zoom has no effect; a fracture stays in blocks. Leave out for evenly scattered pillars.")
    private NoiseStyle style;

    public CavePillars() {
    }

    public double flare() {
        return flare;
    }

    /** Blocks over which the pillars change with height; empty for pillars that run straight. */
    public OptionalInt vertical() {
        return vertical == null ? OptionalInt.empty() : OptionalInt.of(vertical);
    }

    /** The noise that places the pillars; empty where the pack wrote none. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }

    public int spacing() {
        return spacing;
    }

    public int thickness() {
        return thickness;
    }
}
