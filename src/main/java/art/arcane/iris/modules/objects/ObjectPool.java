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

import art.arcane.iris.builtin.LegacyStreams;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectScale;
import art.arcane.iris.builtin.objects.ScaleInterpolation;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.object.ScaleMethod;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * The pack's objects as object placements pick them: by key, scaled the way a placement scales them, and as many at a
 * column as its density says, drawn from Iris's own streams so existing worlds keep their objects.
 */
public final class ObjectPool {
    private static final double NO_SCALE = 1D;
    private static final int MAX_SCALE_VARIANTS = 32;

    private final PackResources resources;
    private final LegacyStreams streams;
    private final ProceduralObjectCatalog procedural;

    private ObjectPool(PackResources resources, LegacyStreams streams, ProceduralObjectCatalog procedural) {
        this.resources = resources;
        this.streams = streams;
        this.procedural = procedural;
    }

    /** The objects of {@code world}'s pack, its procedural objects among them. */
    public static ObjectPool of(PackWorld world) {
        return new ObjectPool(world.resources(), world.extension(LegacyStreams.class).orElseThrow(), ProceduralObjectCatalog.of(world));
    }

    /** The random a chunk's objects are placed with. */
    public RNG chunkRandom(int chunkX, int chunkZ) {
        return streams.objectChunkRandom(chunkX, chunkZ);
    }

    /** The pack object {@code key} names; for a procedural pool, the variant {@code rng} picks. */
    public Optional<PlaceableObject> object(String key, RNG rng) {
        if (ProceduralObjectCatalog.isProceduralKey(key)) {
            return procedural.object(key, rng);
        }
        return objects().object(resources.idOf(key));
    }

    /** An object of the placement, picked with {@code rng} and scaled the way the placement scales it; empty when it has none. */
    public Optional<PlaceableObject> pickScaled(ObjectPlacement placement, RNG rng) {
        List<String> keys = placement.place();
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        return object(keys.get(rng.nextInt(keys.size())), rng).map(origin -> scaled(origin, placement.scale(), rng));
    }

    /** {@code origin} shrunk or grown by exactly {@code factor}, without filling between its blocks. */
    public PlaceableObject resized(PlaceableObject origin, double factor) {
        return factor == NO_SCALE ? origin : objects().scaled(origin, factor, ScaleMethod.NONE);
    }

    /** How many objects the placement places at the column, when its chance check passes. */
    public int density(ObjectPlacement placement, RNG rng, double x, double z) {
        return streams.density(placement, rng, x, z);
    }

    /** Builds the noise the placement draws its density from and bakes its procedural objects, so the first chunk to place it does not wait. */
    public void warm(ObjectPlacement placement, RNG rng) {
        streams.density(placement, rng, 0, 0);
        placement.place().stream().filter(ProceduralObjectCatalog::isProceduralKey).forEach(procedural::placeable);
    }

    private PlaceableObject scaled(PlaceableObject origin, ObjectScale scale, RNG rng) {
        if (!shouldScale(scale)) {
            return origin;
        }
        int variants = variantCount(scale);
        int variant = variants == 1 ? 0 : rng.nextInt(variants);
        return objects().scaled(origin, scaleAt(scale, variant, variants), methodOf(scale.interpolation()));
    }

    private PackObjects objects() {
        return resources.objects();
    }

    private static boolean shouldScale(ObjectScale scale) {
        if (scale.size() != NO_SCALE) {
            return true;
        }
        return scale.variations() > 0 && (scale.minimumScale() != NO_SCALE || scale.maximumScale() != NO_SCALE);
    }

    /** How many evenly spread factors the scale picks from. */
    private static int variantCount(ObjectScale scale) {
        if (scale.size() != NO_SCALE || scale.minimumScale() == scale.maximumScale()) {
            return 1;
        }
        return Math.max(1, Math.min(scale.variations(), MAX_SCALE_VARIANTS));
    }

    private static double scaleAt(ObjectScale scale, int variant, int variants) {
        if (scale.size() != NO_SCALE) {
            return scale.size();
        }
        if (scale.minimumScale() == scale.maximumScale()) {
            return scale.minimumScale();
        }
        return scale.minimumScale() + (((scale.maximumScale() - scale.minimumScale()) / variants) * variant);
    }

    private static ScaleMethod methodOf(ScaleInterpolation interpolation) {
        return switch (interpolation) {
            case NONE -> ScaleMethod.NONE;
            case TRILINEAR -> ScaleMethod.TRILINEAR;
            case TRICUBIC -> ScaleMethod.TRICUBIC;
            case TRIHERMITE -> ScaleMethod.TRIHERMITE;
        };
    }
}
