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

package art.arcane.iris.builtin.stilt;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

import java.util.Optional;

/** How far and with which blocks an object stilts into the ground, written as the placement's {@code stiltSettings}. */
@Snippet("stilt-settings")
@Desc("Defines stilting behaviour.")
public final class StiltSettings {
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Defines the maximum amount of blocks the object stilts verticially before overstilting and randomRange.")
    private int yMax;
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Defines the upper boundary for additional blocks after overstilting and/or maxStiltRange.")
    private int yRand;
    @MaxNumber(64)
    @MinNumber(0)
    @Desc("If the place mode is set to stilt, you can over-stilt it even further into the ground. Especially useful when using fast stilt due to inaccuracies.")
    private int overStilt;
    @Desc("If defined, stilting will be done using this block palette rather than the last layer of the object.")
    private PaletteSpec palette;
    @MinNumber(1)
    @MaxNumber(256)
    @Desc("For ORGANIC_STILT / CEILING_HANG: the maximum number of blocks to scan toward the cave floor (or ceiling) looking for solid ground before giving up.")
    private int organicMaxScan = 48;
    @MinNumber(0)
    @MaxNumber(32)
    @Desc("For ORGANIC_STILT / CEILING_HANG: the maximum number of blocks each column's stilt is randomly shortened by, giving the underside a ragged organic edge instead of a flat disc.")
    private int organicJitter = 3;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("For ORGANIC_STILT / CEILING_HANG: in the deepest fraction of each stilt column, blocks are randomly skipped to break the tip up. Higher = more broken and scratchy. 0 disables.")
    private double organicScratch = 0.55;

    public StiltSettings() {
    }

    public int yMax() {
        return yMax;
    }

    public int yRand() {
        return yRand;
    }

    public int overStilt() {
        return overStilt;
    }

    public Optional<PaletteSpec> palette() {
        return Optional.ofNullable(palette);
    }

    public int organicMaxScan() {
        return organicMaxScan;
    }

    public int organicJitter() {
        return organicJitter;
    }

    public double organicScratch() {
        return organicScratch;
    }
}
