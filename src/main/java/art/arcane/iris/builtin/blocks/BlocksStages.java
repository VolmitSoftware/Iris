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

package art.arcane.iris.builtin.blocks;

import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/** Module {@code iris:blocks}: writing the object's blocks. */
public final class BlocksStages {
    /** Writes each object block where the placement puts it. */
    public static final BlockStage<WrittenBlocks> WRITE = Stage.placementBlock("iris:blocks/write", WrittenBlocks.class);
    /** Marks some written blocks with the placement's markers. */
    public static final PlacementStage<Void> MARKERS = Stage.placement("iris:blocks/markers");
    /** Milestone: stages requiring it run after the object and its markers are written. */
    public static final PlacementStage<Void> COMPLETE = Stage.placement("iris:blocks/complete");

    private BlocksStages() {
    }
}
