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

import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.loot.ObjectVanillaLoot;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.config.Level;
import com.volmit.generation.InvocationContext;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/**
 * What {@code iris:objects} asks of Iris's object system: the pack's objects, how Iris scales, picks and places them, and
 * the loot of what it placed. The layout, settings and seeds around an object are the API's. Iris offers it as an
 * extension of its worlds and of the settings of the pack it loads: {@code world.extension(ObjectHost.class)},
 * {@code settings.extension(ObjectHost.class)}.
 *
 * <p>Objects are named by the keys placements hold ({@link ObjectPlacement#place()}). The randoms are VolmLib's
 * {@link RNG}, since the streams Iris draws objects with decide where existing worlds have them.
 */
public interface ObjectHost {
    /** Whether Iris's debug setting is on, which makes object placing trace what it does. */
    boolean debugging();

    /** The random a chunk's objects are placed with: the same for the same chunk of the same world. */
    RNG chunkRandom(int chunkX, int chunkZ);

    /** The chunk whose objects the mantle invocation {@code context} places. */
    ObjectChunk chunk(InvocationContext context);

    /** The object of the pack that {@code key} names. */
    Optional<PlaceableObject> object(String key);

    /** An object of the placement, picked with {@code rng} and scaled the way the placement scales it; empty when it has none. */
    Optional<PlaceableObject> pickScaled(ObjectPlacement placement, RNG rng);

    /** How many objects the placement places at the column, when its chance check passes. */
    int density(ObjectPlacement placement, RNG rng, double x, double z);

    /** The size of the pack's object, read without loading its blocks; empty when the pack has no such object. */
    Optional<ObjectSize> sizeOf(String objectKey);

    /** Builds what placing the placement would build on first use, so the first chunk to place it does not wait. */
    void warm(ObjectPlacement placement, RNG rng);

    /** A builder for one bake of objects made block by block. */
    ObjectBuilder builder();

    /** Places the object into {@code sink}, which is outside any chunk's generation, such as a growing sapling's tree. */
    int placeInto(PlaceableObject object, int x, int y, int z, ObjectPlacement placement, ObjectSink sink, RNG rng);

    /** The loot of the object placed at the block; empty when no object placement covers the block. */
    Optional<ObjectLoots> placedLoot(int x, int y, int z);

    /**
     * Every region and every reachable biome that declares the settings, regions first. Unlike the sources of
     * {@link com.volmit.config.PackSettings#declared}, which name the resource that declares them, each is named by the
     * id a {@link com.volmit.world.BiomeSample} gives it.
     */
    <F extends IrisFeature> List<Declaration<F>> declarations(Class<F> type);

    /** One region's or biome's declaration of settings, by the id of the region or biome. */
    record Declaration<F extends IrisFeature>(Level level, ResourceId id, F settings) {
    }

    /**
     * The loot an object placement gives its containers.
     *
     * @param packTables          tables of the pack
     * @param platformTables      tables of the platform
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
