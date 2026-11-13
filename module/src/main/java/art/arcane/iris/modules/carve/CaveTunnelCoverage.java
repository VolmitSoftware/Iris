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

/** Where the tunnels of a {@link CaveTunnel} occur: the share of the underground that has them, in patches a noise shapes. */
@Desc("Where tunnels occur.")
public final class CaveTunnelCoverage {
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Share of the underground that has tunnels. 1 puts them everywhere, lower leaves areas without any.")
    private double share = 1;

    @Desc("Noise that shapes the areas with tunnels. Leave out for patches that scale with spacing.")
    private NoiseStyle style;

    public CaveTunnelCoverage() {
    }

    public double share() {
        return share;
    }

    /** The noise that shapes the areas with tunnels; empty where the pack wrote none. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }
}
