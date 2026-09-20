/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.spi;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.util.List;
import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;

/**
 * Neutral access to the host platform's structure, structure-set and configured-feature registries plus placement entry points.
 * <p>
 * The key enumerations read registries and are safe from any thread. The two placement methods write blocks
 * into a live world and must run on the thread owning the target chunks - the region thread on regionized
 * platforms, the server thread elsewhere. They are used by the authoring tools that capture vanilla and
 * datapack structures into Iris objects, not by the generation path.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public interface PlatformStructureHooks {
    /**
     * Every registered structure key. Never null.
     */
    List<String> structureKeys();

    /**
     * Registered jigsaw structure keys - the subset of {@link #structureKeys()} assembled from template pools.
     * Empty when the adapter cannot distinguish them.
     */
    default List<String> jigsawStructureKeys() {
        return List.of();
    }

    /**
     * Registered jigsaw template pool keys. Empty when the adapter cannot enumerate them.
     */
    default List<String> templatePoolKeys() {
        return List.of();
    }

    default JigsawSourceMetadata jigsawSourceMetadata(String structureKey) {
        throw new UnsupportedOperationException("The active platform does not expose registered jigsaw metadata");
    }

    default int templatePoolHorizontalSpan(String templatePoolKey) {
        throw new UnsupportedOperationException("The active platform does not expose registered template pool spans");
    }

    default int jigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        return templatePoolHorizontalSpan(templatePoolKey);
    }

    /**
     * Every registered structure-set key, the placement grouping that decides structure spacing. Never null.
     */
    List<String> structureSetKeys();

    /**
     * Biome keys the given structure is allowed to generate in. Empty when the structure is unknown or declares
     * no biome filter. Never null.
     */
    List<String> structureBiomeKeys(String structureKey);

    /**
     * Configured-feature keys that Iris can place as objects - trees, patches and other single-shot features.
     * Never null.
     */
    List<String> objectFeatureKeys();

    /**
     * Structure keys actually reachable in {@code world}, after its dimension's structure sets and biome filter
     * are applied. Narrower than {@link #structureKeys()}. Never null.
     */
    List<String> reachableStructureKeys(NativeWorld world);

    /**
     * Biome keys the world's biome source can emit. Never null.
     */
    List<String> possibleBiomeKeys(NativeWorld world);

    /**
     * Places a configured feature at world coordinates with the given seed. Returns false when the key is
     * unknown or the feature declines to place. Mutates the world; see the threading note on this interface.
     */
    boolean placeFeature(NativeWorld world, int x, int y, int z, String featureKey, long seed);

    /**
     * Generates and places a structure anchored at the given chunk.
     *
     * @param maxSpan reject the placement if the structure's bounding box exceeds this span in blocks
     * @return the placed bounding box as {@code {minX, minY, minZ, maxX, maxY, maxZ}}, or null when the key is
     *         unknown, the structure produced no valid start, or the box exceeded {@code maxSpan}
     */
    int[] placeStructure(NativeWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan);

    /**
     * Whether this adapter implements {@link #placeStructure(NativeWorld, int, int, String, long, int)}.
     * Check before offering structure capture; adapters without the required host access return false.
     */
    boolean supportsStructurePlacement();


}
