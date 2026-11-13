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
import com.volmit.config.Required;
import com.volmit.noise.NoiseStyle;

import java.util.List;
import java.util.Optional;

/** The tunnels of a {@link Cave}: long winding passages. Thin ones link other caves together. */
@Desc("Long winding passages.")
public final class CaveTunnel {
    /** Without a style, the width changes over this many horizontal spacings. */
    public static final double WIDTH_CHANGE_SPACINGS = 1.5D;
    /** Without a style, any other value of the tunnels changes over this many horizontal spacings. */
    public static final int VALUE_CHANGE_SPACINGS = 6;
    private static final Bounds WIDTH_BLOCKS = new Bounds(1, 64);
    private static final Bounds HEIGHT_BLOCKS = new Bounds(1, 64);
    private static final Bounds VERTICAL_WARP_SHARE = new Bounds(0, 1);

    @Desc("Tunnel width in blocks, 1 to 64. A range changes over about " + WIDTH_CHANGE_SPACINGS + " horizontal spacings.")
    private CaveValue width = CaveValue.between(4, 7);

    @Desc("Tunnel height in blocks, 1 to 64. A range without a style follows the width: low where the tunnel is narrow, tall where it is wide. Leave out for round tunnels.")
    private CaveValue height;

    @Required
    @Desc("Blocks between neighbouring tunnels.")
    private CaveTunnelSpacing spacing = new CaveTunnelSpacing();

    @Desc("Noise that bends the tunnels: zoom sets how often they turn, multiplier how many blocks they swing. A flat style keeps them straight. Leave out for a gentle bend that scales with spacing.")
    private NoiseStyle warp;

    @Desc("How far the warp moves tunnels up and down, as a share of how far it swings them sideways, 0 to 1. 0 adds no rise and fall. A range changes over about "
            + VALUE_CHANGE_SPACINGS + " horizontal spacings.")
    private CaveValue verticalWarp = CaveValue.of(0.5);

    @Desc("Where tunnels occur: a share of the underground from 0 to 1, or {share, softness, style}. Softness, 0 to 1, is how gradually tunnels thin out at the edge of an area, from abrupt dead ends to long tails; style is the noise that shapes the areas. Share and softness change over about "
            + VALUE_CHANGE_SPACINGS + " horizontal spacings as a range. Leave out to put tunnels everywhere.")
    private CaveTunnelCoverage coverage;

    public CaveTunnel() {
    }

    public CaveValue width() {
        return width;
    }

    /** The height of an oval tunnel; empty for a round one. */
    public Optional<CaveValue> height() {
        return Optional.ofNullable(height);
    }

    public CaveTunnelSpacing spacing() {
        return spacing == null ? new CaveTunnelSpacing() : spacing;
    }

    /** The noise that bends the tunnels; empty where the pack wrote none. */
    public Optional<NoiseStyle> warp() {
        return Optional.ofNullable(warp);
    }

    public CaveValue verticalWarp() {
        return verticalWarp;
    }

    public CaveTunnelCoverage coverage() {
        return coverage == null ? CaveTunnelCoverage.EVERYWHERE : coverage;
    }

    void requireValuesWithinBounds(String path, List<String> problems) {
        width.requireWithin(WIDTH_BLOCKS, path + ".width", problems);
        height().ifPresent(tunnelHeight -> tunnelHeight.requireWithin(HEIGHT_BLOCKS, path + ".height", problems));
        verticalWarp.requireWithin(VERTICAL_WARP_SHARE, path + ".verticalWarp", problems);
        coverage().requireValuesWithinBounds(path + ".coverage", problems);
    }
}
