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

package art.arcane.iris.core.modules;

import com.volmit.placement.PlacedMarker;
import com.volmit.resource.ResourceId;

/**
 * The marker types Iris keeps in its mantle when a placement marks a block ({@link PlacedMarker#type()}); a marker of
 * any other type is rejected. The built-in modules mark with these ids, and Iris knows them as ids only.
 */
final class MantleMarkerTypes {
    /** A marker the pack declares by name, kept as a pack marker; its name is the {@link #PACK_MARKER_NAME} attribute. */
    static final ResourceId PACK_MARKER = ResourceId.parse("iris:marker");
    static final String PACK_MARKER_NAME = "marker";
    /** A block of a tree, kept as the block's tree material for felling and growth. */
    static final ResourceId TREE_MATERIAL = ResourceId.parse("iris:tree_material");
    /** A buried treasure chest, kept as a structure point of interest. */
    static final ResourceId BURIED_TREASURE = ResourceId.parse("iris:buried_treasure");

    private MantleMarkerTypes() {
    }

    /** The name of the pack marker {@code marker} is. */
    static String packMarkerName(PlacedMarker marker) {
        String name = marker.attributes().get(PACK_MARKER_NAME);
        if (!marker.type().equals(PACK_MARKER) || name == null) {
            throw new IllegalArgumentException("Iris keeps markers of type " + PACK_MARKER + " with a \"" + PACK_MARKER_NAME
                    + "\" attribute, " + TREE_MATERIAL + " and " + BURIED_TREASURE + " only, not " + marker.type() + " "
                    + marker.attributes());
        }
        return name;
    }
}
