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
import art.arcane.iris.pack.schema.annotation.AcceptsPlainNumber;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;

import java.util.Optional;

/**
 * Where the tunnels of a {@link CaveTunnel} occur: the share of the underground that has them, in areas a noise shapes,
 * and how gradually they thin out at the edge of an area. Written as the share alone, {@code 0.4}, or as
 * {@code {"share": 0.4, "softness": 0.3, "style": {...}}} with each member optional.
 */
@AcceptsPlainNumber
@Description("Where tunnels occur: a share, or {share, softness, style}.")
public final class CaveTunnelCoverage {
    /** The softness that thins tunnels out over the same stretch they always did before softness was a key. */
    public static final double DEFAULT_SOFTNESS = 0.3D;
    static final CaveTunnelCoverage EVERYWHERE = new CaveTunnelCoverage();

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Share of the underground that has tunnels, 0 to 1. A range changes over about " + CaveTunnel.VALUE_CHANGE_SPACINGS + " horizontal spacings.")
    private CaveValue share = CaveValue.of(1);

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How gradually tunnels thin out at the edge of an area, 0 for abrupt dead ends to 1 for long tails.")
    private CaveValue softness = CaveValue.of(DEFAULT_SOFTNESS);

    @Description("The noise that shapes the areas with tunnels. Leave out for areas a few spacings across.")
    private IrisGeneratorStyle style;

    public CaveTunnelCoverage() {
    }

    static CaveTunnelCoverage of(CaveValue share, CaveValue softness, IrisGeneratorStyle style) {
        CaveTunnelCoverage coverage = new CaveTunnelCoverage();
        coverage.share = share;
        coverage.softness = softness;
        coverage.style = style;
        return coverage;
    }

    /** Share of the underground that has tunnels, from 0 to 1. */
    public CaveValue share() {
        return share;
    }

    /** How gradually tunnels thin out at the edge of an area, from 0 for abrupt ends to 1 for long tails. */
    public CaveValue softness() {
        return softness;
    }

    /** The noise that shapes the areas with tunnels; empty where the pack wrote none. */
    public Optional<IrisGeneratorStyle> style() {
        return Optional.ofNullable(style);
    }

    boolean isWrittenAsShareOnly() {
        return style == null && softness.min() == DEFAULT_SOFTNESS && !softness.varies();
    }
}
