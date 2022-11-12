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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("tree-size")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Sapling override object picking options")
@Data
public class IrisTreeSize {

    @Required
    @Desc("The width of the sapling area")
    int width = 1;

    @Required
    @Desc("The depth of the sapling area")
    int depth = 1;

    /**
     * Does the size match
     *
     * @param size the size to check match
     * @return true if it matches (fits within width and depth)
     */
    public boolean doesMatch(IrisTreeSize size) {
        return (width == size.getWidth() && depth == size.getDepth()) || (depth == size.getWidth() && width == size.getDepth());
    }
}
