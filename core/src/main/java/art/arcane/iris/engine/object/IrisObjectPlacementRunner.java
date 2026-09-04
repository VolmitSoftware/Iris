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
import art.arcane.iris.engine.object.PlacementContext.TransformedBounds;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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
        ObjectPlacementConditions util = new ObjectPlacementConditions(config, placer, self);

        boolean evaluateSlopeCondition = !config.isForcePlace() && !config.getSlopeCondition().isDefault();
        if (evaluateSlopeCondition || config.isRotateTowardsSlope()) {
            Engine placementEngine = requireSlopeEngine(placer);
            if (evaluateSlopeCondition &&
                    !config.getSlopeCondition().isValid(placementEngine.getComplex().getSlopeStream().get(x, z))) {
                return -1;
            }
            if (config.isRotateTowardsSlope()) {
               withRotateTowardsSlope(config, rdata.getEngine(), x, z);
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
        ObjectSpinKernel spin = new ObjectSpinKernel(config.getRotation(), spinx, spiny, spinz);
        IrisObjectTranslate translate = config.getTranslate();
        boolean translating = translate.canTranslate();
        IrisBlockVector translateOffset = translating
                ? config.getRotation().rotate(new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ()), spinx, spiny, spinz)
                : null;
        boolean hasEdits = !config.getEdit().isEmpty();
        //int y = -1;
        int xx, zz;
        int yrand = config.getTranslate().getYRandom();
        yrand = yrand > 0 ? rng.i(0, yrand) : yrand < 0 ? rng.i(yrand, 0) : yrand;
        int warpMargin = warped ? (int) Math.ceil(Math.abs(config.getWarp().getMultiplier()) / 2D) : 0;
        TransformedBounds placementBounds = util.transformedBounds(spin, translating, translateOffset, ceilingHang, warpMargin);

        PlacementContext context = new PlacementContext(config, rdata, placer, c, rng, spin, self, placementBounds, x, -1, z, yv, rty);

//        if (config.isFromBottom()) {
//            // todo Convert this to a dedicated mode.
//            y = (self.getH() + 1) + rty;
//            if (!config.isForcePlace()) {
//                if (shouldBailForCarvingAnchor(placer, config, context.x, y, context.z)) {
//                    bail = true;
//                }
//            }
//        } else
        PlacementBehavior behavior = config.getMode().createBehavior(context, util);

        if (yv < 0) {
            behavior.setHeight();

        } else {
            context.y = yv;
            if (!config.isForcePlace() && !rawStructurePiece) {
                if (util.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
                    context.bail = true;
                }
            }
        }

        if (yv >= 0 && config.isBottom() && !rawStructurePiece) {
            context.y += Math.floorDiv(self.h, 2);
            CarvingMode carvingMode = config.getCarvingSupport();
            if (!config.isForcePlace() && !carvingMode.equals(CarvingMode.CARVING_ONLY)) {
                if (util.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
                    context.bail = true;
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
                && context.y <= 1) {
            warnImplausibleBedrockPlacement(placer, config, context.x, context.y, context.z);
            return -1;
        }

        if (context.bail && !config.isForcePlace()) {
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
                && IrisSurfaceSupport.isUnsupported(oplacer, self.getLoader(), context.x, context.z, config.getTranslate(),
                        config.getRotation(), spinx, spiny, spinz, self.getSurfaceSupportOffsets(),
                        config.getSurfaceSupportBuffer(), config.getSurfaceSupportDepth())) {
            return -1;
        }

        if (yv < 0 && !config.getMode().equals(ObjectPlaceMode.FLOATING) && !rawStructurePiece) {
            if (!config.isForcePlace() && !config.isUnderwater() && !config.isOnwater() && placer.isUnderwater(context.x, context.z)) {
                return -1;
            }
        }

        if (!config.isForcePlace() && !rawStructurePiece && c != null && Math.max(0, self.h + yrand + ty) + 1 >= c.getHeight()) {
            return -1;
        }

        if (!config.isForcePlace() && !rawStructurePiece && config.isUnderwater() && context.y + rty + ty >= placer.getFluidHeight()) {
            return -1;
        }

        if (!config.isForcePlace() && !rawStructurePiece && !config.getClamp().canPlace(context.y + rty + ty, context.y - rty + ty)) {
            return -1;
        }

        if (!rawStructurePiece && nativeStructureVetoes(placer, config, spin, translating, translateOffset, ceilingHang,
                yv < 0 && config.getMode() == ObjectPlaceMode.PAINT, warpMargin, context.x, context.y + yrand, context.z)) {
            return -1;
        }

        boolean paint = yv < 0 && config.getMode() == ObjectPlaceMode.PAINT;
        ObjectPlacementConditions.WorldBounds worldBounds = null;
        if (config.isBore() || (!config.isForcePlace() && !rawStructurePiece
                && (!config.getAllowedCollisions().isEmpty() || !config.getForbiddenCollisions().isEmpty()))) {
            worldBounds = util.resolveWorldBounds(placementBounds, paint, context.x, context.y + yrand, context.z);
        }

        if (!config.isForcePlace() && !rawStructurePiece && (!config.getAllowedCollisions().isEmpty() || !config.getForbiddenCollisions().isEmpty())) {
            Engine engine = requireDataEngine(rdata, "collision settings");
            for (int i = worldBounds.minX(); i <= worldBounds.maxX(); i++) {
                for (int j = worldBounds.minY(); j <= worldBounds.maxY(); j++) {
                    for (int k = worldBounds.minZ(); k <= worldBounds.maxZ(); k++) {
                        PlacedObject p = engine.getObjectPlacement(i, j, k);
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

        context.y += yrand;

        if (config.isBore()) {
            for (int i = worldBounds.minX(); i <= worldBounds.maxX(); i++) {
                for (int j = worldBounds.minY() - config.getBoreExtendMinY(); j <= worldBounds.maxY() + config.getBoreExtendMaxY(); j++) {
                    for (int k = worldBounds.minZ(); k <= worldBounds.maxZ(); k++) {
                        placer.set(i, j, k, IrisObject.States.AIR);
                    }
                }
            }
        }

        int lowest = Integer.MAX_VALUE;
        int topLayer = Integer.MIN_VALUE;
        int vacuumLowest = Integer.MAX_VALUE;
        int vacuumHighest = Integer.MIN_VALUE;
        self.readLock.lock();

        ObjectBlockPass pass = new ObjectBlockPass(context, behavior);
        ObjectMarkerPlacement markerPlacer = null;

        try {
            VectorMap<PlatformBlockState> blocks = self.blocks;
            VectorMap<TileData> states = self.states;
            // Zero-tile objects are the common case: skip the two Key allocations VectorMap#get needs to prove null.
            boolean hasStates = !states.isEmpty();

            if (config.getMarkers().isNotEmpty() && placer.getEngine() != null) {
                markerPlacer = new ObjectMarkerPlacement(config, context);
                markerPlacer.loadPossibleMarkers();
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
                    d = IrisObject.States.AIR;
                }

                if (d == null) {
                    IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (null)");
                    d = IrisObject.States.AIR;
                }

                if (placer.isDebugSmartBore() && IrisObject.States.VAIR.equals(d)) {
                    d = IrisObject.States.VAIR_DEBUG;
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

                //todo move this under stilt mode
//                if (stilting && IrisObjectShaping.shouldStilt(data)) {
//                    if (i.getBlockY() < lowest) {
//                        lowest = i.getBlockY();
//                    }
//                    if (i.getBlockY() > topLayer) {
//                        topLayer = i.getBlockY();
//                    }
//                }

                if (preventingDecay && IrisProceduralBlocks.hasProperty(data, "distance") && "false".equals(IrisProceduralBlocks.propertyValue(data, "persistent"))) {
                    data = data.withProperty("persistent", "true");
                }

                if (hasEdits) {
                    for (IrisObjectReplace j : config.getEdit()) {
                        if (rng.chance(j.getChance())) {
                            for (PlatformBlockState k : j.getFind(rdata)) {
                                if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                                    PlatformBlockState newData = j.getReplace(rng, i.getX() + context.x, i.getY() + context.y, i.getZ() + context.z, rdata);

                                    if (IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(data)) && !(newData.isCustom() || data.isCustom()))
                                        data = BlockDataMergeSupport.merge(data, newData);
                                    else
                                        data = newData;

                                    Optional<TileData> t = j.getReplace().getTile(rng, context.x, context.y, context.z, rdata);
                                    if (t.isPresent()) {
                                        tile = t.get();
                                    }
                                }
                            }
                        }
                    }
                }

                List<ObjectMarkerPlacement.MarkerPlacement> matchingMarkers = markerPlacer != null ? markerPlacer.matchMarkers(data) : null;

                data = config.getRotation().rotate(data, spinx, spiny, spinz);
                xx = context.x + (int) Math.round(i.getX());

                int yy = context.y + (int) Math.round(i.getY());
                zz = context.z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + context.x, i.getY() + context.y, i.getZ() + context.z);
                    zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + context.z, i.getY() + context.y, i.getX() + context.x);
                }

                if (yv < 0 && (config.getMode().equals(ObjectPlaceMode.PAINT)) && !B.isVineBlock(data)) {
                    yy = (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2) + placer.getHighest(xx, zz, self.getLoader(), config.isUnderwater());
                }

                if (config.isMeld() && !rawStructurePiece && !placer.isSolid(xx, yy, zz)) {
                    continue;
                }

                if (waterlogCandidate && IrisProceduralBlocks.hasProperty(data, "waterlogged") && util.shouldAutoWaterlogBlock(yv, xx, yy, zz)) {
                    data = data.withProperty("waterlogged", "true");
                }

                if (B.isVineBlock(data)) {
                    data = attachVineFaces(placer, data, xx, yy, zz);
                }

                // Short-circuit order matters for cost only: the mantle read is paid solely
                // for vine blocks. Both operands are pure, so the value is unchanged.
                boolean wouldReplace = B.isVineBlock(data) && B.isSolid(placer.get(xx, yy, zz));
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
                    if (matchingMarkers != null) {
                        BlockPosition markerPosition = new BlockPosition(xx, yy, zz);
                        for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : matchingMarkers) {
                            markerPlacement.candidates.add(markerPosition);
                        }
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


            if (markerPlacer != null) {
                markerPlacer.placeMarkers();
            }

        } finally {
            self.readLock.unlock();
        }


        // todo stilt was here
        config.getMode().getBehavior().postHeight();
//        if (stilting) {
//            // todo stilting
//
//        }

        if (vacuuming && vacuumLowest != Integer.MAX_VALUE && placer.getEngine() != null) {
            vacuumTerrain(placer, config, context.x, context.z, placementBounds.minX(), placementBounds.maxX(),
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
                    placer.set(vx, vy + 1, vz, IrisObject.States.SNOW_LAYERS[Math.max(Math.min(height, 7), 0)]);
                }
            }
        }

        return context.y;
    }

    static boolean shouldPlaceObjectBlock(boolean rawStructurePiece, boolean air, boolean wouldReplace) {
        return !wouldReplace && (rawStructurePiece || !air);
    }

    private void withRotateTowardsSlope(IrisObjectPlacement config, Engine engine, int x, int z) {
        int slopeRotationY = 0;
        ProceduralStream<Double> heightStream = engine.getComplex().getHeightStream();
        // Whichever side of the rectangle that bounds the object is lowest is the 'direction' of the slope (simply said).
        double hNorth = heightStream.get(x, z + ((float) self.d) / 2);
        double hEast = heightStream.get(x + ((float) self.w) / 2, z);
        double hSouth = heightStream.get(x, z - ((float) self.d) / 2);
        double hWest = heightStream.get(x - ((float) self.w) / 2, z);
        double min = Math.min(Math.min(hNorth, hEast), Math.min(hSouth, hWest));
        if (mind == hNorth) {
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
    private boolean nativeStructureVetoes(IObjectPlacer placer, IrisObjectPlacement config, ObjectSpinKernel spin,
                                          boolean translating, IrisBlockVector translateOffset, boolean ceilingHang,
                                          boolean paint, int warpMargin, int x, int y, int z) {
        Engine engine = placer.getEngine();
        if (engine == null) {
            return false;
        }

        int margin = (Math.max(self.getW(), Math.max(self.getH(), self.getD())) / 2) + 1 + warpMargin;
        if (translating) {
            margin += Math.max(Math.abs(translateOffset.getBlockX()),
                    Math.max(Math.abs(translateOffset.getBlockY()), Math.abs(translateOffset.getBlockZ())));
        }

        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(x - margin, z - margin, x + margin, z + margin);
        if (volumes == null || volumes.isEmpty()) {
            return false;
        }

        int worldY = y + engine.getMinHeight();
        int envelopeMinY = paint ? Integer.MIN_VALUE : worldY - margin;
        int envelopeMaxY = paint ? Integer.MAX_VALUE : worldY + margin;
        boolean envelopeMeetsPiece = false;
        for (NativeStructureVolume volume : volumes) {
            if (volume.intersects(x - margin, envelopeMinY, z - margin, x + margin, envelopeMaxY, z + margin)) {
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
                int yy = paint
                        ? (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2)
                                + placer.getHighest(xx, zz, self.getLoader(), config.isUnderwater())
                        : y + (int) Math.round(i.getY());
                int worldBlockY = yy + engine.getMinHeight();

                for (NativeStructureVolume volume : volumes) {
                    if (volume.containsWithin(xx, worldBlockY, zz, warpMargin)) {
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
                        fill = IrisObject.States.STONE;
                    }
                    for (int yy = origY + 1; yy <= targetY; yy++) {
                        placer.set(cx, yy, cz, fill);
                    }
                } else if (targetY < origY) {
                    boolean inside = IrisObjectVacuum.outset(dx, lowX, highX) == 0 && IrisObjectVacuum.outset(dz, lowZ, highZ) == 0;
                    int carveFloor = IrisObjectVacuum.carveFloorY(targetY, topY, inside);
                    for (int yy = origY; yy >= carveFloor; yy--) {
                        placer.set(cx, yy, cz, IrisObject.States.AIR);
                    }
                }
            }
        }
    }
}
