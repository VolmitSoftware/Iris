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


import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import java.util.Optional;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.object.CarvingMode;
import art.arcane.iris.engine.object.DecayControlPlacer;
import art.arcane.iris.engine.object.IObjectPlacer;
import art.arcane.iris.engine.object.IrisAxisRotationClamp;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisCaveAnchorMode;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.IrisObjectTranslate;
import art.arcane.iris.engine.object.IrisObjectVacuum;
import art.arcane.iris.engine.object.IrisProceduralObjects;
import art.arcane.iris.engine.object.IrisProceduralPlacement;
import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.ObjectPlaceMode;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.NoiseType;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.iris.util.common.math.IrisBlockVector;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ComponentFlag(ReservedFlag.OBJECT)
public class MantleObjectComponent extends IrisMantleComponent {
    private static final long CAVE_REJECT_LOG_THROTTLE_MS = 5000L;
    private static final int BEDROCK_CLEARANCE = 6;
    private static final byte LIQUID_FORCED_AIR = 3;
    private static final Map<String, CaveRejectLogState> CAVE_REJECT_LOG_STATE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_LOAD_KEY_WARNED = ConcurrentHashMap.newKeySet();

    private final Object collisionRuleLock;
    private final ObjectSourcePlanCache sourcePlans;
    private volatile int collisionRuleState;

    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.OBJECT, 2);
        this.collisionRuleLock = new Object();
        this.sourcePlans = new ObjectSourcePlanCache();
        this.collisionRuleState = -1;
    }

    private static String placementMarker(IrisObject object, int id, String context) {
        String key = object == null ? null : object.getLoadKey();
        if (key == null || key.isEmpty() || key.equals("null")) {
            String fingerprint = context + "|" + (object == null ? "<null>" : object.getClass().getSimpleName());
            if (MISSING_LOAD_KEY_WARNED.add(fingerprint)) {
                java.io.File file = object == null ? null : object.getLoadFile();
                IrisLogging.warn("Skipping placement marker write: IrisObject has no loadKey (context=" + context
                        + ", file=" + (file == null ? "<unknown>" : file.getPath()) + "). "
                        + "This would previously produce 'Couldn't find Object: null' warnings on chunk reload.");
            }
            return null;
        }
        return key + "@" + id;
    }

    private static boolean isTreePlacement(IrisObject object, IrisObjectPlacement placement) {
        String key = object == null ? null : object.getLoadKey();
        return (key != null && key.startsWith("trees/"))
                || (placement != null && placement.getTrees() != null && placement.getTrees().isNotEmpty());
    }

    private static void writeTreeMaterial(IObjectPlacer placer, int x, int y, int z, PlatformBlockState data) {
        placer.setData(x, y, z, TreeBlockMaterial.of(data));
    }

    @Override
    public int getOutputRadius() {
        return hasCollisionRules() ? 0 : getRadius();
    }

    @Override
    public int getInputRadius() {
        return hasCollisionRules() ? calculateInputRadius(getRadius(), true) : 0;
    }

    @Override
    public void hotload() {
        super.hotload();
        synchronized (collisionRuleLock) {
            collisionRuleState = -1;
            sourcePlans.clear();
        }
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        if (!hasCollisionRules()) {
            generateOrigin(writer, x, z, context);
            return;
        }
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, x, z);
        replaySourceChunks(
                x,
                z,
                getRadius(),
                (sourceX, sourceZ) -> transaction.apply(sourcePlans.get(
                        sourceX,
                        sourceZ,
                        () -> buildSourcePlan(writer, sourceX, sourceZ, context)
                ))
        );
        transaction.commit();
    }

    private ObjectSourcePlan buildSourcePlan(
            MantleWriter writer,
            int sourceChunkX,
            int sourceChunkZ,
            ChunkContext context
    ) {
        ObjectDestinationTransaction scratch = new ObjectDestinationTransaction(
                writer,
                sourceChunkX,
                sourceChunkZ
        );
        replaySourcePredecessors(
                sourceChunkX,
                sourceChunkZ,
                getRadius(),
                (predecessorX, predecessorZ) -> generateOrigin(scratch, predecessorX, predecessorZ, context)
        );
        int checkpoint = scratch.mutationCheckpoint();
        generateOrigin(scratch, sourceChunkX, sourceChunkZ, context);
        return scratch.sourcePlanSince(checkpoint);
    }

    void generateOrigin(ObjectPassPlacer writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsNewGenerationChunk(x, z)) {
            return;
        }
        boolean traceRegen = isRegenTraceThread();
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = complex.getRegionStream().get(xxx, zzz);
        IrisBiome surfaceBiome = complex.getTrueBiomeStream().get(xxx, zzz);
        int surfaceY = complex.getRoundedHeighteightStream().get(xxx, zzz);
        IrisBiome caveBiome = resolveCaveObjectBiome(xxx, zzz, surfaceY, surfaceBiome);
        if (IrisSettings.get().getGeneral().isDebug() && (x & 31) == 0 && (z & 31) == 0) {
            int carvedBlocks = 0;
            int minY = 1;
            int maxY = Math.min(getEngineMantle().getEngine().getHeight() - 1, surfaceY - 14);
            for (int sy = minY; sy < maxY; sy++) {
                if (writer.isCarved(8 + (x << 4), sy, 8 + (z << 4))) {
                    carvedBlocks++;
                }
            }
            IrisLogging.debug("Cave object diag: chunk=" + x + "," + z
                    + " surfaceBiome=" + surfaceBiome.getLoadKey()
                    + " caveBiome=" + caveBiome.getLoadKey()
                    + " surfaceY=" + surfaceY
                    + " maxAnchorY=" + maxY
                    + " carvedAtCenter=" + carvedBlocks
                    + " biomeCarvingObjects=" + caveBiome.getCarvingObjects().size()
                    + " regionCarvingObjects=" + region.getCarvingObjects().size()
                    + " sameBiome=" + (caveBiome == surfaceBiome || java.util.Objects.equals(caveBiome.getLoadKey(), surfaceBiome.getLoadKey())));
        }
        if (traceRegen) {
            IrisLogging.debug("Regen object layer start: chunk=" + x + "," + z
                    + " surfaceBiome=" + surfaceBiome.getLoadKey()
                    + " caveBiome=" + caveBiome.getLoadKey()
                    + " region=" + region.getLoadKey()
                    + " biomeSurfacePlacers=" + surfaceBiome.getSurfaceObjects().size()
                    + " biomeCavePlacers=" + caveBiome.getCarvingObjects().size()
                    + " regionSurfacePlacers=" + region.getSurfaceObjects().size()
                    + " regionCavePlacers=" + region.getCarvingObjects().size());
        }
        ObjectPlacementSummary summary = placeObjects(writer, rng, x, z, surfaceBiome, caveBiome, region, complex, traceRegen);
        placeProceduralObjects(writer, rng, x, z, surfaceBiome, caveBiome, region);
        UpperDimensionContext upperCtx = getEngineMantle().getEngine().getUpperContext();
        IrisDimension dimension = getDimension();
        if (upperCtx != null && dimension.isUpperDimensionObjects()) {
            placeUpperObjects(writer, rng, x, z, xxx, zzz, surfaceY, upperCtx, dimension, complex, traceRegen);
        }
        if (traceRegen) {
            IrisLogging.debug("Regen object layer done: chunk=" + x + "," + z
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

    static int sourceChunkRadius(int radius) {
        return radius > 0 ? Math.ceilDiv(radius, 16) : 0;
    }

    static int calculateInputRadius(int radius, boolean sourceAnchoredCollisions) {
        int normalizedRadius = Math.max(0, radius);
        long sourceChunkRadius = sourceChunkRadius(normalizedRadius);
        long sourceLegs = sourceAnchoredCollisions ? 2L : 1L;
        long inputRadius = (sourceChunkRadius * 16L * sourceLegs) + normalizedRadius + 1L;
        return inputRadius >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) inputRadius;
    }

    static void replaySourceChunks(
            int destinationChunkX,
            int destinationChunkZ,
            int radius,
            SourceChunkConsumer consumer
    ) {
        int chunkRadius = sourceChunkRadius(radius);
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            int sourceChunkX = destinationChunkX + offsetX;
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                consumer.accept(sourceChunkX, destinationChunkZ + offsetZ);
            }
        }
    }

    static void replaySourcePredecessors(
            int sourceChunkX,
            int sourceChunkZ,
            int radius,
            SourceChunkConsumer consumer
    ) {
        int chunkRadius = sourceChunkRadius(radius);
        for (int offsetX = -chunkRadius; offsetX <= 0; offsetX++) {
            int predecessorX = sourceChunkX + offsetX;
            int maximumOffsetZ = offsetX < 0 ? chunkRadius : -1;
            for (int offsetZ = -chunkRadius; offsetZ <= maximumOffsetZ; offsetZ++) {
                consumer.accept(predecessorX, sourceChunkZ + offsetZ);
            }
        }
    }

    private boolean hasCollisionRules() {
        int state = collisionRuleState;
        if (state >= 0) {
            return state == 1;
        }
        synchronized (collisionRuleLock) {
            state = collisionRuleState;
            if (state < 0) {
                state = scanCollisionRules() ? 1 : 0;
                collisionRuleState = state;
            }
            return state == 1;
        }
    }

    private boolean scanCollisionRules() {
        if (scanCollisionRules(getDimension(), this::getData)) {
            return true;
        }
        UpperDimensionContext upperContext = activeUpperContext();
        return upperContext != null && scanCollisionRules(upperContext.getDimension(), upperContext);
    }

    private static boolean scanCollisionRules(IrisDimension dimension, DataProvider dataProvider) {
        for (IrisRegion region : dimension.getAllRegions(dataProvider)) {
            if (region != null && (hasCollisionRules(region.getObjects())
                    || hasCollisionRules(region.getProceduralObjects()))) {
                return true;
            }
        }
        for (IrisBiome biome : dimension.getReachableBiomes(dataProvider)) {
            if (biome != null && (hasCollisionRules(biome.getObjects())
                    || hasCollisionRules(biome.getProceduralObjects()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCollisionRules(KList<IrisObjectPlacement> placements) {
        if (placements == null) {
            return false;
        }
        for (IrisObjectPlacement placement : placements) {
            if (hasCollisionRules(placement)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCollisionRules(IrisObjectPlacement placement) {
        return placement != null && (!placement.getAllowedCollisions().isEmpty()
                || !placement.getForbiddenCollisions().isEmpty());
    }

    private static boolean hasCollisionRules(IrisProceduralObjects proceduralObjects) {
        if (proceduralObjects == null || proceduralObjects.isEmpty()) {
            return false;
        }
        for (IrisProceduralPlacement placement : proceduralObjects.getAllPlacements()) {
            if (placement != null && hasCollisionRules(placement.asPlacement())) {
                return true;
            }
        }
        return false;
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
    private ObjectPlacementSummary placeObjects(ObjectPassPlacer writer, RNG rng, int x, int z, IrisBiome surfaceBiome, IrisBiome caveBiome, IrisRegion region, IrisComplex complex, boolean traceRegen) {
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
        CaveAnchorCache caveAnchorCache = new CaveAnchorCache();

        for (IrisObjectPlacement i : surfaceBiome.getSurfaceObjects()) {
            biomeSurfaceChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && !i.isCompatExcluded(getData());
            if (traceRegen) {
                IrisLogging.debug("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=biome-surface"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                biomeSurfaceTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, complex, traceRegen, x, z, "biome-surface");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place objects in the following biome: " + surfaceBiome.getName());
                    IrisLogging.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    IrisLogging.error("Are these objects missing?");
                }
            }
        }

        for (IrisObjectPlacement i : caveBiome.getCarvingObjects()) {
            if (!i.getCarvingSupport().supportsCarving()) {
                continue;
            }
            biomeCaveChecked++;
            boolean chance = rng.chance(i.getChance()) && !i.isCompatExcluded(getData());
            if (traceRegen) {
                IrisLogging.debug("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=biome-cave"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                biomeCaveTriggered++;
                try {
                    ObjectPlacementResult result = placeCaveObject(writer, rng, x, z, i, biomeCaveProfile, complex, traceRegen, x, z, "biome-cave", caveBiome.getLoadKey(), caveAnchorCache);
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place cave objects in the following biome: " + caveBiome.getName());
                    IrisLogging.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    IrisLogging.error("Are these objects missing?");
                }
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            regionSurfaceChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && !i.isCompatExcluded(getData());
            if (traceRegen) {
                IrisLogging.debug("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=region-surface"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                regionSurfaceTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, complex, traceRegen, x, z, "region-surface");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place objects in the following region: " + region.getName());
                    IrisLogging.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    IrisLogging.error("Are these objects missing?");
                }
            }
        }

        for (IrisObjectPlacement i : region.getCarvingObjects()) {
            if (!i.getCarvingSupport().supportsCarving()) {
                continue;
            }
            regionCaveChecked++;
            boolean chance = rng.chance(i.getChance()) && !i.isCompatExcluded(getData());
            if (traceRegen) {
                IrisLogging.debug("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=region-cave"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                regionCaveTriggered++;
                try {
                    ObjectPlacementResult result = placeCaveObject(writer, rng, x, z, i, regionCaveProfile, complex, traceRegen, x, z, "region-cave", null, caveAnchorCache);
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place cave objects in the following region: " + region.getName());
                    IrisLogging.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    IrisLogging.error("Are these objects missing?");
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

    @ChunkCoordinates
    private void placeProceduralObjects(ObjectPassPlacer writer, RNG rng, int x, int z, IrisBiome surfaceBiome, IrisBiome caveBiome, IrisRegion region) {
        IrisCaveProfile surfaceCaveProfile = resolveCaveProfile(surfaceBiome.getCaveProfile(), region.getCaveProfile());
        IrisCaveProfile regionCaveProfile = resolveCaveProfile(region.getCaveProfile(), caveBiome == null ? null : caveBiome.getCaveProfile());
        placeProceduralFrom(writer, rng, x, z, surfaceBiome.getProceduralObjects(), surfaceBiome.getName(), surfaceCaveProfile, surfaceBiome.getLoadKey());
        placeProceduralFrom(writer, rng, x, z, region.getProceduralObjects(), region.getName(), regionCaveProfile, null);
        if (caveBiome != null && caveBiome != surfaceBiome) {
            IrisCaveProfile caveProfile = resolveCaveProfile(caveBiome.getCaveProfile(), region.getCaveProfile());
            placeProceduralFrom(writer, rng, x, z, caveBiome.getProceduralObjects(), caveBiome.getName(), caveProfile, caveBiome.getLoadKey());
        }
    }

    @ChunkCoordinates
    private void placeProceduralFrom(
            ObjectPassPlacer writer,
            RNG rng,
            int x,
            int z,
            IrisProceduralObjects proceduralObjects,
            String scope,
            IrisCaveProfile caveProfile,
            String expectedCaveBiomeKey
    ) {
        if (proceduralObjects == null || proceduralObjects.isEmpty()) {
            return;
        }

        IrisComplex complex = getComplex();
        int blockX = x << 4;
        int blockZ = z << 4;
        boolean golden = GoldenDebugObjectPlacer.isGoldenDebugChunk(x, z);
        CaveAnchorCache caveAnchorCache = new CaveAnchorCache();
        for (IrisProceduralPlacement p : proceduralObjects.getAllPlacements()) {
            boolean treePlacement = p instanceof IrisProceduralTree;
            boolean chancePassed = passesProceduralChance(rng, p.getChance());
            if (golden) {
                IrisLogging.debug("Goldendebug procedural chance: chunk=" + x + "," + z
                        + " scope=" + scope
                        + " placement=" + p.getName()
                        + " passed=" + chancePassed);
            }
            if (!chancePassed) {
                continue;
            }

            IrisObjectPlacement placement = p.asPlacement();
            boolean carving = placement.getCarvingSupport() == CarvingMode.CARVING_ONLY;
            IrisCaveAnchorMode anchorMode = resolveAnchorMode(placement, caveProfile);
            if (placement.getMode() == ObjectPlaceMode.CEILING_HANG) {
                anchorMode = IrisCaveAnchorMode.CEILING;
            }
            int anchorScanStep = resolveAnchorScanStep(caveProfile);
            int minDepthBelowSurface = resolveObjectMinDepthBelowSurface(caveProfile);
            int anchorSearchAttempts = resolveAnchorSearchAttempts(caveProfile);
            IObjectPlacer basePlacer = p.isPlausible() ? new DecayControlPlacer(writer) : writer;
            IObjectPlacer placer = golden ? new GoldenDebugObjectPlacer(basePlacer, scope + "/" + p.getName()) : basePlacer;
            int density = Math.max(1, p.getDensity());
            for (int i = 0; i < density; i++) {
                IrisObject variant = placement.scaleObject(rng, p.getVariantObject(getData(), rng), getDimension());
                if (variant == null) {
                    if (golden) {
                        IrisLogging.debug("Goldendebug procedural pick: chunk=" + x + "," + z
                                + " placement=" + p.getName()
                                + " densityIndex=" + i
                                + " variant=null");
                    }
                    continue;
                }
                CavePlacementAnchor caveAnchor = null;
                if (carving) {
                    caveAnchor = findCavePlacementAnchor(
                            writer,
                            rng,
                            blockX,
                            blockZ,
                            anchorMode,
                            anchorScanStep,
                            minDepthBelowSurface,
                            anchorSearchAttempts,
                            expectedCaveBiomeKey,
                            placement.isUnderwater(),
                            caveAnchorCache
                    );
                }
                if (carving && caveAnchor == null) {
                    if (golden) {
                        IrisLogging.debug("Goldendebug procedural cave anchor rejected: chunk=" + x + "," + z
                                + " placement=" + p.getName()
                                + " minDepthBelowSurface=" + minDepthBelowSurface);
                    }
                    continue;
                }
                int xx = caveAnchor == null ? rng.i(blockX, blockX + 16) : caveAnchor.x();
                int zz = caveAnchor == null ? rng.i(blockZ, blockZ + 16) : caveAnchor.z();
                int id = rng.i(0, Integer.MAX_VALUE);
                if (golden) {
                    KList<IrisObject> pool = p.getVariantObjects(getData());
                    IrisLogging.debug("Goldendebug procedural pick: chunk=" + x + "," + z
                            + " placement=" + p.getName()
                            + " densityIndex=" + i
                            + " variant=" + variant.getLoadKey()
                            + " variantIndex=" + pool.indexOf(variant) + "/" + pool.size()
                            + " xx=" + xx
                            + " zz=" + zz
                            + " mode=" + placement.getMode()
                            + " carving=" + carving);
                }
                try {
                    int placeResult = -1;
                    CaveObjectPlacementTransaction.CommitResult commitResult = CaveObjectPlacementTransaction.CommitResult.EMPTY;
                    if (carving) {
                        int caveFloorY = caveAnchor.y();
                        if (golden) {
                            IrisLogging.debug("Goldendebug procedural caveFloor: chunk=" + x + "," + z
                                    + " placement=" + p.getName()
                                    + " xx=" + xx
                                    + " zz=" + zz
                                    + " caveFloorY=" + caveFloorY);
                        }
                        IrisObjectPlacement effectivePlacement = resolveCavePlacement(placement, variant, caveProfile);
                        ContainedPlacementResult contained = placeContainedCaveObject(
                                placer,
                                variant,
                                xx,
                                caveFloorY,
                                zz,
                                effectivePlacement,
                                minDepthBelowSurface,
                                id,
                                "procedural",
                                treePlacement,
                                rng
                        );
                        placeResult = contained.resultY();
                        commitResult = contained.commitResult();
                    } else {
                        if (!allowsObjectPlacement(complex, variant, placement, xx, zz)) {
                            continue;
                        }
                        String marker = placementMarker(variant, id, "procedural");
                        placeResult = variant.place(xx, -1, zz, placer, placement, rng, (b, data) -> {
                            if (marker != null) {
                                placer.setData(b.getX(), b.getY(), b.getZ(), marker);
                            }
                            if (treePlacement && marker != null) {
                                writeTreeMaterial(placer, b.getX(), b.getY(), b.getZ(), data);
                            }
                        }, null, getData());
                    }
                    if (golden) {
                        IrisLogging.debug("Goldendebug procedural result: chunk=" + x + "," + z
                                + " placement=" + p.getName()
                                + " variant=" + variant.getLoadKey()
                                + " xx=" + xx
                                + " zz=" + zz
                                + " resultY=" + placeResult
                                + " commit=" + commitResult);
                    }
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place procedural object '" + p.getName() + "' in " + scope);
                }
            }
        }
    }

    static boolean passesProceduralChance(RNG rng, double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 1.0) {
            return true;
        }
        return rng.chance(Math.max(0.0, Math.min(1.0, chance + rng.d(-0.005, 0.005))));
    }

    private CavePlacementAnchor findCavePlacementAnchor(
            ObjectPassPlacer writer,
            RNG rng,
            int minX,
            int minZ,
            IrisCaveAnchorMode anchorMode,
            int anchorScanStep,
            int minDepthBelowSurface,
            int searchAttempts,
            String expectedCaveBiomeKey,
            boolean underwater,
            CaveAnchorCache anchorCache
    ) {
        for (int search = 0; search < searchAttempts; search++) {
            int candidateX = rng.i(minX, minX + 16);
            int candidateZ = rng.i(minZ, minZ + 16);
            int candidateY = findCaveAnchorY(
                    writer,
                    rng,
                    candidateX,
                    candidateZ,
                    anchorMode,
                    anchorScanStep,
                    minDepthBelowSurface,
                    anchorCache
            );
            HydrologyCaveCell hydrology = candidateY < 0
                    ? null
                    : writer.getDataIfPresent(candidateX, candidateY, candidateZ, HydrologyCaveCell.class);
            MatterCavern cavern = candidateY < 0
                    ? null
                    : writer.getDataIfPresent(candidateX, candidateY, candidateZ, MatterCavern.class);
            if (candidateY < 0
                    || caveAnchorBiomeConflicts(candidateX, candidateY, candidateZ, expectedCaveBiomeKey)
                    || !acceptsCaveAnchorAt(candidateX, candidateY, candidateZ, underwater, cavern, hydrology)) {
                continue;
            }
            return new CavePlacementAnchor(candidateX, candidateY, candidateZ);
        }
        return null;
    }

    private boolean acceptsCaveAnchorAt(int x, int y, int z, boolean underwater,
                                        MatterCavern cavern, HydrologyCaveCell hydrology) {
        Optional<TerrainBoundarySignature> resolved = getEngineMantle().getComplex().resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            return acceptsResolvedCaveAnchor(underwater,
                    resolved.get().geometry().voxelAt(y + getEngineMantle().getEngine().getMinHeight()));
        }
        return acceptsCaveAnchorFluid(underwater, hydrology == null ? cavern : hydrology.asCavern(),
                hydrology, y, getDimension().getCaveLavaHeight());
    }

    static boolean acceptsResolvedCaveAnchor(boolean underwater, BoundaryColumnGeometry.Voxel voxel) {
        return !voxel.protectedContent() && (voxel.phase() == BoundaryColumnGeometry.Phase.AIR
                || underwater && voxel.phase() == BoundaryColumnGeometry.Phase.FLUID);
    }

    static boolean acceptsCaveAnchorFluid(boolean underwater, MatterCavern cavern, int y, int lavaHeight) {
        return acceptsCaveAnchorFluid(underwater, cavern, null, y, lavaHeight);
    }

    static boolean acceptsCaveAnchorFluid(
            boolean underwater,
            MatterCavern cavern,
            HydrologyCaveCell hydrology,
            int y,
            int lavaHeight
    ) {
        if (hydrology != null && hydrology.protectsPlacement()) {
            return false;
        }
        if (cavern == null || !cavern.isCavern()) {
            return false;
        }
        if (underwater || cavern.getLiquid() == LIQUID_FORCED_AIR) {
            return true;
        }
        return cavern.isAir() && y > lavaHeight;
    }

    private ContainedPlacementResult placeContainedCaveObject(
            IObjectPlacer placer,
            IrisObject object,
            int x,
            int anchorY,
            int z,
            IrisObjectPlacement placement,
            int minDepthBelowSurface,
            int id,
            String markerContext,
            boolean treePlacement,
            RNG rng
    ) {
        IrisComplex complex = placer.getEngine() == null ? null : placer.getEngine().getComplex();
        if (!allowsObjectPlacement(complex, object, placement, x, z)) {
            return new ContainedPlacementResult(
                    -1,
                    CaveObjectPlacementTransaction.CommitResult.REJECTED_TRANSITION);
        }
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(placer, anchorY, minDepthBelowSurface);
        int placeY = anchorY;
        if (placement.getMode() == ObjectPlaceMode.CEILING_HANG) {
            placeY = Math.max(1, transaction.getCaveCeiling(x, z) - 1 - Math.floorDiv(object.getH(), 2));
        }
        String marker = placementMarker(object, id, markerContext);
        int result = object.place(x, placeY, z, transaction, placement, rng, (block, data) -> {
            if (marker != null) {
                transaction.setData(block.getX(), block.getY(), block.getZ(), marker);
            }
            if (treePlacement && marker != null) {
                writeTreeMaterial(transaction, block.getX(), block.getY(), block.getZ(), data);
            }
            if (placement.isDolphinTarget() && placement.isUnderwater() && B.isStorageChest(data)) {
                transaction.setData(block.getX(), block.getY(), block.getZ(), MatterStructurePOI.BURIED_TREASURE);
            }
        }, null, getData());
        if (result < 0) {
            transaction.discard();
            return new ContainedPlacementResult(result, CaveObjectPlacementTransaction.CommitResult.EMPTY);
        }
        return new ContainedPlacementResult(result, transaction.commit());
    }

    @BlockCoordinates
    private ObjectPlacementResult placeObject(
            ObjectPassPlacer writer,
            RNG rng,
            int x,
            int z,
            IrisObjectPlacement objectPlacement,
            IrisComplex complex,
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
        boolean golden = GoldenDebugObjectPlacer.isGoldenDebugChunk(chunkX, chunkZ);

        for (int i = 0; i < density; i++) {
            attempts++;
            IrisObject v = objectPlacement.scaleObject(rng, objectPlacement.getObject(complex, rng), getDimension());
            if (v == null) {
                nullObjects++;
                if (traceRegen) {
                    IrisLogging.debug("Regen object placement null object: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " densityIndex=" + i
                            + " density=" + density
                            + " placementKeys=" + objectPlacement.getPlace().toString(","));
                }
                continue;
            }
            int xx = rng.i(x, x + 16);
            int zz = rng.i(z, z + 16);
            IrisObjectPlacement effectivePlacement = resolveEffectivePlacement(objectPlacement, v);
            if (!allowsObjectPlacement(complex, v, effectivePlacement, xx, zz)) {
                rejected++;
                continue;
            }
            boolean treePlacement = isTreePlacement(v, effectivePlacement);
            int id = rng.i(0, Integer.MAX_VALUE);
            IObjectPlacer placePlacer = golden ? new GoldenDebugObjectPlacer(writer, scope + "/" + v.getLoadKey()) : writer;
            if (golden) {
                IrisLogging.debug("Goldendebug object attempt: chunk=" + chunkX + "," + chunkZ
                        + " scope=" + scope
                        + " object=" + v.getLoadKey()
                        + " densityIndex=" + i
                        + " xx=" + xx
                        + " zz=" + zz
                        + " mode=" + effectivePlacement.getMode());
            }
            try {
                String marker = placementMarker(v, id, "surface");
                int result = v.place(xx, -1, zz, placePlacer, effectivePlacement, rng, (b, data) -> {
                    if (marker != null) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                    if (treePlacement && marker != null) {
                        writeTreeMaterial(writer, b.getX(), b.getY(), b.getZ(), data);
                    }
                    if (effectivePlacement.isDolphinTarget() && effectivePlacement.isUnderwater() && B.isStorageChest(data)) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                    }
                }, null, getData());

                if (result >= 0) {
                    placed++;
                } else {
                    rejected++;
                }

                if (golden) {
                    IrisLogging.debug("Goldendebug object result: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " object=" + v.getLoadKey()
                            + " resultY=" + result
                            + " fallback=surface");
                }

                if (traceRegen) {
                    IrisLogging.debug("Regen object placement result: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " object=" + v.getLoadKey()
                            + " resultY=" + result
                            + " px=" + xx
                            + " pz=" + zz
                            + " fallback=surface"
                            + " densityIndex=" + i
                            + " density=" + density);
                }
            } catch (Throwable e) {
                errors++;
                IrisLogging.reportError(e);
                IrisLogging.error("Regen object placement exception: chunk=" + chunkX + "," + chunkZ
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
    private boolean caveAnchorBiomeConflicts(int x, int y, int z, String expectedCaveBiomeKey) {
        if (expectedCaveBiomeKey == null) {
            return false;
        }
        Engine engine = getEngineMantle().getEngine();
        IrisBiome at = engine.getCaveBiome(x, y, z);
        IrisBiome surface = engine.getComplex().getTrueBiomeStream().get(x, z);
        return caveAnchorBiomeConflicts(at, surface, expectedCaveBiomeKey);
    }

    static boolean caveAnchorBiomeConflicts(IrisBiome at, IrisBiome surface, String expectedCaveBiomeKey) {
        if (expectedCaveBiomeKey == null || at == null) {
            return false;
        }
        String atKey = at.getLoadKey();
        if (atKey == null || atKey.equals(expectedCaveBiomeKey)) {
            return false;
        }
        if (surface != null && atKey.equals(surface.getLoadKey())) {
            return false;
        }
        return true;
    }

    private ObjectPlacementResult placeCaveObject(
            ObjectPassPlacer writer,
            RNG rng,
            int chunkX,
            int chunkZ,
            IrisObjectPlacement objectPlacement,
            IrisCaveProfile caveProfile,
            IrisComplex complex,
            boolean traceRegen,
            int metricChunkX,
            int metricChunkZ,
            String scope,
            String expectedCaveBiomeKey,
            CaveAnchorCache anchorCache
    ) {
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int density = objectPlacement.getDensity(rng, minX, minZ, getData());
        IrisCaveAnchorMode anchorMode = resolveAnchorMode(objectPlacement, caveProfile);
        if (objectPlacement.getMode() == ObjectPlaceMode.CEILING_HANG) {
            anchorMode = IrisCaveAnchorMode.CEILING;
        }
        int anchorScanStep = resolveAnchorScanStep(caveProfile);
        int objectMinDepthBelowSurface = resolveObjectMinDepthBelowSurface(caveProfile);
        int anchorSearchAttempts = resolveAnchorSearchAttempts(caveProfile);

        for (int i = 0; i < density; i++) {
            attempts++;
            IrisObject object = objectPlacement.scaleObject(rng, objectPlacement.getObject(complex, rng), getDimension());
            if (object == null) {
                nullObjects++;
                if (traceRegen) {
                    IrisLogging.debug("Regen cave object placement null object: chunk=" + metricChunkX + "," + metricChunkZ
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

            CavePlacementAnchor anchor = findCavePlacementAnchor(
                    writer,
                    rng,
                    minX,
                    minZ,
                    anchorMode,
                    anchorScanStep,
                    objectMinDepthBelowSurface,
                    anchorSearchAttempts,
                    expectedCaveBiomeKey,
                    objectPlacement.isUnderwater(),
                    anchorCache
            );

            if (anchor == null) {
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

            int x = anchor.x();
            int y = anchor.y();
            int z = anchor.z();

            int id = rng.i(0, Integer.MAX_VALUE);
            IrisObjectPlacement effectivePlacement = resolveCavePlacement(objectPlacement, object, caveProfile);

            try {
                ContainedPlacementResult contained = placeContainedCaveObject(
                        writer,
                        object,
                        x,
                        y,
                        z,
                        effectivePlacement,
                        objectMinDepthBelowSurface,
                        id,
                        "cave",
                        isTreePlacement(object, effectivePlacement),
                        rng
                );
                int result = contained.resultY();
                boolean wroteBlocks = contained.commitResult() == CaveObjectPlacementTransaction.CommitResult.COMMITTED;
                if (wroteBlocks) {
                    placed++;
                } else {
                    rejected++;
                    String rejectReason;
                    if (result < 0) {
                        rejectReason = "PLACE_NEGATIVE";
                    } else if (contained.commitResult() == CaveObjectPlacementTransaction.CommitResult.REJECTED_BOUNDS) {
                        rejectReason = "CONTAINMENT";
                    } else {
                        rejectReason = "NO_WRITES";
                    }
                    logCaveReject(
                            scope,
                            rejectReason,
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
                    IrisLogging.debug("Regen cave object placement result: chunk=" + metricChunkX + "," + metricChunkZ
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
                IrisLogging.reportError(e);
                IrisLogging.error("Regen cave object placement exception: chunk=" + metricChunkX + "," + metricChunkZ
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

    @ChunkCoordinates
    private void placeUpperObjects(
            ObjectPassPlacer writer,
            RNG rng,
            int chunkX,
            int chunkZ,
            int centerX,
            int centerZ,
            int lowerSurfaceCenterY,
            UpperDimensionContext upperCtx,
            IrisDimension dimension,
            IrisComplex complex,
            boolean traceRegen
    ) {
        IrisBiome upperBiome = upperCtx.getUpperBiome(centerX, centerZ);
        IrisRegion upperRegion = upperCtx.getUpperRegion(centerX, centerZ);
        if (upperBiome == null && upperRegion == null) {
            return;
        }

        boolean forcePlace = dimension.isUpperObjectsForcePlace();
        if (upperBiome != null) {
            for (IrisObjectPlacement i : upperBiome.getSurfaceObjects()) {
                if (!rng.chance(i.getChance() + rng.d(-0.005, 0.005)) || i.isCompatExcluded(getData())) {
                    continue;
                }
                try {
                    placeUpperObject(writer, rng, chunkX, chunkZ, i, upperCtx, complex, forcePlace, traceRegen, "upper-biome-surface");
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place upper-dimension objects in biome " + upperBiome.getName()
                            + ": " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ")");
                }
            }
        }

        if (upperRegion != null) {
            for (IrisObjectPlacement i : upperRegion.getSurfaceObjects()) {
                if (!rng.chance(i.getChance() + rng.d(-0.005, 0.005)) || i.isCompatExcluded(getData())) {
                    continue;
                }
                try {
                    placeUpperObject(writer, rng, chunkX, chunkZ, i, upperCtx, complex, forcePlace, traceRegen, "upper-region-surface");
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    IrisLogging.error("Failed to place upper-dimension objects in region " + upperRegion.getName()
                            + ": " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ")");
                }
            }
        }
    }

    @ChunkCoordinates
    private void placeUpperObject(
            ObjectPassPlacer writer,
            RNG rng,
            int chunkX,
            int chunkZ,
            IrisObjectPlacement objectPlacement,
            UpperDimensionContext upperCtx,
            IrisComplex complex,
            boolean forcePlace,
            boolean traceRegen,
            String scope
    ) {
        int chunkHeight = getEngineMantle().getEngine().getHeight();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int density = objectPlacement.getDensity(rng, minX, minZ, getData());

        for (int i = 0; i < density; i++) {
            IrisObject v = objectPlacement.scaleObject(rng, objectPlacement.getObject(upperCtx, rng), upperCtx.getDimension());
            if (v == null) {
                continue;
            }

            int xx = rng.i(minX, minX + 16);
            int zz = rng.i(minZ, minZ + 16);
            int upperSurfaceY = upperCtx.getEffectiveSurfaceY(xx, zz);
            if (upperSurfaceY >= chunkHeight - 2) {
                continue;
            }

            int halfH = Math.floorDiv(v.getH(), 2);
            int anchorY = upperSurfaceY - 1 - halfH;
            if (anchorY <= 1) {
                continue;
            }

            int id = rng.i(0, Integer.MAX_VALUE);
            IrisObjectPlacement placement = objectPlacement.toPlacement(v.getLoadKey());
            placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
            placement.setRotation(buildUpsideDownRotation());
            placement.setCarvingSupport(CarvingMode.ANYWHERE);
            if (forcePlace) {
                placement.setForcePlace(true);
            }
            if (!allowsObjectPlacement(complex, v, placement, xx, zz)) {
                continue;
            }
            boolean treePlacement = isTreePlacement(v, objectPlacement);
            String marker = placementMarker(v, id, "upper");

            int result = v.place(xx, anchorY, zz, writer, placement, rng, (b, data) -> {
                if (marker != null) {
                    writer.setData(b.getX(), b.getY(), b.getZ(), marker);
                }
                if (treePlacement && marker != null) {
                    writeTreeMaterial(writer, b.getX(), b.getY(), b.getZ(), data);
                }
                if (placement.isDolphinTarget() && placement.isUnderwater() && B.isStorageChest(data)) {
                    writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                }
            }, null, getData());

            if (traceRegen) {
                IrisLogging.debug("Upper object placement: chunk=" + chunkX + "," + chunkZ
                        + " scope=" + scope
                        + " object=" + v.getLoadKey()
                        + " anchorY=" + anchorY
                        + " upperSurfaceY=" + upperSurfaceY
                        + " resultY=" + result
                        + " forcePlace=" + forcePlace);
            }
        }
    }

    private IrisObjectRotation buildUpsideDownRotation() {
        IrisObjectRotation rt = new IrisObjectRotation();
        rt.setEnabled(true);
        rt.setXAxis(new IrisAxisRotationClamp(true, true, 180D, 180D, 90D));
        rt.setYAxis(new IrisAxisRotationClamp(true, false, 0D, 0D, 90D));
        rt.setZAxis(new IrisAxisRotationClamp());
        return rt;
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
        IrisLogging.debug("Cave object reject: scope=" + scope
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

        return applyDimensionSurfaceSupport(resolveImportedPlacement(objectPlacement, object));
    }

    /** Dimension acts as the floor: it can force support on and widen the buffer, never weaken either. */
    private IrisObjectPlacement applyDimensionSurfaceSupport(IrisObjectPlacement placement) {
        IrisDimension dimension = getDimension();
        boolean require = placement.isRequireSurfaceSupport() && dimension.isRequireObjectSurfaceSupport();
        int buffer = Math.max(placement.getSurfaceSupportBuffer(), dimension.getObjectSurfaceSupportBuffer());
        if (require == placement.isRequireSurfaceSupport() && buffer == placement.getSurfaceSupportBuffer()) {
            return placement;
        }

        IrisObjectPlacement resolved = placement.toPlacement(placement.getPlace().toArray(new String[0]));
        resolved.setRequireSurfaceSupport(require);
        resolved.setSurfaceSupportBuffer(buffer);
        return resolved;
    }

    private IrisObjectPlacement resolveImportedPlacement(IrisObjectPlacement objectPlacement, IrisObject object) {
        String loadKey = object.getLoadKey();
        if (loadKey == null || loadKey.isBlank()) {
            return objectPlacement;
        }

        String normalized = loadKey.toLowerCase(Locale.ROOT);
        boolean imported = normalized.startsWith("imports/")
                || normalized.contains("/imports/")
                || normalized.contains("imports/");

        if (!imported) {
            return objectPlacement;
        }

        ObjectPlaceMode mode = objectPlacement.getMode();
        if (mode == ObjectPlaceMode.FLOATING || mode == ObjectPlaceMode.STRUCTURE_PIECE) {
            return objectPlacement;
        }
        boolean needsModeChange = mode != ObjectPlaceMode.FAST_MIN_STILT;
        if (!needsModeChange) {
            return objectPlacement;
        }

        IrisObjectPlacement effectivePlacement = objectPlacement.toPlacement(loadKey);
        effectivePlacement.setMode(ObjectPlaceMode.FAST_MIN_STILT);
        return effectivePlacement;
    }

    private IrisObjectPlacement resolveCavePlacement(IrisObjectPlacement objectPlacement, IrisObject object, IrisCaveProfile caveProfile) {
        IrisObjectPlacement resolvedPlacement = resolveEffectivePlacement(objectPlacement, object);
        if (resolvedPlacement.getMode() != ObjectPlaceMode.CENTER_HEIGHT || caveProfile == null) {
            return resolvedPlacement;
        }

        ObjectPlaceMode profileMode = caveProfile.getDefaultObjectPlaceMode();
        if (profileMode == null) {
            return resolvedPlacement;
        }

        String loadKey = object.getLoadKey();
        if (loadKey == null || loadKey.isBlank()) {
            resolvedPlacement = resolvedPlacement.toPlacement();
        } else {
            resolvedPlacement = resolvedPlacement.toPlacement(loadKey);
        }
        resolvedPlacement.setMode(profileMode);
        return resolvedPlacement;
    }

    static int caveAnchorScanUpperBound(int worldHeight, int surfaceY, int minDepthBelowSurface) {
        int maxAnchorY = CaveObjectPlacementTransaction.maxBuriedY(worldHeight, surfaceY, minDepthBelowSurface);
        if (maxAnchorY <= 1) {
            return 0;
        }
        return maxAnchorY + 1;
    }

    private int findCaveAnchorY(ObjectPassPlacer writer, RNG rng, int x, int z, IrisCaveAnchorMode anchorMode, int anchorScanStep, int objectMinDepthBelowSurface, CaveAnchorCache anchorCache) {
        KList<Integer> anchors = anchorCache.get(writer, anchorMode, anchorScanStep, objectMinDepthBelowSurface, x, z);
        if (anchors.isEmpty()) {
            return -1;
        }

        if (anchors.size() == 1) {
            return anchors.get(0);
        }

        return anchors.get(rng.i(anchors.size()));
    }

    private KList<Integer> scanCaveAnchorColumn(ObjectPassPlacer writer, IrisCaveAnchorMode anchorMode, int anchorScanStep, int objectMinDepthBelowSurface, int x, int z, CaveAnchorCache anchorCache) {
        int height = getEngineMantle().getEngine().getHeight();
        int step = Math.max(1, anchorScanStep);
        int surfaceY = anchorCache.getSurfaceHeight(x, z);
        int maxAnchorExclusive = caveAnchorScanUpperBound(height, surfaceY, objectMinDepthBelowSurface);
        if (maxAnchorExclusive == 0) {
            return new KList<>();
        }

        int carvedHeight = Math.min(height, maxAnchorExclusive + 3);
        byte[] carvedColumn = anchorCache.getCarvedColumn(writer, x, z, carvedHeight);
        return scanCaveAnchorRange(anchorMode, step, carvedHeight, BEDROCK_CLEARANCE, maxAnchorExclusive, carvedColumn);
    }

    private KList<Integer> scanCaveAnchorRange(IrisCaveAnchorMode anchorMode, int step, int height, int minAnchorY, int maxAnchorY, byte[] carvedColumn) {
        KList<Integer> anchors = new KList<>();
        int startY = alignAnchorScanStart(minAnchorY, step);
        for (int y = startY; y < maxAnchorY; y += step) {
            if (!isCarved(carvedColumn, y)) {
                continue;
            }

            boolean solidBelow = hasSolidNeighbor(carvedColumn, y, height, -1);
            boolean solidAbove = hasSolidNeighbor(carvedColumn, y, height, 1);
            if (matchesCaveAnchor(anchorMode, solidBelow, solidAbove)) {
                anchors.add(y);
            }
        }
        return anchors;
    }

    private int alignAnchorScanStart(int minAnchorY, int step) {
        if (minAnchorY <= BEDROCK_CLEARANCE) {
            return BEDROCK_CLEARANCE;
        }

        int delta = minAnchorY - BEDROCK_CLEARANCE;
        int steps = (delta + step - 1) / step;
        return BEDROCK_CLEARANCE + (steps * step);
    }

    private boolean isCarved(byte[] carvedColumn, int y) {
        return y >= 0 && y < carvedColumn.length && carvedColumn[y] == 1;
    }

    private boolean hasSolidNeighbor(byte[] carvedColumn, int y, int height, int direction) {
        for (int d = 1; d <= 3; d++) {
            int ny = y + (direction * d);
            if (ny < 0 || ny >= height) {
                return true;
            }
            if (!isCarved(carvedColumn, ny)) {
                return true;
            }
        }
        return false;
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

    private int resolveAnchorSearchAttempts(IrisCaveProfile caveProfile) {
        if (caveProfile == null) {
            return 6;
        }

        return Math.max(1, caveProfile.getAnchorSearchAttempts());
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

    private record CavePlacementAnchor(int x, int y, int z) {
    }

    private record ContainedPlacementResult(
            int resultY,
            CaveObjectPlacementTransaction.CommitResult commitResult
    ) {
    }

    private static final class CaveRejectLogState {
        private final AtomicLong lastLogMs = new AtomicLong(0L);
        private final AtomicInteger suppressed = new AtomicInteger(0);
    }

    @BlockCoordinates
    private Set<String> guessPlacedKeys(RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        Set<String> f = new KSet<>();
        for (int i = 0; i < objectPlacement.getDensity(rng, x, z, getData()); i++) {
            IrisObject v = objectPlacement.scaleObject(rng, objectPlacement.getObject(getComplex(), rng), getDimension());
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
        int blockX = (x << 4) + 8;
        int blockZ = (z << 4) + 8;
        IrisBiome biome = getComplex().getTrueBiomeStream().get(blockX, blockZ);
        IrisRegion region = getComplex().getRegionStream().get(blockX, blockZ);
        Set<String> v = new KSet<>();
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && !i.isCompatExcluded(getData())) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && !i.isCompatExcluded(getData())) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        return v;
    }

    protected int computeRadius() {
        KSet<String> warnedLargeObjects = new KSet<>();
        int radius = computeDimensionRadius(getDimension(), this::getData, warnedLargeObjects);
        UpperDimensionContext upperContext = activeUpperContext();
        if (upperContext != null) {
            radius = Math.max(radius, computeDimensionRadius(
                    upperContext.getDimension(),
                    upperContext,
                    warnedLargeObjects
            ));
        }
        return radius;
    }

    private int computeDimensionRadius(
            IrisDimension dimension,
            DataProvider dataProvider,
            KSet<String> warnedLargeObjects
    ) {
        KMap<String, IrisBlockVector> sizeCache = new KMap<>();
        int radius = 0;
        for (IrisRegion region : dimension.getAllRegions(dataProvider)) {
            if (region == null) {
                continue;
            }
            radius = Math.max(radius, computePlacementRadius(
                    region.getObjects(),
                    dimension,
                    dataProvider,
                    sizeCache,
                    warnedLargeObjects
            ));
            radius = Math.max(radius, computeProceduralRadius(region.getProceduralObjects(), dimension, dataProvider));
        }
        for (IrisBiome biome : dimension.getReachableBiomes(dataProvider)) {
            if (biome == null) {
                continue;
            }
            radius = Math.max(radius, computePlacementRadius(
                    biome.getObjects(),
                    dimension,
                    dataProvider,
                    sizeCache,
                    warnedLargeObjects
            ));
            radius = Math.max(radius, computeProceduralRadius(biome.getProceduralObjects(), dimension, dataProvider));
        }
        return radius;
    }

    private int computePlacementRadius(
            KList<IrisObjectPlacement> placements,
            IrisDimension dimension,
            DataProvider dataProvider,
            KMap<String, IrisBlockVector> sizeCache,
            KSet<String> warnedLargeObjects
    ) {
        int radius = 0;
        for (IrisObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            for (String objectKey : placement.getPlace()) {
                try {
                    IrisBlockVector size = loadObjectSize(dataProvider, sizeCache, objectKey);
                    if (size == null) {
                        continue;
                    }
                    int reach = calculatePlacementReach(size, placement, placement.getMaximumScale(dimension));
                    if (reach > 128 && warnedLargeObjects.add(objectKey)) {
                        IrisLogging.warn("Object " + objectKey + " has a large placement reach (" + reach + " blocks) and may increase memory usage!");
                    }
                    radius = Math.max(radius, reach);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }
        }
        return radius;
    }

    private int computeProceduralRadius(IrisProceduralObjects procedural, IrisDimension dimension, DataProvider dataProvider) {
        if (procedural == null || procedural.isEmpty()) {
            return 0;
        }
        int radius = 0;
        for (IrisProceduralPlacement placement : procedural.getAllPlacements()) {
            if (placement == null) {
                continue;
            }
            KList<IrisObject> variants = placement.getVariantObjects(dataProvider.getData());
            if (variants == null) {
                continue;
            }
            IrisObjectPlacement objectPlacement = placement.asPlacement();
            for (IrisObject variant : variants) {
                if (variant == null) {
                    continue;
                }
                IrisBlockVector size = new IrisBlockVector(variant.getW(), variant.getH(), variant.getD());
                radius = Math.max(radius, calculatePlacementReach(size, objectPlacement, objectPlacement.getMaximumScale(dimension)));
            }
        }
        return radius;
    }

    static int calculatePlacementReach(IrisBlockVector size, IrisObjectPlacement placement, double scale) {
        if (size == null) {
            return 0;
        }
        if (placement == null) {
            return Math.max(Math.abs(size.getBlockX()), Math.abs(size.getBlockZ()));
        }

        int width = scaledDimension(size.getBlockX(), scale);
        int height = scaledDimension(size.getBlockY(), scale);
        int depth = scaledDimension(size.getBlockZ(), scale);
        IrisObjectRotation rotation = placement.getRotation();
        boolean rotateX = rotation != null && rotation.isEnabled() && rotation.getXAxis() != null && rotation.getXAxis().isEnabled();
        boolean rotateZ = rotation != null && rotation.isEnabled() && rotation.getZAxis() != null && rotation.getZAxis().isEnabled();
        int footprint = rotateX || rotateZ ? Math.max(width, Math.max(height, depth)) : Math.max(width, depth);
        int translation = calculateTranslationReach(placement.getTranslate(), rotation, rotateX || rotateZ);
        int warp = placement.getWarp() != null && !placement.getWarp().isFlat()
                ? (int) Math.ceil(Math.abs(placement.getWarp().getMultiplier()) / 2D)
                : 0;
        int vacuum = IrisObjectVacuum.isVacuumMode(placement.getMode())
                ? 2 * IrisObjectVacuum.resolveRadius(placement.getMode(), placement.getVacuumSettings())
                : 0;
        long reach = (long) footprint + translation + warp + vacuum;
        return reach > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reach;
    }

    static boolean allowsObjectPlacement(
            IrisComplex complex,
            IrisObject object,
            IrisObjectPlacement placement,
            int anchorX,
            int anchorZ
    ) {
        if (complex == null || object == null) {
            return true;
        }
        int reach = calculatePlacementReach(
                new IrisBlockVector(object.getW(), object.getH(), object.getD()),
                placement, 1D);
        return complex.allowsNewGenerationFootprint(
                saturatedOffset(anchorX, -reach),
                saturatedOffset(anchorZ, -reach),
                saturatedOffset(anchorX, reach),
                saturatedOffset(anchorZ, reach));
    }

    private static int saturatedOffset(int coordinate, int offset) {
        long result = (long) coordinate + offset;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }

    private static int scaledDimension(int dimension, double scale) {
        int absoluteDimension = Math.abs(dimension);
        if (scale == 1D) {
            return absoluteDimension;
        }
        return (int) Math.ceil(absoluteDimension * scale);
    }

    private static int calculateTranslationReach(IrisObjectTranslate translate, IrisObjectRotation rotation, boolean rotateVertically) {
        if (translate == null || !translate.canTranslate()) {
            return 0;
        }
        double x = translate.getX();
        double y = translate.getY();
        double z = translate.getZ();
        if (rotateVertically) {
            return (int) Math.ceil(Math.sqrt((x * x) + (y * y) + (z * z)));
        }
        boolean rotateY = rotation != null && rotation.isEnabled() && rotation.getYAxis() != null && rotation.getYAxis().isEnabled();
        return rotateY ? (int) Math.ceil(Math.hypot(x, z)) : (int) Math.max(Math.abs(x), Math.abs(z));
    }

    private IrisBlockVector loadObjectSize(
            DataProvider dataProvider,
            KMap<String, IrisBlockVector> sizeCache,
            String objectKey
    ) {
        return sizeCache.computeIfAbsent(objectKey, k -> {
            try {
                return IrisObject.sampleSize(dataProvider.getData().getObjectLoader().findFile(objectKey));
            } catch (IOException e) {
                IrisLogging.reportError(e);
            }

            return null;
        });
    }

    private UpperDimensionContext activeUpperContext() {
        if (!getDimension().isUpperDimensionObjects()) {
            return null;
        }
        UpperDimensionContext upperContext = getEngineMantle().getEngine().getUpperContext();
        return upperContext == null || upperContext.isSelfReferencing() ? null : upperContext;
    }

    private final class CaveAnchorCache {
        private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<KList<Integer>>> settingCaches;
        private final Long2ObjectOpenHashMap<byte[]> carvedColumns;
        private final Long2IntOpenHashMap surfaceHeights;

        private CaveAnchorCache() {
            this.settingCaches = new Long2ObjectOpenHashMap<>();
            this.carvedColumns = new Long2ObjectOpenHashMap<>();
            this.surfaceHeights = new Long2IntOpenHashMap();
            this.surfaceHeights.defaultReturnValue(Integer.MIN_VALUE);
        }

        private KList<Integer> get(ObjectPassPlacer writer, IrisCaveAnchorMode anchorMode, int anchorScanStep, int objectMinDepthBelowSurface, int x, int z) {
            if (anchorScanStep > 65535 || objectMinDepthBelowSurface > 65535) {
                return scanCaveAnchorColumn(writer, anchorMode, anchorScanStep, objectMinDepthBelowSurface, x, z, this);
            }

            long settingKey = ((long) anchorMode.ordinal() << 32) | ((long) anchorScanStep << 16) | objectMinDepthBelowSurface;
            Long2ObjectOpenHashMap<KList<Integer>> columnCache = settingCaches.get(settingKey);
            if (columnCache == null) {
                columnCache = new Long2ObjectOpenHashMap<>();
                settingCaches.put(settingKey, columnCache);
            }

            long columnKey = Cache.key(x, z);
            KList<Integer> anchors = columnCache.get(columnKey);
            if (anchors != null) {
                return anchors;
            }

            anchors = scanCaveAnchorColumn(writer, anchorMode, anchorScanStep, objectMinDepthBelowSurface, x, z, this);
            columnCache.put(columnKey, anchors);
            return anchors;
        }

        private byte[] getCarvedColumn(ObjectPassPlacer writer, int x, int z, int height) {
            long columnKey = Cache.key(x, z);
            byte[] carvedColumn = carvedColumns.get(columnKey);
            if (carvedColumn != null && carvedColumn.length == height) {
                return carvedColumn;
            }

            carvedColumn = writer.getCarvedColumn(x, z, height);
            carvedColumns.put(columnKey, carvedColumn);
            return carvedColumn;
        }

        private int getSurfaceHeight(int x, int z) {
            long columnKey = Cache.key(x, z);
            int surfaceY = surfaceHeights.get(columnKey);
            if (surfaceY != Integer.MIN_VALUE) {
                return surfaceY;
            }

            surfaceY = getEngineMantle().trueHeight(x, z);
            surfaceHeights.put(columnKey, surfaceY);
            return surfaceY;
        }
    }

    @FunctionalInterface
    interface SourceChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

}
