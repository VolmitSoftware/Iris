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

package art.arcane.iris.builtin.floating;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * What {@code iris:floating} asks of Iris beyond the API: the biome of any column, a biome's own terrain height, the sky
 * biome an island paints, and placing Iris objects against an island. Iris's worlds implement it; a module reaches it
 * with {@link #of}.
 */
public interface FloatingHost {
    /** The host of the world {@code settings} are the pack's of, which must be a world Iris generates. */
    static FloatingHost of(PackSettings settings) {
        if (settings instanceof Source source) {
            return source.floating();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + settings);
    }

    /** The host of {@code world}, which must be a world Iris generates. */
    static FloatingHost of(PackWorld world) {
        if (world instanceof Source source) {
            return source.floating();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + world);
    }

    /** The biome of the column's surface, the biome an island grows from; any column, not only the chunk's. */
    ResourceId surfaceBiome(int worldX, int worldZ);

    /** The biome {@code key} names in the pack; empty when the pack has none. */
    Optional<ResourceId> biome(String key);

    /** The height the terrain generators of {@code biome} give the column, as Iris shapes the biome's terrain with {@code seed}. */
    double biomeHeight(ResourceId biome, int worldX, int worldZ, long seed);

    /**
     * Paints the sky biome {@code biome} stands for at the column into the blocks {@code ys} of the mantle, where the biome
     * pass finds it. {@code random} seeds the biome's generator the first time the biome is asked.
     */
    void paintSkyBiome(ResourceId biome, int worldX, int worldZ, int[] ys, RNG random);

    /** Where {@code object} stands: its solid columns, its tallest column and that column's lowest solid block. */
    IslandFootprint footprint(PlaceableObject object);

    /** {@code object} shrunk by {@code factor} (below 1), the variant picked with {@code random}. */
    Optional<PlaceableObject> shrink(PlaceableObject object, double factor, RNG random);

    /** Places {@code object} floating free at the column, into the chunk the mantle is generating. */
    void placeFloating(PlaceableObject object, int worldX, int worldZ, ObjectPlacement placement, RNG random);

    /**
     * Places {@code object} on an island's top, its lowest block at {@code y}, into the chunk the mantle is generating; nothing
     * is written unless every block it writes is one {@code anchor} lets it write.
     */
    void placeOnTop(PlaceableObject object, int worldX, int y, int worldZ, ObjectPlacement placement, IslandAnchor anchor, RNG random);

    /**
     * Places {@code object} upside down under an island, turned {@code yRotation} degrees about Y, as {@link #placeOnTop} does
     * on top.
     */
    void placeUnder(PlaceableObject object, int worldX, int y, int worldZ, int yRotation, ObjectPlacement placement,
                    IslandAnchor anchor, RNG random);

    /** A world that hands out its {@link FloatingHost}. */
    interface Source {
        FloatingHost floating();
    }
}
