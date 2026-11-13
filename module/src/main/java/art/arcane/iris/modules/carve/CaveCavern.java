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

/** The caverns of a {@link Cave}: large open rooms, with pillars left standing in them where the cave asks for some. */
@Desc("Large open rooms.")
public final class CaveCavern {
    /** Without a style, a value of the caverns or their pillars changes over this many room sizes. */
    public static final int VALUE_CHANGE_SIZES = 4;
    private static final Bounds SIZE_BLOCKS = new Bounds(16, 512);
    private static final Bounds HEIGHT_BLOCKS = new Bounds(8, 256);
    private static final Bounds COVERAGE_SHARE = new Bounds(0, 0.6);
    private static final Bounds ROUGHNESS = new Bounds(0, 1);

    @Desc("Rough span of a room in blocks, 16 to 512. A range changes over about " + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue size = CaveValue.of(56);

    @Desc("Rough height of a room in blocks, 8 to 256. A range changes over about " + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue height = CaveValue.of(22);

    @Desc("Share of the underground that is cavern, 0 to 0.6. 0.1 gives scattered rooms, 0.3 joins them into halls. A range changes over about "
            + VALUE_CHANGE_SIZES + " room sizes; a min of 0 leaves areas without rooms. A range in size, height or roughness makes the share approximate.")
    private CaveValue coverage = CaveValue.of(0.1);

    @Desc("Strength of the finer noise on room walls, 0 to 1. 0 gives smooth bubbles. Ignored with a style, and in rooms under about 20 blocks tall or across. A range changes over about "
            + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue roughness = CaveValue.of(0.3);

    @Desc("Noise that shapes the rooms. Scaled to size and height, so its zoom has no effect; a fracture stays in blocks. Detail under about 12 blocks is lost. Leave out for smooth rooms with rough walls.")
    private NoiseStyle style;

    @Desc("Rock columns left standing inside the rooms. Leave out for open rooms.")
    private CavePillars pillars;

    public CaveCavern() {
    }

    /** The noise that shapes the rooms; empty where the pack wrote none. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }

    public CaveValue size() {
        return size;
    }

    public CaveValue height() {
        return height;
    }

    public CaveValue coverage() {
        return coverage;
    }

    public CaveValue roughness() {
        return roughness;
    }

    public Optional<CavePillars> pillars() {
        return Optional.ofNullable(pillars);
    }

    void requireValuesWithinBounds(String path, List<String> problems) {
        size.requireWithin(SIZE_BLOCKS, path + ".size", problems);
        height.requireWithin(HEIGHT_BLOCKS, path + ".height", problems);
        coverage.requireWithin(COVERAGE_SHARE, path + ".coverage", problems);
        roughness.requireWithin(ROUGHNESS, path + ".roughness", problems);
        pillars().ifPresent(cavernPillars -> cavernPillars.requireValuesWithinBounds(path + ".pillars", problems));
    }
}
