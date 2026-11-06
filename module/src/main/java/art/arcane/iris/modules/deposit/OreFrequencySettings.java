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

package art.arcane.iris.modules.deposit;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** A biome's {@code oreDepositFrequencyMultiplier}, written as the bare number. */
@Desc("Multiplier applied to the frequency of every ore deposit whose center is inside this biome. A value of 0.4 keeps 40% of ore veins while leaving non-ore deposits unchanged.")
@Feature(id = "iris:deposit/ore-frequency", name = "Ore deposit frequency", key = "oreDepositFrequencyMultiplier", root = "multiplier", targets = Level.BIOME)
public final class OreFrequencySettings implements IrisFeature {
    private static final double UNCHANGED = 1D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Multiplier applied to the frequency of every ore deposit whose center is inside this biome. A value of 0.4 keeps 40% of ore veins while leaving non-ore deposits unchanged.")
    private double multiplier = UNCHANGED;

    public OreFrequencySettings() {
    }

    public OreFrequencySettings(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }
}
