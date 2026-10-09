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

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

/** How far an object is shifted from where it would be placed, written as a placement's {@code translate}. */
@Snippet("object-translator")
@Desc("Translate objects")
public final class ObjectTranslate {
    @MinNumber(-128)
    @MaxNumber(128)
    @Desc("The x shift in blocks")
    private int x = 0;

    @Required
    @MinNumber(-128)
    @MaxNumber(128)
    @Desc("The y shift in blocks")
    private int y = 0;

    @MinNumber(-128)
    @MaxNumber(128)
    @Desc("Adds an additional amount of height randomly (translateY + rand(0 - yRandom))")
    private int yRandom = 0;

    @MinNumber(-128)
    @MaxNumber(128)
    @Desc("The z shift in blocks")
    private int z = 0;

    public ObjectTranslate() {
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int yRandom() {
        return yRandom;
    }

    public int z() {
        return z;
    }
}
