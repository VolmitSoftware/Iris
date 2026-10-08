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

package art.arcane.iris.builtin.objects;

import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;

/** Module {@code iris:objects}: the objects the dimension's regions and biomes place. */
public final class ObjectStages {
    /** Places surface, cave and upper-dimension objects into the mantle, after the caves they anchor in. */
    public static final MantleStage<Void> PLACE = Stage.mantle("iris:objects/place");

    private ObjectStages() {
    }
}
