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

package art.arcane.iris.builtin.prepare;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.PreparedPlacement;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/** Module {@code iris:prepare}: what a placement decides before it looks for its height. */
public final class PrepareStages {
    /** The settings the placement uses: the requested ones, turned downhill when they ask to face the slope. Rejects {@code slope}. */
    public static final PlacementStage<IrisObjectPlacement> SLOPE = Stage.placement("iris:prepare/slope", IrisObjectPlacement.class);
    /** The random rotation, translation and height offset, chosen once per placement. */
    public static final PlacementStage<PreparedPlacement> TRANSFORMS = Stage.placement("iris:prepare/transforms", PreparedPlacement.class);

    private PrepareStages() {
    }
}
