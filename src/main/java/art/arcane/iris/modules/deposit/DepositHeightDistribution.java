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

import com.volmit.config.Desc;

@Desc("Controls how a deposit chooses its origin height within the configured vertical band.")
public enum DepositHeightDistribution {
    @Desc("Samples uniformly after clipping the configured band to the terrain and build-height bounds.")
    CLIPPED_UNIFORM,
    @Desc("Samples uniformly from the configured band, then rejects origins outside the terrain or build-height bounds.")
    UNIFORM,
    @Desc("Samples toward the middle of the configured band with a triangular distribution, then rejects invalid origins.")
    TRIANGLE
}
