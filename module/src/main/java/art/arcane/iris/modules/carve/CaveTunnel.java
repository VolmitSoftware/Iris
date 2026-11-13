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
import com.volmit.config.Range;
import com.volmit.config.Required;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;

import java.util.Optional;

/** The tunnels of a {@link Cave}: long winding passages. Thin ones link other caves together. */
@Desc("Long winding passages.")
public final class CaveTunnel {
    private static final Range DEFAULT_WIDTH = Range.of(4, 7);
    /**
     * The style a styled range gets when the pack writes none: white noise, which would make a tunnel's width jump
     * block by block. A width with this style follows the default width noise instead.
     */
    private static final NoiseStyle STYLE_OF_A_RANGE_WRITTEN_WITHOUT_ONE = NoiseStyle.of("STATIC");

    @Desc("Tunnel width in blocks. The style picks where a tunnel is narrow or wide; leave it out for a slow change that scales with spacing.")
    private StyledRange width;

    @Desc("Tunnel height in blocks, low where the tunnel is narrow and tall where it is wide. Leave out for round tunnels.")
    private Range height;

    @Required
    @Desc("Blocks between neighbouring tunnels.")
    private CaveTunnelSpacing spacing = new CaveTunnelSpacing();

    @Desc("Noise that bends the tunnels: zoom sets how often they turn, multiplier how many blocks they swing. A flat style keeps them straight. Leave out for a gentle bend that scales with spacing.")
    private NoiseStyle warp;

    @Desc("Where tunnels occur. Leave out to put them everywhere.")
    private CaveTunnelCoverage coverage;

    public CaveTunnel() {
    }

    public Range width() {
        return width == null ? DEFAULT_WIDTH : Range.of(width.min(), width.max());
    }

    /** The noise that picks the width along a tunnel; empty where the pack wrote none. */
    public Optional<NoiseStyle> widthStyle() {
        if (width == null || width.style().equals(STYLE_OF_A_RANGE_WRITTEN_WITHOUT_ONE)) {
            return Optional.empty();
        }
        return Optional.of(width.style());
    }

    /** The height range of an oval tunnel; empty for a round one. */
    public Optional<Range> height() {
        return Optional.ofNullable(height);
    }

    public CaveTunnelSpacing spacing() {
        return spacing == null ? new CaveTunnelSpacing() : spacing;
    }

    /** The noise that bends the tunnels; empty where the pack wrote none. */
    public Optional<NoiseStyle> warp() {
        return Optional.ofNullable(warp);
    }

    public CaveTunnelCoverage coverage() {
        return coverage == null ? new CaveTunnelCoverage() : coverage;
    }
}
