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

import art.arcane.volmlib.util.math.RNG;

/**
 * The chunk whose mantle {@code iris:objects} is placing into: the one place objects are written. Every placement here
 * runs Iris's placement stages, so it is what a pack's {@code objects} get when they are placed anywhere. Reached through
 * {@link ObjectHost#chunk()} from a mantle stage.
 */
public interface ObjectChunk {
    /** The voids the cave carver planned around the chunk. */
    CarvedSpace carved();

    /** Whether the golden-debug switch is on for the chunk, which logs every decision of its placements. */
    boolean goldenDebug(int chunkX, int chunkZ);

    /**
     * Places an object on the terrain, where it sits at the height its place mode finds.
     *
     * @param goldenTag logs the placer's questions under this tag, or null to leave them
     * @return the height the object was placed at, negative when the placement rejected it
     */
    int placeOnSurface(PlaceableObject object, int x, int z, PlacementSource placement, PlacementChanges changes,
                       PlacementTags tags, RNG rng, String goldenTag);

    /**
     * Places an object in a cave, anchored at {@code anchorY}, and writes it only when all of it lies inside the cave and
     * no deeper than {@code minDepthBelowSurface} under the surface.
     *
     * @param goldenTag logs the placer's questions under this tag, or null to leave them
     */
    CavePlacement placeInCave(PlaceableObject object, int x, int anchorY, int z, PlacementSource placement, PlacementChanges changes,
                              int minDepthBelowSurface, PlacementTags tags, RNG rng, String goldenTag);

    /** Places an object at a given height, which the terrain stacked above uses for the objects hung from it. */
    int placeUpper(PlaceableObject object, int x, int anchorY, int z, ObjectPlacement placement, PlacementChanges changes,
                   PlacementTags tags, RNG rng);
}
