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

import com.volmit.config.Desc;
import com.volmit.config.MinItems;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/** The trees an object placement overrides when their saplings grow, an entry of the placement's {@code trees}. */
@Snippet("iris/objects/tree")
@Desc("Tree replace options for this object placer")
public final class TreeOverride {
    @Required
    @MinItems(1)
    @Desc("The types of trees overwritten by this object")
    private List<String> treeTypes = List.of();

    @Required
    @MinItems(1)
    @Desc("The size of the square of saplings this applies to (2 means a 2 * 2 sapling area)")
    private List<TreeSize> sizes = List.of();

    public TreeOverride() {
    }

    public List<String> treeTypes() {
        return treeTypes == null ? List.of() : Collections.unmodifiableList(treeTypes);
    }

    public List<TreeSize> sizes() {
        return sizes == null ? List.of() : Collections.unmodifiableList(sizes);
    }

    /** Whether this override is for a sapling area of {@code width} by {@code depth} growing the species {@code species}. */
    public boolean matches(int width, int depth, String species) {
        return matchesSize(width, depth) && matchesSpecies(species);
    }

    private boolean matchesSize(int width, int depth) {
        for (TreeSize size : sizes()) {
            if (size.matches(width, depth)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSpecies(String species) {
        for (String type : treeTypes()) {
            if (type != null && type.equalsIgnoreCase(species)) {
                return true;
            }
        }
        return false;
    }
}
