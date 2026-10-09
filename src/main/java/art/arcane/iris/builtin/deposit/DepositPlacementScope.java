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

package art.arcane.iris.builtin.deposit;

import com.volmit.config.Desc;

@Desc("Controls which vertical part of the world may contain a deposit origin.")
public enum DepositPlacementScope {
    @Desc("Places in solid terrain below the generated surface while preserving the configured surface clearance.")
    TERRAIN,
    @Desc("Places only in existing solid hosts above the generated terrain surface.")
    ABOVE_TERRAIN,
    @Desc("Places in existing solid hosts anywhere within the dimension build height.")
    FULL_HEIGHT
}
