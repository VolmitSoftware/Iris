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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.*;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.NoiseType;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ComponentFlag(ReservedFlag.OBJECT)
public class MantleObjectComponent extends IrisMantleComponent {
    private static final long CAVE_REJECT_LOG_THROTTLE_MS = 5000L;
    private static final Map<String, CaveRejectLogState> CAVE_REJECT_LOG_STATE = new ConcurrentHashMap<>();

    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.OBJECT, 1);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        boolean traceRegen = isRegenTraceThread();
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome surfaceBiome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        int surfaceY = getEngineMantle().getEngine().getHeight(xxx, zzz, true);
        IrisBiome caveBiome = resolveCaveObjectBiome(xxx, zzz, surfaceY, surfaceBiome);
        if (traceRegen) {
            Iris.info("Regen object layer start: chunk=" + x + "," + z
                    + " surfaceBiome=" + surfaceBiome.getLoadKey()
                    + " caveBiome=" + caveBiome.getLoadKey()
                    + " region=" + region.getLoadKey()
                    + " biomeSurfacePlacers=" + surfaceBiome.getSurfaceObjects().size()
                    + " biomeCavePlacers=" + caveBiome.getCarvingObjects().size()
                    + " regionSurfacePlacers=" + region.getSurfaceObjects().size()
                    + " regionCavePlacers=" + region.getCarvingObjects().size());
        }
        ObjectPlacementSummary summary = placeObjects(writer, rng, x, z, surfaceBiome, caveBiome, region, traceRegen);
        if (traceRegen) {
            Iris.info("Regen object layer done: chunk=" + x + "," + z
                    + " biomeSurfacePlacersChecked=" + summary.biomeSurfacePlacersChecked()
                    + " biomeSurfacePlacersTriggered=" + summary.biomeSurfacePlacersTriggered()
                    + " biomeCavePlacersChecked=" + summary.biomeCavePlacersChecked()
                    + " biomeCavePlacersTriggered=" + summary.biomeCavePlacersTriggered()
                    + " regionSurfacePlacersChecked=" + summary.regionSurfacePlacersChecked()
                    + " regionSurfacePlacersTriggered=" + summary.regionSurfacePlacersTriggered()
                    + " regionCavePlacersChecked=" + summary.regionCavePlacersChecked()
                    + " regionCavePlacersTriggered=" + summary.regionCavePlacersTriggered()
                    + " objectAttempts=" + summary.objectAttempts()
                    + " objectPlaced=" + summary.objectPlaced()
                    + " objectRejected=" + summary.objectRejected()
                    + " objectNull=" + summary.objectNull()
                    + " objectErrors=" + summary.objectErrors());
        }
    }

    private RNG applyNoise(int x, int z, long seed) {
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(x, z)));
    }

    private IrisBiome resolveCaveObjectBiome(int x, int z, int surfaceY, IrisBiome surfaceBiome) {
        int legacySampleY = Math.max(1, surfaceY - 48);
        IrisBiome legacyCaveBiome = getEngineMantle().getEngine().getCaveBiome(x, legacySampleY, z);
        if (legacyCaveBiome == null) {
            legacyCaveBiome = surfaceBiome;
        }

        int[] sampleDepths = new int[]{48, 80, 112};
        IrisBiome ladderChoice = null;
        for (int sampleDepth : sampleDepths) {
            int sampleY = Math.max(1, surfaceY - sampleDepth);
            IrisBiome sampled = getEngineMantle().getEngine().getCaveBiome(x, sampleY, z);
            boolean sameSurface = sampled == surfaceBiome;
            if (!sameSurface && sampled != null && surfaceBiome != null) {
                String sampledKey = sampled.getLoadKey();
                String surfaceKey = surfaceBiome.getLoadKey();
                sameSurface = sampledKey != null && sampledKey.equals(surfaceKey);
            }

            if (sampled == null || sameSurface) {
                continue;
            }

            if (!sampled.getCarvingObjects().isEmpty()) {
                ladderChoice = sampled;
            }
        }

        if (ladderChoice != null) {
            return ladderChoice;
        }

        return legacyCaveBiome;
    }

    @ChunkCoordinates
    private ObjectPlacementSummary placeObjects(MantleWriter writer, RNG rng, int x, int z, IrisBiome surfaceBiome, IrisBiome caveBiome, IrisRegion region, boolean traceRegen) {
        int biomeSurfaceChecked = 0;
        int biomeSurfaceTriggered = 0;
        int biomeCaveChecked = 0;
        int biomeCaveTriggered = 0;
        int regionSurfaceChecked = 0;
        int regionSurfaceTriggered = 0;
        int regionCaveChecked = 0;
        int regionCaveTriggered = 0;
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;
        IrisCaveProfile biomeCaveProfile = resolveCaveProfile(caveBiome.getCaveProfile(), region.getCaveProfile());
        IrisCaveProfile regionCaveProfile = resolveCaveProfile(region.getCaveProfile(), caveBiome.getCaveProfile());
        int biomeSurfaceExclusionDepth = resolveSurfaceObjectExclusionDepth(biomeCaveProfile);
        int regionSurfaceExclusionDepth = resolveSurfaceObjectExclusionDepth(regionCaveProfile);

        for (IrisObjectPlacement i : surfaceBiome.getSurfaceObjects()) {
            biomeSurfaceChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005));
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=biome-surface"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                biomeSurfaceTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, biomeSurfaceExclusionDepth, traceRegen, x, z, "biome-surface");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following biome: " + surfaceBiome.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : caveBiome.getCarvingObjects()) {
            if (!i.getCarvingSupport().equals(CarvingMode.CARVING_ONLY)) {
                continue;
            }
            biomeCaveChecked++;
            boolean chance = rng.chance(i.getChance());
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=biome-cave"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                biomeCaveTriggered++;
                try {
                    ObjectPlacementResult result = placeCaveObject(writer, rng, x, z, i, biomeCaveProfile, traceRegen, x, z, "biome-cave");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place cave objects in the following biome: " + caveBiome.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            regionSurfaceChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005));
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=region-surface"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                regionSurfaceTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, regionSurfaceExclusionDepth, traceRegen, x, z, "region-surface");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following region: " + region.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : region.getCarvingObjects()) {
            if (!i.getCarvingSupport().equals(CarvingMode.CARVING_ONLY)) {
                continue;
            }
            regionCaveChecked++;
            boolean chance = rng.chance(i.getChance());
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=region-cave"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                regionCaveTriggered++;
                try {
                    ObjectPlacementResult result = placeCaveObject(writer, rng, x, z, i, regionCaveProfile, traceRegen, x, z, "region-cave");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place cave objects in the following region: " + region.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        return new ObjectPlacementSummary(
                biomeSurfaceChecked,
                biomeSurfaceTriggered,
                biomeCaveChecked,
                biomeCaveTriggered,
                regionSurfaceChecked,
                regionSurfaceTriggered,
                regionCaveChecked,
                regionCaveTriggered,
                attempts,
                placed,
                rejected,
                nullObjects,
                errors
        );
    }

    @BlockCoordinates
    private ObjectPlacementResult placeObject(
            MantleWriter writer,
            RNG rng,
            int x,
            int z,
            IrisObjectPlacement objectPlacement,
            int surfaceObjectExclusionDepth,
            boolean traceRegen,
            int chunkX,
            int chunkZ,
            String scope
    ) {
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;
        int density = objectPlacement.getDensity(rng, x, z, getData());

        for (int i = 0; i < density; i++) {
            attempts++;
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                nullObjects++;
                if (traceRegen) {
                    Iris.warn("Regen object placement null object: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " densityIndex=" + i
                            + " density=" + density
                            + " placementKeys=" + objectPlacement.getPlace().toString(","));
                }
                continue;
            }
            int xx = rng.i(x, x + 15);
            int zz = rng.i(z, z + 15);
            int surfaceObjectExclusionRadius = resolveSurfaceObjectExclusionRadius(v);
            if (surfaceObjectExclusionDepth > 0 && hasSurfaceCarveExposure(writer, xx, zz, surfaceObjectExclusionDepth, surfaceObjectExclusionRadius)) {
                rejected++;
                continue;
            }
            int id = rng.i(0, Integer.MAX_VALUE);
            IrisObjectPlacement effectivePlacement = resolveEffectivePlacement(objectPlacement, v);
            try {
                int result = v.place(xx, -1, zz, writer, effectivePlacement, rng, (b, data) -> {
                    writer.setData(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id);
                    if (effectivePlacement.isDolphinTarget() && effectivePlacement.isUnderwater() && B.isStorageChest(data)) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                    }
                }, null, getData());

                if (result >= 0) {
                    placed++;
                } else {
                    rejected++;
                }

                if (traceRegen) {
                    Iris.info("Regen object placement result: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " object=" + v.getLoadKey()
                            + " resultY=" + result
                            + " px=" + xx
                            + " pz=" + zz
                            + " densityIndex=" + i
                            + " density=" + density);
                }
            } catch (Throwable e) {
                errors++;
                Iris.reportError(e);
                Iris.error("Regen object placement exception: chunk=" + chunkX + "," + chunkZ
                        + " scope=" + scope
                        + " object=" + v.getLoadKey()
                        + " densityIndex=" + i
                        + " density=" + density
                        + " error=" + e.getClass().getSimpleName() + ":" + e.getMessage());
            }
        }

        return new ObjectPlacementResult(attempts, placed, rejected, nullObjects, errors);
    }

    @ChunkCoordinates
    private ObjectPlacementResult placeCaveObject(
            MantleWriter writer,
            RNG rng,
            int chunkX,
            int chunkZ,
            IrisObjectPlacement objectPlacement,
            IrisCaveProfile caveProfile,
            boolean traceRegen,
            int metricChunkX,
            int metricChunkZ,
            String scope
    ) {
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int density = objectPlacement.getDensity(rng, minX, minZ, getData());
        KMap<Long, KList<Integer>> anchorCache = new KMap<>();
        IrisCaveAnchorMode anchorMode = resolveAnchorMode(objectPlacement, caveProfile);
        int anchorScanStep = resolveAnchorScanStep(caveProfile);
        int objectMinDepthBelowSurface = resolveObjectMinDepthBelowSurface(caveProfile);
        int anchorSearchAttempts = resolveAnchorSearchAttempts(caveProfile);

        for (int i = 0; i < density; i++) {
            attempts++;
            IrisObject object = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (object == null) {
                nullObjects++;
                if (traceRegen) {
                    Iris.warn("Regen cave object placement null object: chunk=" + metricChunkX + "," + metricChunkZ
                            + " scope=" + scope
                            + " densityIndex=" + i
                            + " density=" + density
                            + " placementKeys=" + objectPlacement.getPlace().toString(","));
                }
                logCaveReject(
                        scope,
                        "NULL_OBJECT",
                        metricChunkX,
                        metricChunkZ,
                        objectPlacement,
                        null,
                        i,
                        density,
                        anchorMode,
                        anchorSearchAttempts,
                        anchorScanStep,
                        objectMinDepthBelowSurface,
                        null,
                        null
                );
                continue;
            }

            int x = 0;
            int z = 0;
            int y = -1;
            for (int search = 0; search < anchorSearchAttempts; search++) {
                int candidateX = rng.i(minX, minX + 15);
                int candidateZ = rng.i(minZ, minZ + 15);
                int candidateY = findCaveAnchorY(writer, rng, candidateX, candidateZ, anchorMode, anchorScanStep, objectMinDepthBelowSurface, anchorCache);
                if (candidateY < 0) {
                    continue;
                }

                x = candidateX;
                z = candidateZ;
                y = candidateY;
                break;
            }

            if (y < 0) {
                rejected++;
                logCaveReject(
                        scope,
                        "NO_ANCHOR",
                        metricChunkX,
                        metricChunkZ,
                        objectPlacement,
                        object,
                        i,
                        density,
                        anchorMode,
                        anchorSearchAttempts,
                        anchorScanStep,
                        objectMinDepthBelowSurface,
                        null,
                        null
                );
                continue;
            }

            int id = rng.i(0, Integer.MAX_VALUE);
            IrisObjectPlacement effectivePlacement = resolveEffectivePlacement(objectPlacement, object);
            AtomicBoolean wrotePlacementData = new AtomicBoolean(false);

            try {
                int result = object.place(x, y, z, writer, effectivePlacement, rng, (b, data) -> {
                    wrotePlacementData.set(true);
                    writer.setData(b.getX(), b.getY(), b.getZ(), object.getLoadKey() + "@" + id);
                    if (effectivePlacement.isDolphinTarget() && effectivePlacement.isUnderwater() && B.isStorageChest(data)) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                    }
                }, null, getData());

                boolean wroteBlocks = wrotePlacementData.get();
                if (wroteBlocks) {
                    placed++;
                } else if (result < 0) {
                    rejected++;
                    logCaveReject(
                            scope,
                            "PLACE_NEGATIVE",
                            metricChunkX,
                            metricChunkZ,
                            objectPlacement,
                            object,
                            i,
                            density,
                            anchorMode,
                            anchorSearchAttempts,
                            anchorScanStep,
                            objectMinDepthBelowSurface,
                            y,
                            null
                    );
                }

                if (traceRegen) {
                    Iris.info("Regen cave object placement result: chunk=" + metricChunkX + "," + metricChunkZ
                            + " scope=" + scope
                            + " object=" + object.getLoadKey()
                            + " resultY=" + result
                            + " anchorY=" + y
                            + " px=" + x
                            + " pz=" + z
                            + " wroteBlocks=" + wroteBlocks
                            + " densityIndex=" + i
                            + " density=" + density);
                }
            } catch (Throwable e) {
                errors++;
                Iris.reportError(e);
                Iris.error("Regen cave object placement exception: chunk=" + metricChunkX + "," + metricChunkZ
                        + " scope=" + scope
                        + " object=" + object.getLoadKey()
                        + " densityIndex=" + i
                        + " density=" + density
                        + " error=" + e.getClass().getSimpleName() + ":" + e.getMessage());
                logCaveReject(
                        scope,
                        "EXCEPTION",
                        metricChunkX,
                        metricChunkZ,
                        objectPlacement,
                        object,
                        i,
                        density,
                        anchorMode,
                        anchorSearchAttempts,
                        anchorScanStep,
                        objectMinDepthBelowSurface,
                        y,
                        e
                );
            }
        }

        return new ObjectPlacementResult(attempts, placed, rejected, nullObjects, errors);
    }

    private void logCaveReject(
            String scope,
            String reason,
            int chunkX,
            int chunkZ,
            IrisObjectPlacement objectPlacement,
            IrisObject object,
            int densityIndex,
            int density,
            IrisCaveAnchorMode anchorMode,
            int anchorSearchAttempts,
            int anchorScanStep,
            int objectMinDepthBelowSurface,
            Integer anchorY,
            Throwable error
    ) {
        if (!IrisSettings.get().getGeneral().isDebug()) {
            return;
        }

        String placementKeys = objectPlacement == null ? "none" : objectPlacement.getPlace().toString(",");
        String objectKey = object == null ? "null" : object.getLoadKey();
        String throttleKey = scope + "|" + reason + "|" + placementKeys + "|" + objectKey;
        CaveRejectLogState state = CAVE_REJECT_LOG_STATE.computeIfAbsent(throttleKey, (k) -> new CaveRejectLogState());
        long now = System.currentTimeMillis();
        long last = state.lastLogMs.get();
        if ((now - last) < CAVE_REJECT_LOG_THROTTLE_MS) {
            state.suppressed.incrementAndGet();
            return;
        }

        if (!state.lastLogMs.compareAndSet(last, now)) {
            state.suppressed.incrementAndGet();
            return;
        }

        int suppressed = state.suppressed.getAndSet(0);
        String anchorYText = anchorY == null ? "none" : String.valueOf(anchorY);
        String errorText = error == null ? "none" : error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
        Iris.warn("Cave object reject: scope=" + scope
                + " reason=" + reason
                + " chunk=" + chunkX + "," + chunkZ
                + " object=" + objectKey
                + " placements=" + placementKeys
                + " densityIndex=" + densityIndex
                + " density=" + density
                + " anchorMode=" + anchorMode
                + " anchorSearchAttempts=" + anchorSearchAttempts
                + " anchorScanStep=" + anchorScanStep
                + " minDepthBelowSurface=" + objectMinDepthBelowSurface
                + " anchorY=" + anchorYText
                + " forcePlace=" + (objectPlacement != null && objectPlacement.isForcePlace())
                + " carvingSupport=" + (objectPlacement == null ? "none" : objectPlacement.getCarvingSupport())
                + " bottom=" + (objectPlacement != null && objectPlacement.isBottom())
                + " suppressed=" + suppressed
                + " error=" + errorText);
    }

    private IrisObjectPlacement resolveEffectivePlacement(IrisObjectPlacement objectPlacement, IrisObject object) {
        if (objectPlacement == null || object == null) {
            return objectPlacement;
        }

        String loadKey = object.getLoadKey();
        if (loadKey == null || loadKey.isBlank()) {
            return objectPlacement;
        }

        String normalized = loadKey.toLowerCase(Locale.ROOT);
        boolean legacyImported = normalized.startsWith("imports/")
                || normalized.contains("/imports/")
                || normalized.contains("imports/");
        IrisExternalDatapack externalDatapack = resolveExternalDatapackForObjectKey(normalized);
        boolean externalImported = externalDatapack != null;
        boolean imported = legacyImported || externalImported;

        if (!imported) {
            return objectPlacement;
        }

        ObjectPlaceMode mode = objectPlacement.getMode();
        boolean needsModeChange = mode != ObjectPlaceMode.FAST_MIN_STILT;
        if (!needsModeChange) {
            return objectPlacement;
        }

        IrisObjectPlacement effectivePlacement = objectPlacement.toPlacement(loadKey);
        effectivePlacement.setMode(ObjectPlaceMode.FAST_MIN_STILT);
        return effectivePlacement;
    }

    private IrisExternalDatapack resolveExternalDatapackForObjectKey(String normalizedLoadKey) {
        if (normalizedLoadKey == null || normalizedLoadKey.isBlank()) {
            return null;
        }

        int slash = normalizedLoadKey.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String candidateId = normalizedLoadKey.substring(0, slash);
        if (candidateId.isBlank()) {
            return null;
        }

        IrisDimension dimension = getDimension();
        if (dimension == null || dimension.getExternalDatapacks() == null || dimension.getExternalDatapacks().isEmpty()) {
            return null;
        }

        for (IrisExternalDatapack externalDatapack : dimension.getExternalDatapacks()) {
            if (externalDatapack == null || !externalDatapack.isEnabled()) {
                continue;
            }

            String id = externalDatapack.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (candidateId.equals(id.toLowerCase(Locale.ROOT))) {
                return externalDatapack;
            }
        }

        return null;
    }

    private int findCaveAnchorY(MantleWriter writer, RNG rng, int x, int z, IrisCaveAnchorMode anchorMode, int anchorScanStep, int objectMinDepthBelowSurface, KMap<Long, KList<Integer>> anchorCache) {
        long key = Cache.key(x, z);
        KList<Integer> anchors = anchorCache.computeIfAbsent(key, (k) -> scanCaveAnchorColumn(writer, anchorMode, anchorScanStep, objectMinDepthBelowSurface, x, z));
        if (anchors.isEmpty()) {
            return -1;
        }

        if (anchors.size() == 1) {
            return anchors.get(0);
        }

        return anchors.get(rng.i(0, anchors.size() - 1));
    }

    private KList<Integer> scanCaveAnchorColumn(MantleWriter writer, IrisCaveAnchorMode anchorMode, int anchorScanStep, int objectMinDepthBelowSurface, int x, int z) {
        KList<Integer> anchors = new KList<>();
        int height = getEngineMantle().getEngine().getHeight();
        int step = Math.max(1, anchorScanStep);
        int surfaceY = getEngineMantle().getEngine().getHeight(x, z);
        int maxAnchorY = Math.min(height - 1, surfaceY - Math.max(0, objectMinDepthBelowSurface));
        if (maxAnchorY <= 1) {
            return anchors;
        }

        for (int y = 1; y < maxAnchorY; y += step) {
            if (!writer.isCarved(x, y, z)) {
                continue;
            }

            boolean solidBelow = y <= 0 || !writer.isCarved(x, y - 1, z);
            boolean solidAbove = y >= (height - 1) || !writer.isCarved(x, y + 1, z);
            if (matchesCaveAnchor(anchorMode, solidBelow, solidAbove)) {
                anchors.add(y);
            }
        }

        return anchors;
    }

    private boolean matchesCaveAnchor(IrisCaveAnchorMode anchorMode, boolean solidBelow, boolean solidAbove) {
        return switch (anchorMode) {
            case PROFILE_DEFAULT, FLOOR -> solidBelow;
            case CEILING -> solidAbove;
            case CENTER -> !solidBelow && !solidAbove;
            case ANY -> true;
        };
    }

    private IrisCaveProfile resolveCaveProfile(IrisCaveProfile preferred, IrisCaveProfile secondary) {
        IrisCaveProfile dimensionProfile = getDimension().getCaveProfile();
        if (preferred != null && preferred.isEnabled()) {
            return preferred;
        }

        if (secondary != null && secondary.isEnabled()) {
            return secondary;
        }

        if (dimensionProfile != null) {
            return dimensionProfile;
        }

        return new IrisCaveProfile();
    }

    private IrisCaveAnchorMode resolveAnchorMode(IrisObjectPlacement objectPlacement, IrisCaveProfile caveProfile) {
        IrisCaveAnchorMode placementMode = objectPlacement.getCaveAnchorMode();
        if (placementMode != null && !placementMode.equals(IrisCaveAnchorMode.PROFILE_DEFAULT)) {
            return placementMode;
        }

        if (caveProfile == null) {
            return IrisCaveAnchorMode.FLOOR;
        }

        IrisCaveAnchorMode profileMode = caveProfile.getDefaultObjectAnchor();
        if (profileMode == null || profileMode.equals(IrisCaveAnchorMode.PROFILE_DEFAULT)) {
            return IrisCaveAnchorMode.FLOOR;
        }

        return profileMode;
    }

    private int resolveAnchorScanStep(IrisCaveProfile caveProfile) {
        if (caveProfile == null) {
            return 1;
        }

        return Math.max(1, caveProfile.getAnchorScanStep());
    }

    private int resolveObjectMinDepthBelowSurface(IrisCaveProfile caveProfile) {
        if (caveProfile == null) {
            return 6;
        }

        return Math.max(0, caveProfile.getObjectMinDepthBelowSurface());
    }

    private int resolveSurfaceObjectExclusionDepth(IrisCaveProfile caveProfile) {
        if (caveProfile == null) {
            return 5;
        }

        return Math.max(0, caveProfile.getSurfaceObjectExclusionDepth());
    }

    private int resolveSurfaceObjectExclusionRadius(IrisObject object) {
        if (object == null) {
            return 1;
        }

        int maxDimension = Math.max(object.getW(), object.getD());
        return Math.max(1, Math.min(8, Math.floorDiv(Math.max(1, maxDimension), 2)));
    }

    private int resolveAnchorSearchAttempts(IrisCaveProfile caveProfile) {
        if (caveProfile == null) {
            return 6;
        }

        return Math.max(1, caveProfile.getAnchorSearchAttempts());
    }

    private boolean hasSurfaceCarveExposure(MantleWriter writer, int x, int z, int depth, int radius) {
        int horizontalRadius = Math.max(0, radius);
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                int columnX = x + dx;
                int columnZ = z + dz;
                int surfaceY = getEngineMantle().getEngine().getHeight(columnX, columnZ, true);
                int fromY = Math.max(1, surfaceY - Math.max(0, depth));
                int toY = Math.min(getEngineMantle().getEngine().getHeight() - 1, surfaceY + 1);
                for (int y = fromY; y <= toY; y++) {
                    if (writer.isCarved(columnX, y, columnZ)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isRegenTraceThread() {
        return Thread.currentThread().getName().startsWith("Iris-Regen-")
                && IrisSettings.get().getGeneral().isDebug();
    }

    private record ObjectPlacementSummary(
            int biomeSurfacePlacersChecked,
            int biomeSurfacePlacersTriggered,
            int biomeCavePlacersChecked,
            int biomeCavePlacersTriggered,
            int regionSurfacePlacersChecked,
            int regionSurfacePlacersTriggered,
            int regionCavePlacersChecked,
            int regionCavePlacersTriggered,
            int objectAttempts,
            int objectPlaced,
            int objectRejected,
            int objectNull,
            int objectErrors
    ) {
    }

    private record ObjectPlacementResult(int attempts, int placed, int rejected, int nullObjects, int errors) {
    }

    private static final class CaveRejectLogState {
        private final AtomicLong lastLogMs = new AtomicLong(0L);
        private final AtomicInteger suppressed = new AtomicInteger(0);
    }

    @BlockCoordinates
    private Set<String> guessPlacedKeys(RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        Set<String> f = new KSet<>();
        for (int i = 0; i < objectPlacement.getDensity(rng, x, z, getData()); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                continue;
            }

            f.add(v.getLoadKey());
        }

        return f;
    }

    public Set<String> guess(int x, int z) {
        // todo The guess doesnt bring into account that the placer may return -1
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        IrisBiome biome = getEngineMantle().getEngine().getSurfaceBiome((x << 4) + 8, (z << 4) + 8);
        IrisRegion region = getEngineMantle().getEngine().getRegion((x << 4) + 8, (z << 4) + 8);
        Set<String> v = new KSet<>();
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        return v;
    }

    protected int computeRadius() {
        var dimension = getDimension();

        AtomicInteger xg = new AtomicInteger();
        AtomicInteger zg = new AtomicInteger();

        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        for (var region : dimension.getAllRegions(this::getData)) {
            for (var j : region.getObjects()) {
                if (j.getScale().canScaleBeyond()) {
                    scalars.put(j.getScale(), j.getPlace());
                } else {
                    objects.addAll(j.getPlace());
                }
            }
        }
        for (var biome : dimension.getAllBiomes(this::getData)) {
            for (var j : biome.getObjects()) {
                if (j.getScale().canScaleBeyond()) {
                    scalars.put(j.getScale(), j.getPlace());
                } else {
                    objects.addAll(j.getPlace());
                }
            }
        }

        BurstExecutor e = getEngineMantle().getTarget().getBurster().burst(objects.size());
        boolean maintenanceFolia = false;
        if (J.isFolia()) {
            var world = getEngineMantle().getEngine().getWorld().realWorld();
            maintenanceFolia = world != null && IrisToolbelt.isWorldMaintenanceActive(world);
        }
        if (maintenanceFolia) {
            Iris.info("MantleObjectComponent radius scan using single-threaded mode during maintenance regen.");
            e.setMulticore(false);
        }
        KMap<String, BlockVector> sizeCache = new KMap<>();
        for (String i : objects) {
            e.queue(() -> {
                try {
                    BlockVector bv = sizeCache.computeIfAbsent(i, (k) -> {
                        try {
                            return IrisObject.sampleSize(getData().getObjectLoader().findFile(i));
                        } catch (IOException ex) {
                            Iris.reportError(ex);
                            ex.printStackTrace();
                        }

                        return null;
                    });

                    if (bv == null) {
                        throw new RuntimeException();
                    }

                    if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                        Iris.warn("Object " + i + " has a large size (" + bv + ") and may increase memory usage!");
                    }

                    synchronized (xg) {
                        xg.getAndSet(Math.max(bv.getBlockX(), xg.get()));
                    }

                    synchronized (zg) {
                        zg.getAndSet(Math.max(bv.getBlockZ(), zg.get()));
                    }
                } catch (Throwable ed) {
                    Iris.reportError(ed);

                }
            });
        }

        for (Map.Entry<IrisObjectScale, KList<String>> entry : scalars.entrySet()) {
            double ms = entry.getKey().getMaximumScale();
            for (String j : entry.getValue()) {
                e.queue(() -> {
                    try {
                        BlockVector bv = sizeCache.computeIfAbsent(j, (k) -> {
                            try {
                                return IrisObject.sampleSize(getData().getObjectLoader().findFile(j));
                            } catch (IOException ioException) {
                                Iris.reportError(ioException);
                                ioException.printStackTrace();
                            }

                            return null;
                        });

                        if (bv == null) {
                            throw new RuntimeException();
                        }

                        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                            Iris.warn("Object " + j + " has a large size (" + bv + ") and may increase memory usage! (Object scaled up to " + Form.pc(ms, 2) + ")");
                        }

                        synchronized (xg) {
                            xg.getAndSet((int) Math.max(Math.ceil(bv.getBlockX() * ms), xg.get()));
                        }

                        synchronized (zg) {
                            zg.getAndSet((int) Math.max(Math.ceil(bv.getBlockZ() * ms), zg.get()));
                        }
                    } catch (Throwable ee) {
                        Iris.reportError(ee);

                    }
                });
            }
        }

        e.complete();
        return Math.max(xg.get(), zg.get());
    }
}
