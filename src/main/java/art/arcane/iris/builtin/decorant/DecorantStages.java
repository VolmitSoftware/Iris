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

package art.arcane.iris.builtin.decorant;

import com.volmit.stage.ChunkStage;
import com.volmit.stage.Stage;

/** Module {@code iris:decorant}: the decorators of biomes, such as grass, flowers and cave ceilings. */
public final class DecorantStages {
    /** Places every column's surface, sea, shore and cave decorators. */
    public static final ChunkStage<Void> DECORATE = Stage.chunk("iris:decorant/decorate");

    private DecorantStages() {
    }
}
