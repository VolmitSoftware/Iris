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

/** The caverns of a {@link Cave}: large open rooms, with pillars left standing in them where the cave asks for some. */
@Desc("Large open rooms.")
public final class CaveCavern {
    @MinNumber(16)
    @MaxNumber(512)
    @Desc("Rough span of a room in blocks.")
    private int size = 56;

    @MinNumber(8)
    @MaxNumber(256)
    @Desc("Rough height of a room in blocks.")
    private int height = 22;

    @MinNumber(0)
    @MaxNumber(0.6)
    @Desc("Share of the underground that is cavern. 0.1 gives scattered rooms, 0.3 joins them into halls.")
    private double coverage = 0.1;

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

    public int size() {
        return size;
    }

    public int height() {
        return height;
    }

    public double coverage() {
        return coverage;
    }

    public Optional<CavePillars> pillars() {
        return Optional.ofNullable(pillars);
    }
}
