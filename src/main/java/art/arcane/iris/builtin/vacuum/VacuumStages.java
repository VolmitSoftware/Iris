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

package art.arcane.iris.builtin.vacuum;

import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/** Module {@code iris:vacuum}: bending the terrain to meet objects of the vacuum placement modes. */
public final class VacuumStages {
    public static final PlacementStage<Void> TERRAIN = Stage.placement("iris:vacuum/terrain");

    private VacuumStages() {
    }
}
