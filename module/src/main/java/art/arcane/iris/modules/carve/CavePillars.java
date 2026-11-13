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

import art.arcane.iris.modules.carve.CaveValue.Bounds;
import com.volmit.config.Desc;
import com.volmit.noise.NoiseStyle;

import java.util.List;
import java.util.Optional;

/** The pillars of a {@link CaveCavern}: columns of rock left standing from floor to ceiling. */
@Desc("Rock columns left standing inside caverns.")
public final class CavePillars {
    private static final Bounds SPACING_BLOCKS = new Bounds(6, 256);
    private static final Bounds THICKNESS_BLOCKS = new Bounds(1, 64);
    private static final Bounds FLARE = new Bounds(1, 16);
    private static final Bounds VERTICAL_BLOCKS = new Bounds(8, 512);

    @Desc("Rough distance between pillars in blocks, 6 to 256. A range changes over about " + CaveCavern.VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue spacing = CaveValue.of(22);

    @Desc("Rough thickness of a pillar at its middle, in blocks, 1 to 64. Pillars widen towards floor and ceiling. A range changes over about "
            + CaveCavern.VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue thickness = CaveValue.of(4);

    @Desc("How many times more ground a pillar covers at floor and ceiling than at its middle, 1 to 16. 1 gives straight columns. A range changes over about "
            + CaveCavern.VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue flare = CaveValue.of(4);

    @Desc("Blocks over which the pillars change with height, 8 to 512, so they lean, pinch and break off. Detail under about 12 blocks is lost. A range changes over about "
            + CaveCavern.VALUE_CHANGE_SIZES + " room sizes. Leave out for columns that run straight from floor to ceiling.")
    private CaveValue vertical;

    @Desc("Noise that places the pillars. Scaled to spacing, so its zoom has no effect; a fracture stays in blocks. Leave out for evenly scattered pillars.")
    private NoiseStyle style;

    public CavePillars() {
    }

    public CaveValue flare() {
        return flare;
    }

    /** Blocks over which the pillars change with height; empty for pillars that run straight. */
    public Optional<CaveValue> vertical() {
        return Optional.ofNullable(vertical);
    }

    /** The noise that places the pillars; empty where the pack wrote none. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }

    public CaveValue spacing() {
        return spacing;
    }

    public CaveValue thickness() {
        return thickness;
    }

    void requireValuesWithinBounds(String path, List<String> problems) {
        spacing.requireWithin(SPACING_BLOCKS, path + ".spacing", problems);
        thickness.requireWithin(THICKNESS_BLOCKS, path + ".thickness", problems);
        flare.requireWithin(FLARE, path + ".flare", problems);
        vertical().ifPresent(blocks -> blocks.requireWithin(VERTICAL_BLOCKS, path + ".vertical", problems));
    }
}
