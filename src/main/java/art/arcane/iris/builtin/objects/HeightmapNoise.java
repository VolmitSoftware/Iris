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

package art.arcane.iris.builtin.objects;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;

import java.util.Collections;
import java.util.List;

/** The virtual heightmap an object placement may sit on instead of the terrain, written as the placement's {@code heightmap}. */
@Snippet("generator")
@Desc("A noise generator")
public final class HeightmapNoise {
    @MinNumber(0.0001)
    @Desc("The coordinate input zoom")
    private double zoom = 1;
    @Desc("Reverse the output. So that noise = -noise + opacity")
    private boolean negative = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The output multiplier")
    private double opacity = 1;
    @Desc("Coordinate offset x")
    private double offsetX = 0;
    @Desc("Height output offset y. Avoid using with terrain generation.")
    private double offsetY = 0;
    @Desc("Coordinate offset z")
    private double offsetZ = 0;
    @Required
    @Desc("The seed")
    private long seed = 0;
    @Desc("Apply a parametric curve on the output")
    private boolean parametric = false;
    @Desc("Apply a bezier curve on the output")
    private boolean bezier = false;
    @Desc("Apply a sin-center curve on the output (0, and 1 = 0 and 0.5 = 1.0 using a sinoid shape.)")
    private boolean sinCentered = false;
    @Desc("The exponent noise^EXPONENT")
    private double exponent = 1;
    @Desc("Enable / disable. Outputs offsetY if disabled")
    private boolean enabled = true;
    @Required
    @Desc("The Noise Style")
    private NoiseStyle style = NoiseStyle.of("IRIS");
    @MinNumber(1)
    @Desc("Multiple octaves for multple generators of changing zooms added together")
    private int octaves = 1;
    @MinItems(1)
    @Desc("Apply a child noise generator to fracture the input coordinates of this generator")
    private List<HeightmapNoise> fracture = List.of();

    public HeightmapNoise() {
    }

    public double zoom() {
        return zoom;
    }

    public boolean negative() {
        return negative;
    }

    public double opacity() {
        return opacity;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public long seed() {
        return seed;
    }

    public boolean parametric() {
        return parametric;
    }

    public boolean bezier() {
        return bezier;
    }

    public boolean sinCentered() {
        return sinCentered;
    }

    public double exponent() {
        return exponent;
    }

    public boolean enabled() {
        return enabled;
    }

    public NoiseStyle style() {
        return style;
    }

    public int octaves() {
        return octaves;
    }

    public List<HeightmapNoise> fracture() {
        return fracture == null ? List.of() : Collections.unmodifiableList(fracture);
    }
}
