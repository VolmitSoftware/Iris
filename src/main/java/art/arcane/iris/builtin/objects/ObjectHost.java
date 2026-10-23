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
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What {@code iris:objects} asks of Iris: the objects of the pack, how Iris scales and places them, and the layout around
 * a world position. Iris's worlds implement it; a module reaches it with {@link #of}. Nothing here hands out the engine.
 */
public interface ObjectHost {
    /** The host of the world {@code settings} are the pack's of, which must be a world Iris generates. */
    static ObjectHost of(PackSettings settings) {
        if (settings instanceof Source source) {
            return source.objects();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + settings);
    }

    /** The host of {@code world}, which must be a world Iris generates. */
    static ObjectHost of(PackWorld world) {
        if (world instanceof Source source) {
            return source.objects();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + world);
    }

    /** Whether Iris's debug setting is on. */
    boolean debugging();

    /** The height, above the dimension's bottom, up to which caves are filled with lava. */
    int caveLavaHeight();

    /** The height of the dimension's fluid. */
    int fluidHeight();

    /** The settings that apply at a world column, for work outside a chunk's generation. */
    FeatureSettings columnSettings();

    /** The random a chunk's objects are placed with: the same for the same chunk of the same world. */
    RNG chunkRandom(int chunkX, int chunkZ);

    /** The chunk whose objects are being placed on this thread. */
    ObjectChunk chunk();

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

    /** The procedural objects {@code settings} declare, made placeable by {@code resolve} once per world. */
    List<ProceduralPlacement> procedural(ProceduralObjectSettings settings,
                                         Function<ProceduralObjectSettings, List<ProceduralPlacement>> resolve);

    /** A builder for one bake of objects made block by block. */
    ObjectBuilder builder();

    /** The object settings of the biome and the region of the dimension stacked above, at the column; none where nothing sits above. */
    Optional<UpperObjects> upperObjects(int worldX, int worldZ);

    /** Places the object into {@code sink}, which is outside any chunk's generation, such as a growing sapling's tree. */
    int placeInto(PlaceableObject object, int x, int y, int z, ObjectPlacement placement, ObjectSink sink, RNG rng);

    /** The region at the column. */
    ResourceId regionAt(int worldX, int worldZ);

    /** The surface biome at the column. */
    ResourceId surfaceBiomeAt(int worldX, int worldZ);

    /** The biome at the position, which is the cave biome underground. */
    ResourceId biomeAt(int worldX, int y, int worldZ);

    /** The settings the biome declares itself, whichever column it is found at; empty when it declares none. */
    <F extends IrisFeature> Optional<F> biomeDeclaration(ResourceId biome, Class<F> type);

    /** The settings the region declares itself; empty when it declares none. */
    <F extends IrisFeature> Optional<F> regionDeclaration(ResourceId region, Class<F> type);

    /** Every region and every reachable biome that declares the settings, by the key the pack knows it by, regions first. */
    <F extends IrisFeature> List<Declaration<F>> declarations(Class<F> type);

    /** What the dimension stacked above declares to place at a column, where each is null when its level declares none. */
    record UpperObjects(ObjectSettings biome, ObjectSettings region, String biomeKey, String regionKey) {
    }

    /** Iris's worlds answer for their objects. */
    interface Source {
        ObjectHost objects();
    }

    /** One region's or biome's declaration of settings. */
    record Declaration<F extends IrisFeature>(Level level, String key, F settings) {
    }
}
