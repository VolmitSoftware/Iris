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

package art.arcane.iris.structure.object;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("object-translator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Translate objects")
@Data
public class IrisObjectTranslate {
    @MinNumber(-128)
    @MaxNumber(128)
    @Description("The x shift in blocks")
    private int x = 0;

    @Required
    @MinNumber(-128)
    @MaxNumber(128)
    @Description("The y shift in blocks")
    private int y = 0;

    @MinNumber(-128)
    @MaxNumber(128)
    @Description("Adds an additional amount of height randomly (translateY + rand(0 - yRandom))")
    private int yRandom = 0;

    @MinNumber(-128)
    @MaxNumber(128)
    @Description("The z shift in blocks")
    private int z = 0;

    public boolean canTranslate() {
        return x != 0 || y != 0 || z != 0;
    }

    public IrisBlockVector translate(IrisBlockVector i) {
        if (canTranslate()) {
            return (IrisBlockVector) i.clone().add(new IrisBlockVector(x, y, z));
        }

        return i;
    }

    public IrisBlockVector translate(IrisBlockVector clone, IrisObjectRotation rotation, int sx, int sy, int sz) {
        if (canTranslate()) {
            return (IrisBlockVector) clone.clone().add(rotation.rotate(new IrisBlockVector(x, y, z), sx, sy, sz));
        }

        return clone;
    }
}
