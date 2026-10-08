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

import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/** Module {@code iris:stilt}: the stilts of the stilt and ceiling placement modes. */
public final class StiltStages {
    /** Finds the layers to repeat. */
    public static final BlockStage<StiltLayers> LAYERS = Stage.placementBlock("iris:stilt/layers", StiltLayers.class);
    /** Grows the stilt once the object is written. */
    public static final PlacementStage<Void> FILL = Stage.placement("iris:stilt/fill");

    private StiltStages() {
    }
}
