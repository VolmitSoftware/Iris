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

package art.arcane.iris.builtin;

import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.loot.ObjectVanillaLoot;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import com.volmit.IrisFeature;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What Iris's own services ask a world that is running, outside any chunk stage: the layout and the objects at a block
 * as they are now, the platform's weather and loot tables, and the data Iris keeps with the world. A chunk stage reads
 * the layout through its {@link com.volmit.world.TerrainSampler}; a service has no chunk, so it asks here.
 *
 * <p>X and Z are world coordinates; Y is the height above the dimension's bottom, as everywhere in IrisApi.
 * Safe to use from any thread. Iris offers it as an extension of its worlds: {@code world.extension(LiveWorld.class)}.
 */
public interface LiveWorld {
    /** The platform's block Y of height 0, the dimension's bottom: a position's height plus this is its Y in the platform. */
    int bottomY();

    /** The pack biome at the column's surface. */
    ResourceId surfaceBiome(int x, int z);

    /** The pack biome at the block when it lies in a cave biome, else the surface biome. */
    ResourceId caveBiome(int x, int y, int z);

    /** What the surface biome of the column is for: land, sea, shore or cave. */
    BiomeKind surfaceKind(int x, int z);

    /** The terrain's height at the column, before anything was placed on it. */
    double terrainHeight(int x, int z);

    /** The height of the highest block placed in the column, with the terrain and what objects and structures added. */
    int highestBlock(int x, int z, boolean ignoreFluid);

    /**
     * The settings of {@code type} that the dimension, the region or a biome declares for the block, as they are
     * decoded for generation; empty when that level declares none.
     */
    <F extends IrisFeature> Optional<F> settings(Class<F> type, Level level, int x, int y, int z);

    /** The time and weather of the world, or empty when the world has no platform world yet. */
    Optional<Sky> sky();

    /** Whether the world is a studio world. */
    boolean studio();

    /** The keys of the loot tables the platform has, such as {@code minecraft:chests/simple_dungeon}. They do not change while the world runs. */
    Set<String> platformLootTables();

    /** The floors of the caves marked in the chunk, none on a platform that cannot read them. */
    List<BlockPosition> caveFloors(int chunkX, int chunkZ);

    /** The loot the object placed at the block defines, or empty when no object placement covers the block. */
    Optional<ObjectLoots> objectLoots(int x, int y, int z);

    /** The cooldowns of the world's spawners. */
    SpawnCooldowns spawnCooldowns();

    /** Where the layout is asked: the dimension, the region of the column, or one of its biomes. */
    enum Level {
        DIMENSION,
        REGION,
        /** The biome at the column's surface. */
        SURFACE_BIOME,
        /** The biome at the block: a cave biome below the surface, the surface biome above. */
        BIOME,
        /** The cave biome at the block, the surface biome when it has none. */
        CAVE_BIOME
    }

    /** What a biome is for, which decides the spawners that fit it. */
    enum BiomeKind {
        LAND, SEA, SHORE, CAVE
    }

    /** The world's time of day in ticks, whether it is storming and whether it thunders. */
    record Sky(long time, boolean storming, boolean thundering) {
    }

    /**
     * The loot an object placement gives its containers.
     *
     * @param packTables         tables of the pack
     * @param platformTables     tables of the platform
     * @param overridesGlobalLoot whether the placement's tables replace those of the dimension, region and biomes
     */
    record ObjectLoots(List<ObjectLoot> packTables, List<ObjectVanillaLoot> platformTables, boolean overridesGlobalLoot) {
        public ObjectLoots {
            packTables = List.copyOf(packTables);
            platformTables = List.copyOf(platformTables);
        }

        /** Whether the placement names any table. */
        public boolean defined() {
            return !packTables.isEmpty() || !platformTables.isEmpty();
        }
    }
}
