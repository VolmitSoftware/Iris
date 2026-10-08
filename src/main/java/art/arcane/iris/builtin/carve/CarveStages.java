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

package art.arcane.iris.builtin.carve;

import com.volmit.stage.ChunkStage;
import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;

/** Module {@code iris:carve}: caves, planned in the mantle and cut into each chunk's terrain. */
public final class CarveStages {
    /** Plans the caves of the dimension's cave profiles into the mantle, one block into each neighbor. */
    public static final MantleStage<Void> CAVES = Stage.mantle("iris:carve/caves");
    /** Cuts the planned caves out of the chunk's terrain and decorates their floors and ceilings. */
    public static final ChunkStage<Void> APPLY = Stage.chunk("iris:carve/apply");

    private CarveStages() {
    }
}
