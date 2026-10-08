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

package art.arcane.iris.builtin.floating;

import com.volmit.stage.ChunkStage;
import com.volmit.stage.Stage;

/** Module {@code iris:floating}: the floating islands of floating child biomes. */
public final class FloatingStages {
    /** Writes the islands' solid blocks. */
    public static final ChunkStage<Void> SOLIDS = Stage.chunk("iris:floating/solids");
    /** Decorates the islands' tops and bottoms once the chunk's own decoration is done. */
    public static final ChunkStage<Void> DECORATE = Stage.chunk("iris:floating/decorate");

    private FloatingStages() {
    }
}
