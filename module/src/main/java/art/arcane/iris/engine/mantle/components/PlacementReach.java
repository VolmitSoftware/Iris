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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.PlaceMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.ProceduralVariants;
import art.arcane.iris.modules.objects.ObjectSize;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.resource.PackResources;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** How far, in blocks along X and Z, the objects a pack places may write beyond their chunk. */
final class PlacementReach {
    private static final int LARGE_REACH_WARNING_BLOCKS = 128;
    private static final int MAX_VACUUM_RADIUS_BLOCKS = 128;
    private static final double SCALE_PADDING_FACTOR = 2D;

    private final PackResources resources;
    private final ProceduralObjectCatalog procedural;
    private final ModuleLog log;
    private final Map<String, Optional<ObjectSize>> sizes = new HashMap<>();
    private final Set<String> warnedLargeObjects = new HashSet<>();

    PlacementReach(PackResources resources, ModuleLog log) {
        this.resources = resources;
        this.procedural = ProceduralObjectCatalog.detached(resources, log);
        this.log = log;
    }

    /** The largest reach of any object placement the pack's regions and biomes declare. */
    int ofPack(PackSettings settings) {
        int radius = 0;
        for (Entry<ObjectSettings> entry : settings.declared(ObjectSettings.class)) {
            radius = Math.max(radius, ofPlacements(entry.value().objects()));
        }
        return radius;
    }

    private int ofPlacements(List<ObjectPlacement> placements) {
        int radius = 0;
        for (ObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            for (String objectKey : placement.place()) {
                radius = Math.max(radius, ofObject(placement, objectKey));
            }
        }
        return radius;
    }

    private int ofObject(ObjectPlacement placement, String objectKey) {
        try {
            Optional<ObjectSize> size = sizes.computeIfAbsent(objectKey, key -> sizeOf(resources, procedural, key));
            if (size.isEmpty()) {
                return 0;
            }
            int reach = of(size.get(), placement);
            if (reach > LARGE_REACH_WARNING_BLOCKS && warnedLargeObjects.add(objectKey)) {
                log.warn("Object " + objectKey + " has a large placement reach (" + reach + " blocks) and may increase memory usage!");
            }
            return reach;
        } catch (Throwable failure) {
            log.error("Failed to work out the reach of an object placement", failure);
            return 0;
        }
    }

    /** The size of the object {@code objectKey} names, for a procedural pool its largest variant; empty when the pack has none. */
    static Optional<ObjectSize> sizeOf(PackResources resources, ProceduralObjectCatalog procedural, String objectKey) {
        if (ProceduralObjectCatalog.isProceduralKey(objectKey)) {
            return procedural.variants(objectKey).map(ProceduralVariants::largestSize);
        }
        return resources.objects().sizeOf(resources.idOf(objectKey)).map(PlacementReach::sizeOf);
    }

    private static ObjectSize sizeOf(BlockBox box) {
        return new ObjectSize(box.maxX() - box.minX(), box.maxY() - box.minY(), box.maxZ() - box.minZ());
    }

    static int of(ObjectSize size, ObjectPlacement placement) {
        return of(size, placement.mode().placeMode(), placement.scale().maxScale(), placement.rotation(), placement.rotateTowardsSlope(),
                placement.translate(), placement.warp() == null || placement.warp().isFlat() ? 0 : placement.warp().multiplier(),
                placement.vacuumSettings().orElse(null));
    }

    private static int of(ObjectSize size, PlaceMode mode, Double maxScale, ObjectRotation rotation, boolean rotatesTowardsSlope,
                          ObjectTranslate translate, Double warpMultiplier, VacuumSettings vacuum) {
        double scale = maxScale == null ? 1D : Math.max(1D, maxScale);
        int width = scaledDimension(size.width(), scale);
        int height = scaledDimension(size.height(), scale);
        int depth = scaledDimension(size.depth(), scale);
        boolean rotateX = rotation != null && rotation.enabled() && rotation.xAxis() != null && rotation.xAxis().enabled();
        boolean rotateZ = rotation != null && rotation.enabled() && rotation.zAxis() != null && rotation.zAxis().enabled();
        boolean tiltsVertically = rotatesTowardsSlope || rotateX || rotateZ;
        int footprint = tiltsVertically ? Math.max(width, Math.max(height, depth)) : Math.max(width, depth);
        int translation = translationReach(translate, rotation, tiltsVertically);
        int warp = warpMultiplier == null ? 0 : (int) Math.ceil(Math.abs(warpMultiplier) / 2D);
        int vacuumReach = mode.bendsTerrain() ? 2 * vacuumRadius(mode, vacuum) : 0;
        long reach = (long) footprint + translation + warp + vacuumReach;
        return reach > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reach;
    }

    private static int scaledDimension(int dimension, double scale) {
        int absoluteDimension = Math.abs(dimension);
        if (scale <= 1D) {
            return absoluteDimension;
        }
        return (int) Math.ceil((absoluteDimension * scale) + (scale * SCALE_PADDING_FACTOR));
    }

    private static int translationReach(ObjectTranslate translate, ObjectRotation rotation, boolean rotateVertically) {
        if (translate == null || (translate.x() == 0 && translate.y() == 0 && translate.z() == 0)) {
            return 0;
        }
        double x = translate.x();
        double y = translate.y();
        double z = translate.z();
        if (rotateVertically) {
            return (int) Math.ceil(Math.sqrt((x * x) + (y * y) + (z * z)));
        }
        AxisRotationClamp yAxis = rotation == null ? null : rotation.yAxis();
        boolean rotateY = rotation != null && rotation.enabled() && yAxis != null && yAxis.enabled();
        return rotateY ? (int) Math.ceil(Math.hypot(x, z)) : (int) Math.max(Math.abs(x), Math.abs(z));
    }

    private static int vacuumRadius(PlaceMode mode, VacuumSettings settings) {
        int radius = mode.vacuum().defaultRadiusBlocks();
        if (settings != null && settings.radius() > 0) {
            radius = settings.radius();
        }
        return Math.max(0, Math.min(MAX_VACUUM_RADIUS_BLOCKS, radius));
    }
}
