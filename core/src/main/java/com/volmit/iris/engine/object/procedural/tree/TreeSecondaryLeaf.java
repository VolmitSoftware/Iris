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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.procedural.ProceduralObjectCatalog;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBlockType;
import com.volmit.iris.engine.object.annotations.Required;

@Snippet(ProceduralObjectCatalog.PART_FOLDER + "/tree-secondary-leaves")
@Desc("A weighted accent leaf or foliage block scattered through the canopy (blossoms, shroomlight, berries).")
public final class TreeSecondaryLeaf {
    @Required
    @Desc("The block id to scatter, e.g. minecraft:shroomlight")
    @RegistryListBlockType
    private String block = "";

    @MinNumber(0)
    @Desc("The relative weight of this block among the secondary leaves.")
    private int weight = 1;

    public TreeSecondaryLeaf() {
    }

    public String block() {
        return block;
    }

    public int weight() {
        return weight;
    }
}
