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

package art.arcane.iris.builtin.structure;

import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;

/** Module {@code iris:structure}: Iris structures, assembled from pieces. */
public final class StructureStages {
    /** Places the structures of the dimension, its regions and biomes into the mantle, after objects and floating objects. */
    public static final MantleStage<Void> PLACE = Stage.mantle("iris:structure/place");

    private StructureStages() {
    }
}
