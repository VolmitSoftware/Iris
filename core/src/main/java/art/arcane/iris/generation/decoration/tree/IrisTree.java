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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.TreeType;

@Snippet("tree")
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Description("Tree replace options for this object placer")
@Data
public class IrisTree {
    @Required
    @Description("The types of trees overwritten by this object")
    @ArrayType(min = 1, type = String.class)
    private KList<String> treeTypes = new KList<>();


    @Required
    @Description("The size of the square of saplings this applies to (2 means a 2 * 2 sapling area)")
    @ArrayType(min = 1, type = IrisTreeSize.class)
    private KList<IrisTreeSize> sizes = new KList<>();


    public boolean matches(IrisTreeSize size, TreeType type) {
        if (!matchesSize(size)) {
            return false;
        }

        return matchesType(type);
    }

    private boolean matchesSize(IrisTreeSize size) {
        for (IrisTreeSize i : getSizes()) {
            if ((i.getDepth() == size.getDepth() && i.getWidth() == size.getWidth()) || (i.getDepth() == size.getWidth() && i.getWidth() == size.getDepth())) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesType(TreeType type) {
        if (type == null) {
            return false;
        }

        String name = type.name();
        for (String i : getTreeTypes()) {
            if (i != null && i.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }
}