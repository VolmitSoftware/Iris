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

package art.arcane.iris.modules.objects;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

/** Whether a dimension's saplings grow the trees of its object placements, written as {@code "treeSettings": {...}}. */
@Snippet("iris/objects/tree-settings")
@Desc("Tree growth override settings")
@Feature(id = "iris:objects/trees", name = "Tree growth", key = "treeSettings", targets = Level.DIMENSION)
public final class TreeGrowthSettings implements IrisFeature {
    @Required
    @Desc("Turn replacing on and off")
    private boolean enabled = false;

    @Desc("Object picking modes")
    private TreeMode mode = TreeMode.FIRST;

    public TreeGrowthSettings() {
    }

    public boolean enabled() {
        return enabled;
    }

    public TreeMode mode() {
        return mode == null ? TreeMode.FIRST : mode;
    }
}
