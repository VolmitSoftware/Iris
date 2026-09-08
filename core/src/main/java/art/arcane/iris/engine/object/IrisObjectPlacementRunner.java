/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.framework.placer.HeightmapObjectPlacer;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.SimplexNoise;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;

/**
 * One-shot placement of an {@link IrisObject} into the world. Instances are cheap and single use: they exist so
 * the placement pass can hoist its per-placement invariants without leaking that state onto the loader-cached
 * (and thread-shared) object itself.
 */
final class IrisObjectPlacementRunner {
    private static final long IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS = 5000L;
    private static final long VACUUM_WAVE_SEED = 7392113L;
    private static final ConcurrentHashMap<String, Long> IMPLAUSIBLE_BEDROCK_WARNS = new ConcurrentHashMap<>();

    private final IrisObject self;

    IrisObjectPlacementRunner(IrisObject self) {
        this.self = self;
    }

    int place(int x, int yv, int z, IObjectPlacer oplacer, IrisObjectPlacement config, RNG rng, BiConsumer<BlockPosition, PlatformBlockState> listener, CarveResult c, IrisData rdata) {
        Objects.requireNonNull(oplacer, "Object placer is required.");
        Objects.requireNonNull(config, "Object placement config is required.");
        Objects.requireNonNull(rng, "Object placement RNG is required.");
        Objects.requireNonNull(rdata, "Object placement data is required.");
        IObjectPlacer placer = config.getHeightmap() != null ? new HeightmapObjectPlacer(rng, x, yv, z, config, oplacer) : oplacer;

        boolean evaluateSlopeCondition = !config.isForcePlace() && !config.getSlopeCondition().isDefault();
        if (evaluateSlopeCondition || config.isRotateTowardsSlope()) {
            Engine placementEngine = requireSlopeEngine(placer);
            if (evaluateSlopeCondition &&
                    !config.getSlopeCondition().isValid(placementEngine.getComplex().getSlopeStream().get(x, z))) {
                return -1;
            }

            if (config.isRotateTowardsSlope()) {
                int slopeRotationY = 0;
                ProceduralStream<Double> heightStream = placementEngine.getComplex().getHeightStream();
                // Whichever side of the rectangle that bounds the object is lowest is the 'direction' of the slope (simply said).
                double hNorth = heightStream.get(x, z + ((float) self.d) / 2);
                double hEast = heightStream.get(x + ((float) self.w) / 2, z);
                double hSouth = heightStream.get(x, z - ((float) self.d) / 2);
                double hWest = heightStream.get(x - ((float) self.w) / 2, z);
                double min = Math.min(Math.min(hNorth, hEast), Math.min(hSouth, hWest));
                if (min == hNorth) {
                    slopeRotationY = 0;
                } else if (min == hEast) {
                    slopeRotationY = 90;
                } else if (min == hSouth) {
                    slopeRotationY = 180;
                } else if (min == hWest) {
                    slopeRotationY = 270;
                }

                double newRotation = config.getRotation().getYAxis().getMin() + slopeRotationY;
                IrisObjectRotation originalRotation = config.getRotation();
                IrisObjectRotation slopeRotation = new IrisObjectRotation();
                slopeRotation.setXAxis(originalRotation.getXAxis());
                slopeRotation.setZAxis(originalRotation.getZAxis());
                if (newRotation == 0) {
                    slopeRotation.setYAxis(new IrisAxisRotationClamp(false, false, 0, 0, 90));
                    slopeRotation.setEnabled(originalRotation.canRotateX() || originalRotation.canRotateZ());
                } else {
                    slopeRotation.setYAxis(new IrisAxisRotationClamp(true, false, newRotation, newRotation, 90));
                    slopeRotation.setEnabled(true);
                }
                config = config.toPlacement(config.getPlace().toArray(new String[0]));
                config.setRotation(slopeRotation);
            }
        }

        if (config.isSmartBore()) {
            IrisObjectShaping.ensureSmartBored(self);
        }

        boolean warped = !config.getWarp().isFlat();
        // Placement-invariant hoists off the per-voxel loop: the warp CNG resolution went
        // through a keyed cache probe per block, and the two boolean gates below cut per-block
        // property scans that can never match for this placement.
        CNG surfaceWarp = warped ? config.getSurfaceWarp(rng, self.getLoader()) : null;
        double warpHalf = warped ? config.getWarp().getMultiplier() / 2D : 0D;
        boolean preventingDecay = placer.isPreventingDecay();
        boolean waterlogCandidate = config.isWaterloggable() || config.isUnderwater();
        boolean rawStructurePiece = config.getMode() == ObjectPlaceMode.STRUCTURE_PIECE;
        boolean paint = yv < 0 && config.getMode() == ObjectPlaceMode.PAINT;
        boolean organicFloor = config.getMode() == ObjectPlaceMode.ORGANIC_STILT;
        boolean ceilingHang = config.getMode() == ObjectPlaceMode.CEILING_HANG;
        boolean organic = organicFloor || ceilingHang;
        boolean vacuuming = IrisObjectVacuum.isVacuumMode(config.getMode());
        boolean stilting = (config.getMode().equals(ObjectPlaceMode.STILT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT) ||
                config.getMode() == ObjectPlaceMode.MIN_STILT || config.getMode() == ObjectPlaceMode.FAST_MIN_STILT ||
                config.getMode() == ObjectPlaceMode.CENTER_STILT || config.getMode() == ObjectPlaceMode.ERODE_STILT || organic);
        boolean eroding = config.getMode() == ObjectPlaceMode.ERODE_STILT;
        KMap<Position2, Integer> heightmap = config.getSnow() > 0 ? new KMap<>() : null;
        int spinx = rng.imax() / 1000;
        int spiny = rng.imax() / 1000;
        int spinz = rng.imax() / 1000;
        int rty = config.getRotation().rotate(new IrisBlockVector(0, self.getCenter().getBlockY(), 0), spinx, spiny, spinz).getBlockY();
        int ty = config.getTranslate().translate(new IrisBlockVector(0, self.getCenter().getBlockY(), 0), config.getRotation(), spinx, spiny, spinz).getBlockY();
        // Per-placement invariants. The rotation kernel, the rotated translate offset and the edit-list emptiness
        // are pure functions of (config, spin), all of which are fixed from here down. Recomputing them per block
        // only cost allocations and transcendentals.
        SpinKernel spin = new SpinKernel(config.getRotation(), spinx, spiny, spinz);
        IrisObjectTranslate translate = config.getTranslate();
        boolean translating = translate.canTranslate();
        IrisBlockVector translateOffset = translating
                ? config.getRotation().rotate(new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ()), spinx, spiny, spinz)
                : null;
        boolean hasEdits = !config.getEdit().isEmpty();
        int y = -1;
        int xx, zz;
        int yrand = config.getTranslate().getYRandom();
        yrand = yrand > 0 ? rng.i(0, yrand) : yrand < 0 ? rng.i(yrand, 0) : yrand;
        int warpMargin = warped ? (int) Math.ceil(Math.abs(config.getWarp().getMultiplier()) / 2D) : 0;
        TransformedBounds placementBounds = transformedBounds(spin, translating, translateOffset, ceilingHang, warpMargin);
        boolean bail = false;

        if (config.isFromBottom()) {
            // todo Convert this to a dedicated mode.
            y = (self.getH() + 1) + rty;
            if (!config.isForcePlace()) {
                if (shouldBailForCarvingAnchor(placer, config, x, y, z)) {
                    bail = true;
                }
            }
        } else  if (yv < 0) {
            if (config.getMode().equals(ObjectPlaceMode.CENTER_HEIGHT) || config.getMode() == ObjectPlaceMode.CENTER_STILT
                    || organic || vacuuming) {
                y = (c != null ? c.getSurface() : placer.getHighest(x, z, self.getLoader(), config.isUnderwater())) + rty;
                if (!config.isForcePlace()) {
                    if (shouldBailForCarvingAnchor(placer, config, x, y, z)) {
                        bail = true;
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.STILT)) {
                int minX = x + placementBounds.minX();
                int maxX = x + placementBounds.maxX();
                int minZ = z + placementBounds.minZ();
                int maxZ = z + placementBounds.maxZ();
                for (int i = minX; i <= maxX; i++) {
                    for (int ii = minZ; ii <= maxZ; ii++) {
                        int h = placer.getHighest(i, ii, self.getLoader(), config.isUnderwater()) + rty;
                        if (!config.isForcePlace()) {
                            if (shouldBailForCarvingAnchor(placer, config, i, h, ii)) {
                                bail = true;
                                break;
                            }
                        }
                        if (h > y)
                            y = h;
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.FAST_MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT)) {
                int minX = x + placementBounds.minX();
                int maxX = x + placementBounds.maxX();
                int minZ = z + placementBounds.minZ();
                int maxZ = z + placementBounds.maxZ();
                int xRadius = Math.max(0, (maxX - minX) / 2);
                int zRadius = Math.max(0, (maxZ - minZ) / 2);

                for (int i = minX; i <= maxX; i += Math.abs(xRadius) + 1) {
                    for (int ii = minZ; ii <= maxZ; ii += Math.abs(zRadius) + 1) {
                        int h = placer.getHighest(i, ii, self.getLoader(), config.isUnderwater()) + rty;
                        if (!config.isForcePlace()) {
                            if (shouldBailForCarvingAnchor(placer, config, i, h, ii)) {
                                bail = true;
                                break;
                            }
                        }
                        if (h > y)
                            y = h;
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.MIN_HEIGHT) || config.getMode() == ObjectPlaceMode.MIN_STILT) {
                y = requireDataEngine(rdata, "minimum-height mode").getHeight() + 1;
                int minX = x + placementBounds.minX();
                int maxX = x + placementBounds.maxX();
                int minZ = z + placementBounds.minZ();
                int maxZ = z + placementBounds.maxZ();
                for (int i = minX; i <= maxX; i++) {
                    for (int ii = minZ; ii <= maxZ; ii++) {
                        int h = placer.getHighest(i, ii, self.getLoader(), config.isUnderwater()) + rty;
                        if (!config.isForcePlace()) {
                            if (shouldBailForCarvingAnchor(placer, config, i, h, ii)) {
                                bail = true;
                                break;
                            }
                        }
                        if (h < y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.FAST_MIN_HEIGHT) || config.getMode() == ObjectPlaceMode.FAST_MIN_STILT) {
                y = requireDataEngine(rdata, "fast minimum-height mode").getHeight() + 1;
                int minX = x + placementBounds.minX();
                int maxX = x + placementBounds.maxX();
                int minZ = z + placementBounds.minZ();
                int maxZ = z + placementBounds.maxZ();
                int xRadius = Math.max(0, (maxX - minX) / 2);
                int zRadius = Math.max(0, (maxZ - minZ) / 2);

                for (int i = minX; i <= maxX; i += Math.abs(xRadius) + 1) {
                    for (int ii = minZ; ii <= maxZ; ii += Math.abs(zRadius) + 1) {
                        int h = placer.getHighest(i, ii, self.getLoader(), config.isUnderwater()) + rty;
                        if (!config.isForcePlace()) {
                            if (shouldBailForCarvingAnchor(placer, config, i, h, ii)) {
                                bail = true;
                                break;
                            }
                        }
                        if (h < y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.PAINT)) {
                int anchorX = x + (translating ? (int) Math.round(translateOffset.getX()) : 0);
                int anchorZ = z + (translating ? (int) Math.round(translateOffset.getZ()) : 0);
                y = (c != null ? c.getSurface()
                        : placer.getHighest(anchorX, anchorZ, self.getLoader(), config.isUnderwater())) + rty;
                if (!config.isForcePlace()) {
                    if (shouldBailForCarvingAnchor(placer, config, x, y, z)) {
                        bail = true;
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.FLOATING)) {
                y = rty;
            }
        } else {
            y = yv;
            if (!config.isForcePlace() && !rawStructurePiece) {
                if (shouldBailForCarvingAnchor(placer, config, x, y, z)) {
                    bail = true;
                }
            }
        }

        if (yv >= 0 && config.isBottom() && !rawStructurePiece) {
            y += Math.floorDiv(self.h, 2);
            CarvingMode carvingMode = config.getCarvingSupport();
            if (!config.isForcePlace() && !carvingMode.equals(CarvingMode.CARVING_ONLY)) {
                if (shouldBailForCarvingAnchor(placer, config, x, y, z)) {
                    bail = true;
                }
            }
        }

        if (yv < 0
                && !config.isForcePlace()
                && !config.isFromBottom()
                && config.getMode() != ObjectPlaceMode.FLOATING
                && !rawStructurePiece
                && config.getCarvingSupport().supportsSurface()
                && placer.getEngine() != null
                && placer.getEngine().getDimension().isBedrock()
                && y <= 1) {
            warnImplausibleBedrockPlacement(placer, config, x, y, z);
            return -1;
        }

        if (bail && !config.isForcePlace()) {
            return -1;
        }

        IrisPaintSurfaceProjection paintProjection = null;
        KList<IrisBlockVector> paintSupport = null;
        if (paint) {
            int buffer = config.isRequireSurfaceSupport() ? Math.max(0, config.getSurfaceSupportBuffer()) : 0;
            int anchorX = x + (translating ? (int) Math.round(translateOffset.getX()) : 0);
            int anchorZ = z + (translating ? (int) Math.round(translateOffset.getZ()) : 0);
            paintProjection = IrisPaintSurfaceProjection.create(placer, new IrisPaintSurfaceProjection.Request(
                    self.getLoader(), x + placementBounds.minX() - buffer, x + placementBounds.maxX() + buffer,
                    z + placementBounds.minZ() - buffer, z + placementBounds.maxZ() + buffer,
                    anchorX, y - rty, anchorZ, config.isUnderwater(), config.getHeightmap() != null));
            if (paintProjection == null) {
                return -1;
            }
            paintSupport = transformedPaintSupport(spin, translateOffset, surfaceWarp, warpHalf, x, y + yrand, z);
        }

        if (yv < 0
                && config.getCarvingSupport().supportsSurface()
                && config.getMode() != ObjectPlaceMode.FLOATING
                && !rawStructurePiece
                && (paint ? IrisSurfaceSupport.intersectsHydrology(oplacer, x, z, null, null,
                        0, 0, 0, paintSupport)
                        : IrisSurfaceSupport.intersectsHydrology(oplacer, x, z, config.getTranslate(),
                        config.getRotation(), spinx, spiny, spinz, self.getSurfaceSupportOffsets()))) {
            return -1;
        }

        // Surface-anchored placements may never roof or bridge a carved hole. Explicit-Y anchors are only
        // guarded for SURFACE_ONLY: ANYWHERE covers the inverted upper dimension and CARVING_ONLY covers
        // cave anchors, and neither reads the terrain surface this stencil samples.
        boolean surfaceAnchored = yv < 0
                ? config.getCarvingSupport().supportsSurface()
                : config.getCarvingSupport() == CarvingMode.SURFACE_ONLY;
        if (surfaceAnchored
                && !config.isForcePlace()
                && !config.isFromBottom()
                && config.getMode() != ObjectPlaceMode.FLOATING
                && !rawStructurePiece
                && !config.isUnderwater()
                && !config.isOnwater()
                && config.isRequireSurfaceSupport()
                && (paint ? IrisSurfaceSupport.isUnsupported(oplacer, self.getLoader(), x, z, null, null,
                        0, 0, 0, paintSupport, config.getSurfaceSupportBuffer(), config.getSurfaceSupportDepth(),
                        paintProjection)
                        : IrisSurfaceSupport.isUnsupported(oplacer, self.getLoader(), x, z, config.getTranslate(),
                        config.getRotation(), spinx, spiny, spinz, self.getSurfaceSupportOffsets(),
                        config.getSurfaceSupportBuffer(), config.getSurfaceSupportDepth()))) {
            return -1;
        }

        if (yv < 0 && !config.getMode().equals(ObjectPlaceMode.FLOATING) && !rawStructurePiece) {
            if (!config.isForcePlace() && !config.isUnderwater() && !config.isOnwater() && placer.isUnderwater(x, z)) {
                return -1;
            }
        }

        if (!config.isForcePlace() && !rawStructurePiece && c != null && Math.max(0, self.h + yrand + ty) + 1 >= c.getHeight()) {
            return -1;
        }

        if (!config.isForcePlace() && !rawStructurePiece && config.isUnderwater()
                && y + rty + ty >= placer.getFluidHeight(x, z)) {
            return -1;
        }

        if (!config.isForcePlace() && !rawStructurePiece && !config.getClamp().canPlace(y + rty + ty, y - rty + ty)) {
            return -1;
        }

        if (!rawStructurePiece && nativeStructureVetoes(placer, config, spin, placementBounds, translating, translateOffset, ceilingHang,
                paintProjection, surfaceWarp, warpHalf, yrand, warpMargin, x, y + yrand, z)) {
            return -1;
        }

        WorldBounds worldBounds = null;
        if (config.isBore() || (!config.isForcePlace() && !rawStructurePiece
                && (!config.getAllowedCollisions().isEmpty() || !config.getForbiddenCollisions().isEmpty()))) {
            worldBounds = resolveWorldBounds(placementBounds, paintProjection, yrand, x, y + yrand, z);
        }

        if (!config.isForcePlace() && !rawStructurePiece && (!config.getAllowedCollisions().isEmpty() || !config.getForbiddenCollisions().isEmpty())) {
            Engine engine = requireDataEngine(rdata, "collision settings");
            for (int i = worldBounds.minX(); i <= worldBounds.maxX(); i++) {
                for (int k = worldBounds.minZ(); k <= worldBounds.maxZ(); k++) {
                    int surface = paint ? paintProjection.surfaceY(i, k) : 0;
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    int minimumY = paint ? surface + placementBounds.minY() + Math.floorDiv(self.h, 2) + yrand : worldBounds.minY();
                    int maximumY = paint ? surface + placementBounds.maxY() + Math.floorDiv(self.h, 2) + yrand : worldBounds.maxY();
                    for (int j = minimumY; j <= maximumY; j++) {
                        String placementMarker = placer.getData(i, j, k, String.class);
                        PlacedObject p = engine.resolveObjectPlacementMarker(i, k, placementMarker);
                        if (p == null) continue;
                        IrisObject o = p.getObject();
                        if (o == null) continue;
                        String key = o.getLoadKey();
                        if (key != null) {
                            if (config.getForbiddenCollisions().contains(key) && !config.getAllowedCollisions().contains(key)) {
                                return -1;
                            }
                        }
                    }
                }
            }
        }

        y += yrand;

        if (config.isBore()) {
            for (int i = worldBounds.minX(); i <= worldBounds.maxX(); i++) {
                for (int k = worldBounds.minZ(); k <= worldBounds.maxZ(); k++) {
                    int surface = paint ? paintProjection.surfaceY(i, k) : 0;
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    int minimumY = paint ? surface + placementBounds.minY() + Math.floorDiv(self.h, 2) + yrand : worldBounds.minY();
                    int maximumY = paint ? surface + placementBounds.maxY() + Math.floorDiv(self.h, 2) + yrand : worldBounds.maxY();
                    for (int j = minimumY - config.getBoreExtendMinY(); j <= maximumY + config.getBoreExtendMaxY(); j++) {
                        placer.set(i, j, k, IrisObject.States.air());
                    }
                }
            }
        }

        int lowest = Integer.MAX_VALUE;
        int topLayer = Integer.MIN_VALUE;
        int vacuumLowest = Integer.MAX_VALUE;
        int vacuumHighest = Integer.MIN_VALUE;
        self.readLock.lock();

        KMap<IrisBlockVector, String> markers = null;

        try {
            VectorMap<PlatformBlockState> blocks = self.blocks;
            VectorMap<TileData> states = self.states;
            // Zero-tile objects are the common case: skip the two Key allocations VectorMap#get needs to prove null.
            boolean hasStates = !states.isEmpty();

            if (config.getMarkers().isNotEmpty() && placer.getEngine() != null) {
                markers = new KMap<>();
                KList<IrisBlockVector> list = StreamSupport.stream(blocks.keys().spliterator(), false)
                        .collect(KList.collector());
                // Marker selection persists into the mantle, so it must be seed-deterministic.
                // Derive a side stream keyed on the placement position instead of consuming the
                // caller's rng: consuming draws there would shift every later placement in the
                // chunk and invalidate existing worlds/goldenhashes.
                RNG markerRng = rng.nextParallelRNG(((((long) x) << 32) | (z & 0xFFFFFFFFL)) * 31L + yv);

                int markerIndex = 0;
                for (IrisObjectMarker j : config.getMarkers()) {
                    IrisMarker marker = self.getLoader().getMarkerLoader().load(j.getMarker());
                    int markerSalt = markerIndex++;

                    if (marker == null || marker.isCompatExcluded()) {
                        continue;
                    }

                    int max = j.getMaximumMarkers();
                    for (IrisBlockVector i : list.shuffleCopy(markerRng.nextParallelRNG(markerSalt))) {
                        if (max <= 0) {
                            break;
                        }

                        PlatformBlockState data = blocks.get(i);
                        if (data == null) {
                            continue;
                        }

                        for (PlatformBlockState k : j.getMark(rdata)) {
                            if (max <= 0) {
                                break;
                            }

                            if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                                boolean a = !blocks.containsKey((IrisBlockVector) i.clone().add(new IrisBlockVector(0, 1, 0)));
                                boolean fff = !blocks.containsKey((IrisBlockVector) i.clone().add(new IrisBlockVector(0, 2, 0)));

                                if (!marker.isEmptyAbove() || (a && fff)) {
                                    markers.put(i, j.getMarker());
                                    max--;
                                }
                            }
                        }
                    }
                }
            }

            VectorMap<PlatformBlockState>.Cursor cursor = blocks.cursor();
            while (cursor.next()) {
                IrisBlockVector g = cursor.key();
                PlatformBlockState d;
                TileData tile = null;

                try {
                    d = cursor.value();
                    if (hasStates) {
                        tile = states.get(g);
                    }
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (cme)");
                    d = IrisObject.States.air();
                }

                if (d == null) {
                    IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (null)");
                    d = IrisObject.States.air();
                }

                if (placer.isDebugSmartBore() && IrisObject.States.vair().equals(d)) {
                    d = IrisObject.States.vairDebug();
                }

                PlatformBlockState data = d;
                IrisBlockVector i = g.clone();
                spin.rotate(i);
                if (ceilingHang) {
                    i.setY(-i.getBlockY());
                }
                if (translating) {
                    i.add(translateOffset);
                }

                if (stilting && IrisObjectShaping.shouldStilt(data)) {
                    if (i.getBlockY() < lowest) {
                        lowest = i.getBlockY();
                    }
                    if (i.getBlockY() > topLayer) {
                        topLayer = i.getBlockY();
                    }
                }

                if (preventingDecay && IrisProceduralBlocks.hasProperty(data, "distance") && "false".equals(IrisProceduralBlocks.propertyValue(data, "persistent"))) {
                    data = data.withProperty("persistent", "true");
                }

                if (hasEdits) {
                    for (IrisObjectReplace j : config.getEdit()) {
                        if (rng.chance(j.getChance())) {
                            for (PlatformBlockState k : j.getFind(rdata)) {
                                if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                                    PlatformBlockState newData = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z, rdata);

                                    boolean sameMaterial = IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(data));
                                    if (sameMaterial && !(newData.isCustom() || data.isCustom()))
                                        data = BlockDataMergeSupport.merge(data, newData);
                                    else
                                        data = newData;

                                    Optional<TileData> t = j.getReplace().getTile(rng, x, y, z, rdata);
                                    if (t.isPresent()) {
                                        tile = t.get();
                                    } else if (!sameMaterial) {
                                        tile = null;
                                    }
                                }
                            }
                        }
                    }
                }

                data = config.getRotation().rotate(data, spinx, spiny, spinz);
                if (data == null) {
                    continue;
                }
                xx = x + (int) Math.round(i.getX());

                int yy = y + (int) Math.round(i.getY());
                zz = z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + x, i.getY() + y, i.getZ() + z);
                    zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + z, i.getY() + y, i.getX() + x);
                }

                if (paint) {
                    int surface = paintProjection.surfaceY(xx, zz);
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    if (!B.isVineBlock(data)) {
                        yy = (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2) + surface + yrand;
                    }
                }

                if (config.isMeld() && !rawStructurePiece && !placer.isSolid(xx, yy, zz)) {
                    continue;
                }

                if (waterlogCandidate && IrisProceduralBlocks.hasProperty(data, "waterlogged") && shouldAutoWaterlogBlock(placer, config, yv, xx, yy, zz)) {
                    data = data.withProperty("waterlogged", "true");
                }

                if (!rawStructurePiece && B.isVineBlock(data)) {
                    data = attachVineFaces(placer, data, xx, yy, zz);
                }

                // Short-circuit order matters for cost only: the mantle read is paid solely
                // for vine blocks. Both operands are pure, so the value is unchanged.
                boolean wouldReplace = !rawStructurePiece && B.isVineBlock(data) && B.isSolid(placer.get(xx, yy, zz));
                String material = IrisObjectShaping.materialKey(data);
                boolean air = material.equals("minecraft:air") || material.equals("minecraft:cave_air");
                boolean place = shouldPlaceObjectBlock(rawStructurePiece, air, wouldReplace);

                if (data.isCustom() || place) {
                    placer.set(xx, yy, zz, data);
                    if (heightmap != null) {
                        Position2 pos = new Position2(xx, zz);
                        Integer currentHeight = heightmap.get(pos);
                        if (currentHeight == null || currentHeight < yy) {
                            heightmap.put(pos, yy);
                        }
                    }
                    if (tile != null) {
                        placer.setTile(xx, yy, zz, tile);
                    }
                    if (markers != null && markers.containsKey(g)) {
                        placer.setData(xx, yy, zz, new MatterMarker(markers.get(g)));
                    }
                    if (listener != null) {
                        listener.accept(new BlockPosition(xx, yy, zz), data);
                    }
                    if (vacuuming && yy < vacuumLowest) {
                        vacuumLowest = yy;
                    }
                    if (vacuuming && yy > vacuumHighest) {
                        vacuumHighest = yy;
                    }
                }
            }
        } finally {
            self.readLock.unlock();
        }

        if (stilting) {
            self.readLock.lock();
            try {
                VectorMap<PlatformBlockState> blocks = self.blocks;
                IrisStiltSettings settings = config.getStiltSettings();

                double erodeCentroidX = 0;
                double erodeCentroidZ = 0;
                double erodeMaxDist = 1;
                if (eroding) {
                    int centroidCount = 0;
                    VectorMap<PlatformBlockState>.Cursor centroidCursor = blocks.cursor();
                    while (centroidCursor.next()) {
                        IrisBlockVector rot = centroidCursor.key().clone();
                        spin.rotate(rot);
                        if (translating) {
                            rot.add(translateOffset);
                        }
                        if (rot.getBlockY() == lowest) {
                            PlatformBlockState bd = centroidCursor.value();
                            if (bd != null && IrisObjectShaping.shouldStilt(bd)) {
                                erodeCentroidX += rot.getX();
                                erodeCentroidZ += rot.getZ();
                                centroidCount++;
                            }
                        }
                    }
                    if (centroidCount > 0) {
                        erodeCentroidX /= centroidCount;
                        erodeCentroidZ /= centroidCount;
                    }
                    VectorMap<PlatformBlockState>.Cursor spreadCursor = blocks.cursor();
                    while (spreadCursor.next()) {
                        IrisBlockVector rot = spreadCursor.key().clone();
                        spin.rotate(rot);
                        if (translating) {
                            rot.add(translateOffset);
                        }
                        if (rot.getBlockY() == lowest) {
                            PlatformBlockState bd = spreadCursor.value();
                            if (bd != null && IrisObjectShaping.shouldStilt(bd)) {
                                double dx = rot.getX() - erodeCentroidX;
                                double dz = rot.getZ() - erodeCentroidZ;
                                double dist = Math.sqrt(dx * dx + dz * dz);
                                if (dist > erodeMaxDist) {
                                    erodeMaxDist = dist;
                                }
                            }
                        }
                    }
                }

                VectorMap<PlatformBlockState>.Cursor stiltCursor = blocks.cursor();
                while (stiltCursor.next()) {
                    IrisBlockVector g = stiltCursor.key();
                    PlatformBlockState sourceData;
                    try {
                        sourceData = stiltCursor.value();
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                        IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (stilt cme)");
                        sourceData = IrisObject.States.air();
                    }

                    if (sourceData == null) {
                        IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (stilt null)");
                        sourceData = IrisObject.States.air();
                    }

                    if (!IrisObjectShaping.shouldStilt(sourceData)) {
                        continue;
                    }

                    PlatformBlockState d = sourceData;
                    if (settings != null && settings.getPalette() != null) {
                        d = config.getStiltSettings().getPalette().get(rng, x, y, z, rdata);
                    } else {
                        String mat = IrisObjectShaping.materialKey(d);
                        if (mat.equals("minecraft:grass_block") || mat.equals("minecraft:mycelium") || mat.equals("minecraft:podzol") || mat.equals("minecraft:dirt_path")) {
                            d = B.getState("minecraft:dirt");
                        }
                    }

                    IrisBlockVector i = g.clone();
                    spin.rotate(i);
                    if (ceilingHang) {
                        i.setY(-i.getBlockY());
                    }
                    if (translating) {
                        i.add(translateOffset);
                    }
                    d = config.getRotation().rotate(d, spinx, spiny, spinz);

                    int targetLayer = ceilingHang ? topLayer : lowest;
                    if (i.getBlockY() != targetLayer)
                        continue;

                    if (hasEdits) {
                        for (IrisObjectReplace j : config.getEdit()) {
                            if (rng.chance(j.getChance())) {
                                for (PlatformBlockState k : j.getFind(rdata)) {
                                    if (d == null) {
                                        continue;
                                    }
                                    if (j.isExact() ? k.matches(d) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(d))) {
                                        PlatformBlockState newData = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z, rdata);

                                        if (IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(d))) {
                                            d = BlockDataMergeSupport.merge(d, newData);
                                        } else {
                                            d = newData;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (d == null || !d.isOccluding())
                        continue;

                    xx = x + (int) Math.round(i.getX());
                    zz = z + (int) Math.round(i.getZ());

                    if (warped) {
                        xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + x, i.getY() + y, i.getZ() + z);
                        zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + z, i.getY() + y, i.getX() + x);
                    }

                    if (organic) {
                        int startY = targetLayer + y;
                        int maxScan = settings != null ? Math.max(1, settings.getOrganicMaxScan()) : 48;
                        int jitterMax = settings != null ? Math.max(0, settings.getOrganicJitter()) : 3;
                        double scratch = settings != null ? Math.max(0, Math.min(1, settings.getOrganicScratch())) : 0.55;
                        long colHash = ((long) xx * 341873128712L) ^ ((long) zz * 132897987541L);
                        int jitter = jitterMax > 0 ? (int) (Math.abs(colHash) % (jitterMax + 1)) : 0;

                        if (ceilingHang) {
                            int scan = 0;
                            int solidY = startY + 1;
                            while (scan < maxScan && !placer.isSolid(xx, solidY, zz)) {
                                solidY++;
                                scan++;
                            }
                            int topBound = (scan < maxScan ? solidY - 1 : startY + Math.min(maxScan, 8)) - jitter;
                            int total = topBound - startY;
                            for (int j = startY; j <= topBound; j++) {
                                if (scratch > 0 && total > 0) {
                                    double ratio = (double) (j - startY) / total;
                                    if (ratio > (1.0 - scratch)) {
                                        long sh = ((long) xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L);
                                        double skipChance = (ratio - (1.0 - scratch)) / scratch;
                                        if ((Math.abs(sh) % 1000) / 1000.0 < skipChance * 0.7) {
                                            continue;
                                        }
                                    }
                                }
                                placer.set(xx, j, zz, d);
                            }
                        } else {
                            int scan = 0;
                            int solidY = startY - 1;
                            while (scan < maxScan && !placer.isSolid(xx, solidY, zz)) {
                                solidY--;
                                scan++;
                            }
                            int bottomBound = (scan < maxScan ? solidY + 1 : startY - Math.min(maxScan, 8)) + jitter;
                            int total = startY - bottomBound;
                            for (int j = startY; j >= bottomBound; j--) {
                                if (scratch > 0 && total > 0) {
                                    double ratio = (double) (startY - j) / total;
                                    if (ratio > (1.0 - scratch)) {
                                        long sh = ((long) xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L);
                                        double skipChance = (ratio - (1.0 - scratch)) / scratch;
                                        if ((Math.abs(sh) % 1000) / 1000.0 < skipChance * 0.7) {
                                            continue;
                                        }
                                    }
                                }
                                placer.set(xx, j, zz, d);
                            }
                        }
                        continue;
                    }

                    int highest = placer.getHighest(xx, zz, self.getLoader(), true);

                    if (IrisProceduralBlocks.hasProperty(d, "waterlogged") && shouldAutoWaterlogBlock(placer, config, yv, xx, highest, zz)) {
                        d = d.withProperty("waterlogged", "true");
                    }

                    int lowerBound = highest - 1;
                    if (settings != null) {
                        lowerBound -= config.getStiltSettings().getOverStilt() - rng.i(0, config.getStiltSettings().getYRand());
                        if (settings.getYMax() != 0)
                            lowerBound -= Math.min(config.getStiltSettings().getYMax() - (lowest + y - highest), 0);
                    }

                    if (eroding) {
                        double dx = i.getX() - erodeCentroidX;
                        double dz = i.getZ() - erodeCentroidZ;
                        double normalizedDist = Math.sqrt(dx * dx + dz * dz) / erodeMaxDist;
                        normalizedDist = Math.min(normalizedDist, 1.0);
                        int totalDepth = (lowest + y) - lowerBound;
                        int erodeDepth = (int) (totalDepth * Math.pow(1.0 - normalizedDist, 1.5));
                        lowerBound = (lowest + y) - erodeDepth;
                    }

                    for (int j = lowest + y; j > lowerBound; j--) {
                        PlatformBlockState fluidState = placer.get(xx, j, zz);
                        if (B.isFluid(fluidState)) {
                            break;
                        }
                        if (eroding) {
                            int depth = (lowest + y) - j;
                            int totalDepth = (lowest + y) - lowerBound;
                            double depthRatio = totalDepth > 0 ? (double) depth / totalDepth : 0;
                            if (depthRatio > 0.4) {
                                long hash = ((long) (xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L));
                                double skipChance = (depthRatio - 0.4) / 0.6;
                                if ((Math.abs(hash) % 1000) / 1000.0 < skipChance * 0.7) {
                                    continue;
                                }
                            }
                        }

                        if (B.isVineBlock(d)) {
                            d = attachVineFaces(placer, d, xx, j, zz);
                        }
                        placer.set(xx, j, zz, d);
                    }

                }
            } finally {
                self.readLock.unlock();
            }
        }

        if (vacuuming && vacuumLowest != Integer.MAX_VALUE && placer.getEngine() != null) {
            vacuumTerrain(placer, config, x, z, placementBounds.minX(), placementBounds.maxX(),
                    placementBounds.minZ(), placementBounds.maxZ(), vacuumLowest, vacuumHighest);
        }

        if (heightmap != null) {
            RNG rngx = rng.nextParallelRNG(3468854);

            for (Position2 i : heightmap.k()) {
                int vx = i.getX();
                int vy = heightmap.get(i);
                int vz = i.getZ();

                if (config.getSnow() > 0) {
                    int height = rngx.i(0, (int) (config.getSnow() * 7));
                    placer.set(vx, vy + 1, vz, IrisObject.States.snowLayer(Math.max(Math.min(height, 7), 0)));
                }
            }
        }

        return y;
    }

    static boolean shouldPlaceObjectBlock(boolean rawStructurePiece, boolean air, boolean wouldReplace) {
        return !wouldReplace && (rawStructurePiece || !air);
    }

    private KList<IrisBlockVector> transformedPaintSupport(SpinKernel spin, IrisBlockVector translateOffset,
                                                          CNG warp, double half, int x, int y, int z) {
        KList<IrisBlockVector> offsets = new KList<>();
        for (IrisBlockVector source : self.getSurfaceSupportOffsets()) {
            IrisBlockVector offset = source.clone();
            spin.rotate(offset);
            if (translateOffset != null) {
                offset.add(translateOffset);
            }
            int worldX = x + (int) Math.round(offset.getX());
            int worldZ = z + (int) Math.round(offset.getZ());
            if (warp != null) {
                worldX += warp.fitDouble(-half, half, offset.getX() + x, offset.getY() + y, offset.getZ() + z);
                worldZ += warp.fitDouble(-half, half, offset.getZ() + z, offset.getY() + y, offset.getX() + x);
            }
            offsets.add(new IrisBlockVector(worldX - x, 0, worldZ - z));
        }
        return offsets;
    }

    private TransformedBounds transformedBounds(SpinKernel spin, boolean translating, IrisBlockVector translateOffset,
                                                boolean ceilingHang, int margin) {
        int sourceMinX = -self.getCenter().getBlockX();
        int sourceMaxX = self.getW() - self.getCenter().getBlockX() - 1;
        int sourceMinY = -self.getCenter().getBlockY();
        int sourceMaxY = self.getH() - self.getCenter().getBlockY() - 1;
        int sourceMinZ = -self.getCenter().getBlockZ();
        int sourceMaxZ = self.getD() - self.getCenter().getBlockZ() - 1;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int xCorner = 0; xCorner < 2; xCorner++) {
            int sourceX = xCorner == 0 ? sourceMinX : sourceMaxX;
            for (int yCorner = 0; yCorner < 2; yCorner++) {
                int sourceY = yCorner == 0 ? sourceMinY : sourceMaxY;
                for (int zCorner = 0; zCorner < 2; zCorner++) {
                    int sourceZ = zCorner == 0 ? sourceMinZ : sourceMaxZ;
                    IrisBlockVector corner = new IrisBlockVector(sourceX, sourceY, sourceZ);
                    spin.rotate(corner);
                    if (ceilingHang) {
                        corner.setY(-corner.getBlockY());
                    }
                    if (translating) {
                        corner.add(translateOffset);
                    }
                    int transformedX = (int) Math.round(corner.getX());
                    int transformedY = (int) Math.round(corner.getY());
                    int transformedZ = (int) Math.round(corner.getZ());
                    minX = Math.min(minX, transformedX);
                    maxX = Math.max(maxX, transformedX);
                    minY = Math.min(minY, transformedY);
                    maxY = Math.max(maxY, transformedY);
                    minZ = Math.min(minZ, transformedZ);
                    maxZ = Math.max(maxZ, transformedZ);
                }
            }
        }

        return new TransformedBounds(minX - margin, maxX + margin, minY, maxY, minZ - margin, maxZ + margin);
    }

    private WorldBounds resolveWorldBounds(TransformedBounds bounds, IrisPaintSurfaceProjection projection,
                                           int randomY, int x, int y, int z) {
        int minX = x + bounds.minX();
        int maxX = x + bounds.maxX();
        int minZ = z + bounds.minZ();
        int maxZ = z + bounds.maxZ();
        if (projection == null) {
            return new WorldBounds(minX, maxX, y + bounds.minY(), y + bounds.maxY(), minZ, maxZ);
        }

        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (int worldX = minX; worldX <= maxX; worldX++) {
            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
                int surface = projection.surfaceY(worldX, worldZ);
                if (surface == IrisPaintSurfaceProjection.MISSING) {
                    continue;
                }
                minimumSurface = Math.min(minimumSurface, surface);
                maximumSurface = Math.max(maximumSurface, surface);
            }
        }
        int paintOffset = Math.floorDiv(self.h, 2) + randomY;
        return new WorldBounds(minX, maxX, minimumSurface + bounds.minY() + paintOffset,
                maximumSurface + bounds.maxY() + paintOffset, minZ, maxZ);
    }

    private static Engine requireSlopeEngine(IObjectPlacer placer) {
        return requirePlacementEngine(placer, "slope settings");
    }

    private static Engine requirePlacementEngine(IObjectPlacer placer, String feature) {
        Engine engine = placer.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires an active Iris engine for " + feature + ".");
        }
        return engine;
    }

    private static Engine requireDataEngine(IrisData data, String feature) {
        Engine engine = data.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires active Iris data for " + feature + ".");
        }
        return engine;
    }

    /**
     * Objects may never intersect a native structure piece. The rect query is the fast path: no native structures
     * near this placement means no per block work at all. Only when the placement envelope meets a piece does the
     * precise pass run, and the first solid block inside a piece rejects the whole object before any write.
     */
    private boolean nativeStructureVetoes(IObjectPlacer placer, IrisObjectPlacement config, SpinKernel spin, TransformedBounds bounds,
                                          boolean translating, IrisBlockVector translateOffset, boolean ceilingHang,
                                          IrisPaintSurfaceProjection projection, CNG surfaceWarp, double warpHalf,
                                          int randomY, int warpMargin, int x, int y, int z) {
        Engine engine = placer.getEngine();
        if (engine == null) {
            return false;
        }

        int minX = x + bounds.minX();
        int maxX = x + bounds.maxX();
        int minZ = z + bounds.minZ();
        int maxZ = z + bounds.maxZ();
        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(minX, minZ, maxX, maxZ);
        if (volumes == null || volumes.isEmpty()) {
            return false;
        }

        int worldY = y + engine.getMinHeight();
        int envelopeMinY = projection != null ? Integer.MIN_VALUE : worldY + bounds.minY() - warpMargin;
        int envelopeMaxY = projection != null ? Integer.MAX_VALUE : worldY + bounds.maxY() + warpMargin;
        boolean envelopeMeetsPiece = false;
        for (NativeStructureVolume volume : volumes) {
            if (volume.intersects(minX, envelopeMinY, minZ, maxX, envelopeMaxY, maxZ)) {
                envelopeMeetsPiece = true;
                break;
            }
        }

        if (!envelopeMeetsPiece) {
            return false;
        }

        self.readLock.lock();
        try {
            VectorMap<PlatformBlockState>.Cursor cursor = self.blocks.cursor();
            while (cursor.next()) {
                PlatformBlockState state = cursor.value();
                if (state == null || isAirBlock(state)) {
                    continue;
                }

                IrisBlockVector i = cursor.key().clone();
                spin.rotate(i);
                if (ceilingHang) {
                    i.setY(-i.getBlockY());
                }
                if (translating) {
                    i.add(translateOffset);
                }

                int xx = x + (int) Math.round(i.getX());
                int zz = z + (int) Math.round(i.getZ());
                int yy = y + (int) Math.round(i.getY());
                if (projection != null) {
                    if (surfaceWarp != null) {
                        xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + x, i.getY() + y, i.getZ() + z);
                        zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + z, i.getY() + y, i.getX() + x);
                    }
                    int surface = projection.surfaceY(xx, zz);
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    if (!B.isVineBlock(state)) {
                        yy = (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2) + surface + randomY;
                    }
                }
                int worldBlockY = yy + engine.getMinHeight();

                for (NativeStructureVolume volume : volumes) {
                    if (volume.containsWithin(xx, worldBlockY, zz, projection == null ? warpMargin : 0)) {
                        return true;
                    }
                }
            }
        } finally {
            self.readLock.unlock();
        }

        return false;
    }

    private static boolean isAirBlock(PlatformBlockState state) {
        String material = IrisObjectShaping.materialKey(state);
        return material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

    private void warnImplausibleBedrockPlacement(IObjectPlacer placer, IrisObjectPlacement config, int x, int y, int z) {
        String key = self.getLoadKey();
        String fingerprint = (key == null ? "<null>" : key) + "|" + config.getMode();
        long now = System.currentTimeMillis();
        Long last = IMPLAUSIBLE_BEDROCK_WARNS.get(fingerprint);
        if (last != null && now - last < IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS) {
            return;
        }
        IMPLAUSIBLE_BEDROCK_WARNS.put(fingerprint, now);
        IrisLogging.warn("Implausible object placement rejected: "
                + (key == null ? "<no loadKey>" : key)
                + " resolved anchorY=" + y + " at (" + x + "," + z + ") mode=" + config.getMode()
                + " carving=" + config.getCarvingSupport()
                + ". Surface-anchored placement should never land on the bedrock row. "
                + "Height sampling returned a bogus value — not configured for floor placement "
                + "(forcePlace=false, fromBottom=false, mode!=FLOATING). Skipping to protect bedrock.");
    }

    private void vacuumTerrain(IObjectPlacer placer, IrisObjectPlacement config, int centerX, int centerZ, int lowX, int highX, int lowZ, int highZ, int baseY, int topY) {
        ObjectPlaceMode mode = config.getMode();
        IrisVacuumSettings settings = config.getVacuumSettings();
        int radius = IrisObjectVacuum.resolveRadius(mode, settings);
        int step = IrisObjectVacuum.resolveStep(mode);
        double falloff = IrisObjectVacuum.resolveFalloff(settings);
        int jitter = settings != null ? Math.max(0, settings.getOrganicJitter()) : 4;
        boolean organicEdge = mode == ObjectPlaceMode.VACUUM_ORGANIC;
        boolean wavyEdge = mode == ObjectPlaceMode.VACUUM_WAVY;
        double waveAmplitude = IrisObjectVacuum.resolveWaveAmplitude(settings);
        double waveScale = IrisObjectVacuum.resolveWaveScale(settings);
        SimplexNoise waveNoise = (wavyEdge && waveAmplitude > 0) ? new SimplexNoise(VACUUM_WAVE_SEED) : null;
        int meetY = baseY - 1;

        IrisComplex complex = placer.getEngine().getComplex();
        int worldMin = placer.getEngine().getMinHeight();
        int worldMax = worldMin + placer.getEngine().getHeight() - 1;

        for (int dx = lowX - radius; dx <= highX + radius; dx += step) {
            for (int dz = lowZ - radius; dz <= highZ + radius; dz += step) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                double effRadius = radius;
                if (organicEdge && jitter > 0) {
                    long h = ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
                    double n = ((Math.abs(h) % 1000) / 1000.0) - 0.5;
                    effRadius = Math.max(1.0, radius + (n * 2.0 * jitter));
                }
                int origY = placer.getHighest(cx, cz, self.getLoader(), true);
                int targetY = IrisObjectVacuum.columnTargetY(dx, dz, lowX, highX, lowZ, highZ, effRadius, falloff, origY, meetY);
                if (waveNoise != null) {
                    int outX = IrisObjectVacuum.outset(dx, lowX, highX);
                    int outZ = IrisObjectVacuum.outset(dz, lowZ, highZ);
                    double waveDistance = Math.sqrt((double) (outX * outX) + (double) (outZ * outZ));
                    double sample = waveNoise.noiseSigned(cx * waveScale, cz * waveScale);
                    targetY += IrisObjectVacuum.waveOffset(waveDistance, effRadius, sample, waveAmplitude);
                }
                if (targetY == origY) {
                    continue;
                }
                targetY = Math.max(worldMin + 1, Math.min(worldMax, targetY));
                if (targetY > origY) {
                    PlatformBlockState fill = complex != null ? complex.getRockStream().get(cx, cz) : null;
                    if (B.isAir(fill)) {
                        fill = IrisObject.States.stone();
                    }
                    for (int yy = origY + 1; yy <= targetY; yy++) {
                        placer.set(cx, yy, cz, fill);
                    }
                } else if (targetY < origY) {
                    boolean inside = IrisObjectVacuum.outset(dx, lowX, highX) == 0 && IrisObjectVacuum.outset(dz, lowZ, highZ) == 0;
                    int carveFloor = IrisObjectVacuum.carveFloorY(targetY, topY, inside);
                    for (int yy = origY; yy >= carveFloor; yy--) {
                        placer.set(cx, yy, cz, IrisObject.States.air());
                    }
                }
            }
        }
    }

    private boolean shouldBailForCarvingAnchor(IObjectPlacer placer, IrisObjectPlacement placement, int x, int y, int z) {
        CarvingMode carvingMode = placement.getCarvingSupport();
        return switch (carvingMode) {
            case SURFACE_ONLY -> placer.isCarved(x, y, z);
            case CARVING_ONLY -> !isCarvedCaveAnchor(placer, x, y, z);
            case ANYWHERE -> false;
        };
    }

    private boolean isCarvedCaveAnchor(IObjectPlacer placer, int x, int y, int z) {
        return placer.isCarved(x, y, z)
                || placer.isCarved(x, y - 1, z)
                || placer.isCarved(x, y - 2, z)
                || placer.isCarved(x, y - 3, z);
    }

    private boolean shouldAutoWaterlogBlock(IObjectPlacer placer, IrisObjectPlacement placement, int yv, int x, int y, int z) {
        if (!(placement.isWaterloggable() || placement.isUnderwater())) {
            return false;
        }

        if (yv >= 0 && placement.getCarvingSupport().equals(CarvingMode.CARVING_ONLY)) {
            return false;
        }

        PlatformBlockState existing = placer.get(x, y, z);
        if (existing == null) {
            return false;
        }

        return B.isWater(existing) || B.isWaterLogged(existing);
    }

    private static PlatformBlockState attachVineFaces(IObjectPlacer placer, PlatformBlockState data, int x, int y, int z) {
        PlatformBlockState result = data;
        for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
            if (!IrisProceduralBlocks.hasProperty(data, face)) {
                continue;
            }
            int[] mod = IrisProceduralBlocks.faceOffset(face);
            PlatformBlockState facing = placer.get(x + mod[0], y + mod[1], z + mod[2]);
            if (B.isSolid(facing) && !B.isVineBlock(facing)) {
                result = result.withProperty(face, "true");
            }
        }
        return result;
    }

    /**
     * Precomputed vector rotation for a single placement.
     * <p>
     * This mirrors {@link IrisObjectRotation#rotate(IrisBlockVector, int, int, int)} branch for branch, with two
     * differences: it mutates the vector handed to it instead of cloning, and the per-axis angle plus its cosine
     * and sine are resolved once instead of once per block. The angle is a pure function of the axis clamp and the
     * spin, both fixed for the placement, and Math.cos/Math.sin are pure, so every produced double is identical to
     * the value the per-call form would have produced.
     */
    private static final class SpinKernel {
        private static final int MODE_NONE = 0;
        private static final int MODE_FLIP = 1;
        private static final int MODE_QUARTER = 2;
        private static final int MODE_THREE_QUARTER = 3;
        private static final int MODE_ANGLE = 4;

        private final boolean rotates;
        private final int xMode;
        private final int yMode;
        private final int zMode;
        private final double xCos;
        private final double xSin;
        private final double yCos;
        private final double ySin;
        private final double zCos;
        private final double zSin;

        private SpinKernel(IrisObjectRotation rotation, int spinx, int spiny, int spinz) {
            rotates = rotation.canRotate();
            xMode = rotation.canRotateX() ? modeOf(rotation.getXAxis()) : MODE_NONE;
            zMode = rotation.canRotateZ() ? modeOf(rotation.getZAxis()) : MODE_NONE;
            yMode = rotation.canRotateY() ? modeOf(rotation.getYAxis()) : MODE_NONE;

            double xAngle = xMode == MODE_ANGLE ? rotation.getXRotation(spinx) : 0;
            double zAngle = zMode == MODE_ANGLE ? rotation.getZRotation(spinz) : 0;
            double yAngle = yMode == MODE_ANGLE ? rotation.getYRotation(spiny) : 0;
            xCos = Math.cos(xAngle);
            xSin = Math.sin(xAngle);
            zCos = Math.cos(zAngle);
            zSin = Math.sin(zAngle);
            yCos = Math.cos(yAngle);
            ySin = Math.sin(yAngle);
        }

        private static int modeOf(IrisAxisRotationClamp clamp) {
            if (!clamp.isLocked()) {
                return MODE_ANGLE;
            }

            if (Math.abs(clamp.getMax()) % 360D == 180D) {
                return MODE_FLIP;
            }

            if (clamp.getMax() % 360D == 90D || clamp.getMax() % 360D == -270D) {
                return MODE_QUARTER;
            }

            if (clamp.getMax() == -90D || clamp.getMax() % 360D == 270D) {
                return MODE_THREE_QUARTER;
            }

            return MODE_ANGLE;
        }

        /**
         * Rotates in place. Axis order (X, then Z, then Y) is load bearing.
         */
        private void rotate(IrisBlockVector v) {
            if (!rotates) {
                return;
            }

            switch (xMode) {
                case MODE_FLIP -> {
                    v.setZ(-v.getZ());
                    v.setY(-v.getY());
                }
                case MODE_QUARTER -> {
                    double z = v.getZ();
                    v.setZ(v.getY());
                    v.setY(-z);
                }
                case MODE_THREE_QUARTER -> {
                    double z = v.getZ();
                    v.setZ(-v.getY());
                    v.setY(z);
                }
                case MODE_ANGLE -> {
                    double y = xCos * v.getY() - xSin * v.getZ();
                    double z = xSin * v.getY() + xCos * v.getZ();
                    v.setY(y);
                    v.setZ(z);
                }
                default -> {
                }
            }

            switch (zMode) {
                case MODE_FLIP -> {
                    v.setY(-v.getY());
                    v.setX(-v.getX());
                }
                case MODE_QUARTER -> {
                    double y = v.getY();
                    v.setY(v.getX());
                    v.setX(-y);
                }
                case MODE_THREE_QUARTER -> {
                    double y = v.getY();
                    v.setY(-v.getX());
                    v.setX(y);
                }
                case MODE_ANGLE -> {
                    double x = zCos * v.getX() - zSin * v.getY();
                    double y = zSin * v.getX() + zCos * v.getY();
                    v.setX(x);
                    v.setY(y);
                }
                default -> {
                }
            }

            switch (yMode) {
                case MODE_FLIP -> {
                    v.setX(-v.getX());
                    v.setZ(-v.getZ());
                }
                case MODE_QUARTER -> {
                    double x = v.getX();
                    v.setX(v.getZ());
                    v.setZ(-x);
                }
                case MODE_THREE_QUARTER -> {
                    double x = v.getX();
                    v.setX(-v.getZ());
                    v.setZ(x);
                }
                case MODE_ANGLE -> {
                    double x = yCos * v.getX() + ySin * v.getZ();
                    double z = -ySin * v.getX() + yCos * v.getZ();
                    v.setX(x);
                    v.setZ(z);
                }
                default -> {
                }
            }
        }
    }

    private record TransformedBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    private record WorldBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}
