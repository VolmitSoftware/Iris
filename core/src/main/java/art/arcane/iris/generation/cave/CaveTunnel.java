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

/** The tunnels of a {@link IrisCave}: long winding passages. Thin ones link other caves together. */
@Description("Long winding passages.")
public final class CaveTunnel {
    /** Without a style, the width changes over this many horizontal spacings. */
    public static final double WIDTH_CHANGE_SPACINGS = 1.5D;
    /** Without a style, any other value of the tunnels changes over this many horizontal spacings. */
    public static final int VALUE_CHANGE_SPACINGS = 6;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Tunnel width in blocks, 1 to 64. Default {min 4, max 7}. A range changes over about " + WIDTH_CHANGE_SPACINGS + " horizontal spacings.")
    private CaveValue width = CaveValue.between(4, 7);

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Tunnel height in blocks, 1 to 64. A range without a style follows the width: low where the tunnel is narrow, tall where it is wide. Leave out for round tunnels.")
    private CaveValue height;

    @Description("Blocks between neighbouring tunnels.")
    private CaveTunnelSpacing spacing = new CaveTunnelSpacing();

    @Description("Noise that bends the tunnels: zoom sets how often they turn, multiplier how many blocks they swing. A flat style keeps them straight. Leave out for a gentle bend that scales with spacing.")
    private IrisGeneratorStyle warp;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How far the warp moves tunnels up and down, as a share of how far it swings them sideways, 0 to 1. 0 adds no rise and fall. Default 0.5. A range changes over about "
            + VALUE_CHANGE_SPACINGS + " horizontal spacings.")
    private CaveValue verticalWarp = CaveValue.of(0.5);

    @Description("Where tunnels occur: a share of the underground from 0 to 1, or {share, softness, style}. Softness, 0 to 1, is how gradually tunnels thin out at the edge of an area, from abrupt dead ends to long tails; style is the noise that shapes the areas. Share and softness change over about "
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
    public Optional<IrisGeneratorStyle> warp() {
        return Optional.ofNullable(warp);
    }

    public CaveValue verticalWarp() {
        return verticalWarp;
    }

    public CaveTunnelCoverage coverage() {
        return coverage == null ? CaveTunnelCoverage.EVERYWHERE : coverage;
    }
}
