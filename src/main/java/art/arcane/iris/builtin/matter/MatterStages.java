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

package art.arcane.iris.builtin.matter;

import com.volmit.stage.ChunkStage;
import com.volmit.stage.Stage;

/** Module {@code iris:matter}: the blocks the mantle planned for a chunk, such as objects and structures. */
public final class MatterStages {
    /** Copies the chunk's planned mantle blocks into it. */
    public static final ChunkStage<Void> INSERT = Stage.chunk("iris:matter/insert");

    private MatterStages() {
    }
}
