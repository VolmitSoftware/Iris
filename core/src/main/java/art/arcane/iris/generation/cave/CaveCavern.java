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

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;

import java.util.Optional;

/** The caverns of a {@link IrisCave}: large open rooms, with pillars left standing in them where the cave asks for some. */
@Description("Large open rooms.")
public final class CaveCavern {
    /** Without a style, a value of the caverns or their pillars changes over this many room sizes. */
    public static final int VALUE_CHANGE_SIZES = 4;

    @MinNumber(16)
    @MaxNumber(512)
    @Description("Rough span of a room in blocks, 16 to 512. Default 56. A range changes over about " + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue size = CaveValue.of(56);

    @MinNumber(8)
    @MaxNumber(256)
    @Description("Rough height of a room in blocks, 8 to 256. Default 22. A range changes over about " + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue height = CaveValue.of(22);

    @MinNumber(0)
    @MaxNumber(0.6)
    @Description("Share of the underground that is cavern, 0 to 0.6. Default 0.1. 0.1 gives scattered rooms, 0.3 joins them into halls. A range changes over about "
            + VALUE_CHANGE_SIZES + " room sizes; a min of 0 leaves areas without rooms. A range in size, height or roughness makes the share approximate.")
    private CaveValue coverage = CaveValue.of(0.1);

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Strength of the finer noise on room walls, 0 to 1. Default 0.3. 0 gives smooth bubbles. Ignored with a style, and in rooms under about 20 blocks tall or across. A range changes over about "
            + VALUE_CHANGE_SIZES + " room sizes.")
    private CaveValue roughness = CaveValue.of(0.3);

    @Description("Noise that shapes the rooms. Scaled to size and height, so its zoom has no effect; a fracture stays in blocks. Detail under about 12 blocks is lost. Leave out for smooth rooms with rough walls.")
    private IrisGeneratorStyle style;

    @Description("Rock columns left standing inside the rooms. Leave out for open rooms.")
    private CavePillars pillars;

    public CaveCavern() {
    }

    /** The noise that shapes the rooms; empty where the pack wrote none. */
    public Optional<IrisGeneratorStyle> style() {
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
}
