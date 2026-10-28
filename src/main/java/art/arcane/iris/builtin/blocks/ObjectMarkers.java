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

package art.arcane.iris.builtin.blocks;

import com.volmit.placement.PlacedMarker;
import com.volmit.resource.ResourceId;
import com.volmit.geometry.BlockPosition;

import java.util.Map;

/**
 * The placement markers Iris keeps: a pack marker, type {@code iris:marker}, naming the pack's marker under the
 * {@code marker} attribute; a placed object's block that is a tree's ({@code iris:tree_material}), which Iris keeps
 * as the block's tree material for felling and growth; and a buried treasure chest ({@code iris:buried_treasure}).
 */
public final class ObjectMarkers {
    public static final ResourceId TYPE = ResourceId.parse("iris:marker");
    public static final ResourceId TREE_MATERIAL = ResourceId.parse("iris:tree_material");
    public static final ResourceId BURIED_TREASURE = ResourceId.parse("iris:buried_treasure");
    public static final String MARKER_ATTRIBUTE = "marker";

    private ObjectMarkers() {
    }

    public static PlacedMarker treeMaterial(BlockPosition position) {
        return new PlacedMarker(position, TREE_MATERIAL, Map.of());
    }

    public static PlacedMarker buriedTreasure(BlockPosition position) {
        return new PlacedMarker(position, BURIED_TREASURE, Map.of());
    }

    /** {@code markerKey} is the pack's marker, such as {@code spawn/zombie}. */
    public static PlacedMarker of(BlockPosition position, String markerKey) {
        return new PlacedMarker(position, TYPE, Map.of(MARKER_ATTRIBUTE, markerKey));
    }

    public static String markerKey(PlacedMarker marker) {
        requireIrisMarker(marker);
        return marker.attributes().get(MARKER_ATTRIBUTE);
    }

    public static void requireIrisMarker(PlacedMarker marker) {
        if (!marker.type().equals(TYPE) || !marker.attributes().containsKey(MARKER_ATTRIBUTE)) {
            throw new IllegalArgumentException("Iris stores markers of type " + TYPE + " with a \"" + MARKER_ATTRIBUTE
                    + "\" attribute only, not " + marker.type() + " " + marker.attributes());
        }
    }
}
