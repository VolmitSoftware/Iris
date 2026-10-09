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

package art.arcane.iris.builtin.validate;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

/** The heights an object placement may sit between, written as the placement's {@code clamp}. */
@Snippet("object-limit")
@Desc("Limits object placement by minimum and maximum world height")
public final class ObjectLimit {
    @MinNumber(-2048)
    @MaxNumber(2048)
    @Desc("The minimum height for placement (bottom of object)")
    private int minimumHeight = -2048;

    @MinNumber(-2048)
    @MaxNumber(2048)
    @Desc("The maximum height for placement (top of object)")
    private int maximumHeight = 2048;

    public ObjectLimit() {
    }

    public int minimumHeight() {
        return minimumHeight;
    }

    public int maximumHeight() {
        return maximumHeight;
    }

    /** Whether an object whose top is at {@code highest} and bottom at {@code lowest} fits between the limits. */
    public boolean canPlace(int highest, int lowest) {
        return highest <= maximumHeight && lowest >= minimumHeight;
    }
}
