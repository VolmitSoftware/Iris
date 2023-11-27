/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.util.BlockVector;

@Snippet("object-translator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisObjectTranslate {
    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @Desc("The x shift in blocks")
    private int x = 0;

    @Required
    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @Desc("The y shift in blocks")
    private int y = 0;

    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @Desc("Adds an additional amount of height randomly (translateY + rand(0 - yRandom))")
    private int yRandom = 0;

    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @Desc("The z shift in blocks")
    private int z = 0;

    public boolean canTranslate() {
        return x != 0 || y != 0 || z != 0;
    }

    public BlockVector translate(BlockVector i) {
        if (canTranslate()) {
            return (BlockVector) i.clone().add(new BlockVector(x, y, z));
        }

        return i;
    }

    public BlockVector translate(BlockVector clone, IrisObjectRotation rotation, int sx, int sy, int sz) {
        if (canTranslate()) {
            return (BlockVector) clone.clone().add(rotation.rotate(new BlockVector(x, y, z), sx, sy, sz));
        }

        return clone;
    }
}
