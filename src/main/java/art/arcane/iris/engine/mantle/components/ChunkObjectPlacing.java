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

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.carve.CaveLavaHeightSettings;
import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.RequireSurfaceSupportSettings;
import art.arcane.iris.builtin.objects.SurfaceSupportBufferSettings;
import art.arcane.iris.builtin.objects.UpperObjectsForceSettings;
import art.arcane.iris.builtin.objects.UpperObjectsSettings;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.modules.objects.CavePlacement;
import art.arcane.iris.modules.objects.TerrainTransaction;
import art.arcane.iris.modules.objects.ObjectPool;
import art.arcane.iris.modules.objects.ObjectSeparation;
import art.arcane.iris.modules.objects.PlacementChanges;
import art.arcane.iris.modules.objects.PlacementTags;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.ResourceId;
import com.volmit.stage.MantleStageContext;
import com.volmit.placement.PlacementRecord;
import com.volmit.world.TerrainSampler;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Places what the objects of one chunk's region and biomes say: the objects of its surface, caves and the terrain stacked
 * above, in that order, all drawing from the one random of the chunk.
 */
final class ChunkObjectPlacing {
    /** Objects read their chunk's settings at local 8, 8 as Iris always did, not at the API's centre column (local 7, 7): moving it would move objects. */
    private static final int OBJECT_COLUMN_OFFSET_BLOCKS = 8;
    private static final int CHUNK_WIDTH_BLOCKS = 16;
    private static final double CHANCE_JITTER = 0.005;
    private static final int[] CAVE_BIOME_SAMPLE_DEPTHS = {48, 80, 112};
    private static final int LEGACY_CAVE_SAMPLE_DEPTH = 48;
    private static final int UPPER_SURFACE_MARGIN_BLOCKS = 2;
    private static final int MINIMUM_UPPER_ANCHOR_Y = 1;
    private static final int DIAGNOSTIC_CHUNK_MASK = 31;
    private static final int DIAGNOSTIC_SURFACE_MARGIN = 14;
    private static final String TREE_FOLDER = "trees/";
    private static final String REGEN_THREAD_PREFIX = "Iris-Regen-";

    private final ObjectPool objects;
    private final ObjectSeparation separation;
    private final ModuleLog log;
    private final MantleStageContext context;
    private final ObjectChunk chunk;
    private final TerrainSampler terrain;
    private final int chunkX;
    private final int chunkZ;
    private final int centerX;
    private final int centerZ;
    private final RNG rng;
    private final boolean traceRegen;
    private final boolean golden;
    private final EffectivePlacements effective;
    private final CaveSettings caves;
    private final int worldHeight;
    private final int caveLavaHeight;

    ChunkObjectPlacing(ObjectPool objects, ModuleLog log, MantleStageContext context) {
        this.objects = objects;
        this.separation = ObjectSeparation.of(context.world());
        this.log = log;
        this.context = context;
        this.chunk = new ObjectChunk(context, log);
        this.terrain = context.terrain();
        this.chunkX = context.chunkX();
        this.chunkZ = context.chunkZ();
        this.centerX = context.originX() + OBJECT_COLUMN_OFFSET_BLOCKS;
        this.centerZ = context.originZ() + OBJECT_COLUMN_OFFSET_BLOCKS;
        this.rng = objects.chunkRandom(chunkX, chunkZ);
        this.traceRegen = Thread.currentThread().getName().startsWith(REGEN_THREAD_PREFIX) && log.debugging();
        this.golden = chunk.goldenDebug(chunkX, chunkZ);
        this.worldHeight = context.mantle().bounds().maxY();
        this.caveLavaHeight = Optional.ofNullable(dimensionLevel(CaveLavaHeightSettings.class)).orElseGet(CaveLavaHeightSettings::new).height();
        this.effective = new EffectivePlacements(dimensionRequiresSurfaceSupport(), dimensionSurfaceSupportBuffer());
        this.caves = new CaveSettings(dimensionLevel(CaveProfile.class));
    }

    /** Places what originates in this chunk. */
    void place() {
        Column column = readColumn();
        if (log.debugging() && (chunkX & DIAGNOSTIC_CHUNK_MASK) == 0 && (chunkZ & DIAGNOSTIC_CHUNK_MASK) == 0) {
            logCaveDiagnostics(column);
        }
        if (traceRegen) {
            logLayerStart(column);
        }
        Tally tally = placeObjects(column);
        if (upperObjectsEnabled()) {
            placeUpperObjects(column);
        }
        if (traceRegen) {
            logLayerDone(tally);
        }
    }

    /** The region and biomes at the chunk's centre and what each declares. */
    private Column readColumn() {
        ResourceId region = terrain.biome(centerX, terrain.bounds().minY(), centerZ).region();
        ResourceId surface = terrain.surfaceBiome(centerX, centerZ);
        int surfaceY = terrain.surfaceHeight(centerX, centerZ);
        ResourceId cave = resolveCaveObjectBiome(surfaceY, surface);
        return new Column(region, surface, cave, surfaceY, objectsOf(cave, region, surface), profilesOf(cave, region, surface));
    }

    private ResourceId resolveCaveObjectBiome(int surfaceY, ResourceId surfaceBiome) {
        ResourceId legacy = caveBiomeAt(Math.max(1, surfaceY - LEGACY_CAVE_SAMPLE_DEPTH)).orElse(surfaceBiome);
        ResourceId ladderChoice = null;
        for (int depth : CAVE_BIOME_SAMPLE_DEPTHS) {
            Optional<ResourceId> sampled = caveBiomeAt(Math.max(1, surfaceY - depth));
            if (sampled.isEmpty() || sampled.get().equals(surfaceBiome)) {
                continue;
            }
            if (hasCarvingObjects(context.settings().declaredBy(sampled.get(), ObjectSettings.class))) {
                ladderChoice = sampled.get();
            }
        }
        return ladderChoice != null ? ladderChoice : legacy;
    }

    private Optional<ResourceId> caveBiomeAt(int y) {
        return terrain.biome(centerX, y, centerZ).caveBiome();
    }

    private static boolean hasCarvingObjects(Optional<ObjectSettings> settings) {
        return settings.isPresent() && !carvingObjects(settings.get()).isEmpty();
    }

    private ColumnObjects objectsOf(ResourceId cave, ResourceId region, ResourceId surface) {
        SettingsStack<ObjectSettings> declared = context.settings().at(ObjectSettings.class, centerX, centerZ);
        return new ColumnObjects(levelValue(declared, Level.BIOME), caveSettings(cave, surface, declared, ObjectSettings.class),
                levelValue(declared, Level.REGION));
    }

    private ColumnProfiles profilesOf(ResourceId cave, ResourceId region, ResourceId surface) {
        SettingsStack<CaveProfile> declared = context.settings().at(CaveProfile.class, centerX, centerZ);
        return new ColumnProfiles(levelValue(declared, Level.BIOME), caveSettings(cave, surface, declared, CaveProfile.class),
                levelValue(declared, Level.REGION));
    }

    private <F extends IrisFeature> F caveSettings(ResourceId cave, ResourceId surface, SettingsStack<F> declared, Class<F> type) {
        if (cave.equals(surface)) {
            return levelValue(declared, Level.BIOME);
        }
        return context.settings().declaredBy(cave, type).orElse(null);
    }

    private <F extends IrisFeature> F dimensionLevel(Class<F> type) {
        return levelValue(context.settings().at(type, centerX, centerZ), Level.DIMENSION);
    }

    /** The value {@code level} declares, or null: the column records below hold null for a level that declares nothing. */
    private static <F extends IrisFeature> F levelValue(SettingsStack<F> declared, Level level) {
        return declared.level(level).orElse(null);
    }

    private boolean dimensionRequiresSurfaceSupport() {
        RequireSurfaceSupportSettings settings = dimensionLevel(RequireSurfaceSupportSettings.class);
        return settings == null ? new RequireSurfaceSupportSettings().require() : settings.require();
    }

    private int dimensionSurfaceSupportBuffer() {
        SurfaceSupportBufferSettings settings = dimensionLevel(SurfaceSupportBufferSettings.class);
        return settings == null ? new SurfaceSupportBufferSettings().buffer() : settings.buffer();
    }

    private boolean upperObjectsEnabled() {
        UpperObjectsSettings settings = dimensionLevel(UpperObjectsSettings.class);
        return settings != null && settings.enabled() && terrain.upperDimensionY(centerX, centerZ).isPresent();
    }

    private boolean upperObjectsForced() {
        UpperObjectsForceSettings settings = dimensionLevel(UpperObjectsForceSettings.class);
        return settings != null && settings.force();
    }

    private static List<ObjectPlacement> surfaceObjects(ObjectSettings settings) {
        return settings == null ? List.of() : settings.objects().stream().filter(p -> p.carvingSupport().supportsSurface()).toList();
    }

    private static List<ObjectPlacement> carvingObjects(ObjectSettings settings) {
        return settings == null ? List.of() : settings.objects().stream().filter(p -> p.carvingSupport().supportsCarving()).toList();
    }

    private CaveAnchors newAnchors() {
        return new CaveAnchors(chunk.carved(), terrain, worldHeight, caveLavaHeight);
    }

    private Tally placeObjects(Column column) {
        Tally tally = new Tally();
        CaveProfile biomeCaveProfile = caves.resolve(column.profiles().cave(), column.profiles().region());
        CaveProfile regionCaveProfile = caves.resolve(column.profiles().region(), column.profiles().cave());
        CaveAnchors anchors = newAnchors();
        placeSurfaceScope(tally, "biome-surface", surfaceObjects(column.objects().surface()), column.surface().toString());
        placeCaveScope(tally, "biome-cave", carvingObjects(column.objects().cave()), biomeCaveProfile, column.cave(), column.cave().toString(), anchors);
        placeSurfaceScope(tally, "region-surface", surfaceObjects(column.objects().region()), column.region().toString());
        placeCaveScope(tally, "region-cave", carvingObjects(column.objects().region()), regionCaveProfile, null, column.region().toString(), anchors);
        return tally;
    }

    private void placeSurfaceScope(Tally tally, String scope, List<ObjectPlacement> placements, String ownerName) {
        for (ObjectPlacement placement : placements) {
            tally.checked(scope);
            try {
                if (placement.isSeparated()) {
                    placeSeparatedObject(tally, placement, scope);
                    continue;
                }
                boolean passes = rng.chance(separation.chanceAt(placement, centerX, centerZ) + rng.d(-CHANCE_JITTER, CHANCE_JITTER));
                traceChance(scope, passes, placement);
                if (!passes) {
                    continue;
                }
                tally.triggered(scope);
                placeObject(tally, placement, scope);
            } catch (Throwable failure) {
                tally.errors++;
                logPlacementFailure("Failed to place objects in the following " + ownerKind(scope) + ": " + ownerName, placement, failure);
            }
        }
    }

    /** Places the one object the chunk's candidate stands for, when no closer candidate of a lower rank outranks it. */
    private void placeSeparatedObject(Tally tally, ObjectPlacement placement, String scope) {
        Optional<ObjectSeparation.Candidate> kept = separation.keptCandidate(chunkX, chunkZ, placement, context.settings());
        traceChance(scope, kept.isPresent(), placement);
        if (kept.isEmpty()) {
            return;
        }
        tally.triggered(scope);
        ObjectSeparation.Candidate candidate = kept.get();
        tally.attempts++;
        Optional<PlaceableObject> picked = objects.pickScaled(placement, candidate.rng());
        if (picked.isEmpty()) {
            tally.nullObjects++;
            traceNullObject(scope, 0, 1, placement);
            return;
        }
        placeOnSurfaceAt(tally, placement, scope, picked.get(), candidate.x(), candidate.z(), candidate.rng(), 0, 1);
    }

    private void placeCaveScope(Tally tally, String scope, List<ObjectPlacement> placements, CaveProfile profile,
                                ResourceId expectedCaveBiome, String ownerName, CaveAnchors anchors) {
        for (ObjectPlacement placement : placements) {
            tally.checked(scope);
            boolean passes = rng.chance(placement.chance());
            traceChance(scope, passes, placement);
            if (!passes) {
                continue;
            }
            tally.triggered(scope);
            try {
                placeCaveObject(tally, placement, profile, scope, expectedCaveBiome, anchors);
            } catch (Throwable failure) {
                tally.errors++;
                logPlacementFailure("Failed to place cave objects in the following " + ownerKind(scope) + ": " + ownerName, placement, failure);
            }
        }
    }

    private static String ownerKind(String scope) {
        return scope.startsWith("biome") ? "biome" : "region";
    }

    private void logPlacementFailure(String message, ObjectPlacement placement, Throwable failure) {
        log.error(message, failure);
        log.error("Object(s) " + String.join(", ", placement.place()) + " (" + failure.getClass().getSimpleName() + ").", null);
        log.error("Are these objects missing?", null);
    }

    private void placeObject(Tally tally, ObjectPlacement placement, String scope) {
        int blockX = context.originX();
        int blockZ = context.originZ();
        int density = objects.density(placement, rng, blockX, blockZ);
        for (int i = 0; i < density; i++) {
            tally.attempts++;
            Optional<PlaceableObject> picked = objects.pickScaled(placement, rng);
            if (picked.isEmpty()) {
                tally.nullObjects++;
                traceNullObject(scope, i, density, placement);
                continue;
            }
            int x = rng.i(blockX, blockX + CHUNK_WIDTH_BLOCKS);
            int z = rng.i(blockZ, blockZ + CHUNK_WIDTH_BLOCKS);
            placeOnSurfaceAt(tally, placement, scope, picked.get(), x, z, rng, i, density);
        }
    }

    private void placeOnSurfaceAt(Tally tally, ObjectPlacement placement, String scope, PlaceableObject object, int x, int z,
                                  RNG placingRng, int densityIndex, int density) {
        PlacementChanges changes = effective.forSurface(placement, object);
        boolean treePlacement = isTreePlacement(object, placement);
        int id = placingRng.i(0, Integer.MAX_VALUE);
        String goldenTag = golden ? scope + "/" + object.name() : null;
        if (golden) {
            log.debug("Goldendebug object attempt: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                    + " densityIndex=" + densityIndex + " xx=" + x + " zz=" + z + " mode=" + placement.mode());
        }
        try {
            PlacementTags tags = tags(object, id, "surface", treePlacement, placement);
            int result = chunk.placeOnSurface(object, x, z, placement, changes, tags, goldenTag);
            if (result >= 0) {
                tally.placed++;
            } else {
                tally.rejected++;
            }
            traceObjectResult(scope, object, result, x, z, densityIndex, density);
        } catch (Throwable failure) {
            tally.errors++;
            log.error("Regen object placement exception: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                    + " densityIndex=" + densityIndex + " density=" + density + " error=" + failure.getClass().getSimpleName() + ":" + failure.getMessage(), failure);
        }
    }

    private void placeCaveObject(Tally tally, ObjectPlacement placement, CaveProfile profile, String scope,
                                 ResourceId expectedCaveBiome, CaveAnchors anchors) {
        int minX = context.originX();
        int minZ = context.originZ();
        int density = objects.density(placement, rng, minX, minZ);
        CaveAnchors.Search search = caves.searchFor(placement, profile);
        for (int i = 0; i < density; i++) {
            tally.attempts++;
            Optional<PlaceableObject> picked = objects.pickScaled(placement, rng);
            if (picked.isEmpty()) {
                tally.nullObjects++;
                traceNullObject(scope, i, density, placement);
                CaveRejects.log(log,scope, "NULL_OBJECT", chunkX, chunkZ, placement, null, i, density, search, null, null);
                continue;
            }
            PlaceableObject object = picked.get();
            CaveAnchors.Anchor anchor = anchors.find(rng, minX, minZ, search, expectedCaveBiome, placement.underwater());
            if (anchor == null) {
                tally.rejected++;
                CaveRejects.log(log,scope, "NO_ANCHOR", chunkX, chunkZ, placement, object, i, density, search, null, null);
                continue;
            }
            int id = rng.i(0, Integer.MAX_VALUE);
            PlacementChanges changes = effective.forCave(placement, object, profile);
            try {
                PlacementTags tags = tags(object, id, "cave", isTreePlacement(object, placement), placement);
                CavePlacement contained = chunk.placeInCave(object, anchor.x(), anchor.y(), anchor.z(), placement, changes,
                        search.minDepthBelowSurface(), tags, null);
                tallyCave(tally, contained, scope, object, placement, i, density, search, anchor);
            } catch (Throwable failure) {
                tally.errors++;
                log.error("Regen cave object placement exception: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                        + " densityIndex=" + i + " density=" + density + " error=" + failure.getClass().getSimpleName() + ":" + failure.getMessage(), failure);
                CaveRejects.log(log,scope, "EXCEPTION", chunkX, chunkZ, placement, object, i, density, search, anchor.y(), failure);
            }
        }
    }

    private void tallyCave(Tally tally, CavePlacement contained, String scope, PlaceableObject object, ObjectPlacement placement,
                           int index, int density, CaveAnchors.Search search, CaveAnchors.Anchor anchor) {
        if (contained.wroteBlocks()) {
            tally.placed++;
        } else {
            tally.rejected++;
            CaveRejects.log(log,scope, rejectReason(contained), chunkX, chunkZ, placement, object, index, density, search, anchor.y(), null);
        }
        if (traceRegen) {
            log.debug("Regen cave object placement result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                    + " resultY=" + contained.resultY() + " anchorY=" + anchor.y() + " px=" + anchor.x() + " pz=" + anchor.z()
                    + " wroteBlocks=" + contained.wroteBlocks() + " densityIndex=" + index + " density=" + density);
        }
    }

    private static String rejectReason(CavePlacement contained) {
        if (contained.resultY() < 0) {
            return "PLACE_NEGATIVE";
        }
        return contained.commit() == TerrainTransaction.Commit.REJECTED ? "CONTAINMENT" : "NO_WRITES";
    }

    private void placeUpperObjects(Column column) {
        SettingsStack<ObjectSettings> upper = terrain.upperDimension()
                .map(dimension -> dimension.settings().at(ObjectSettings.class, centerX, centerZ))
                .orElse(SettingsStack.empty());
        Optional<Entry<ObjectSettings>> biome = levelEntry(upper, Level.BIOME);
        Optional<Entry<ObjectSettings>> region = levelEntry(upper, Level.REGION);
        if (biome.isEmpty() && region.isEmpty()) {
            return;
        }
        boolean forcePlace = upperObjectsForced();
        biome.ifPresent(declared -> placeUpperScope(declared, "upper-biome-surface", "biome", forcePlace));
        region.ifPresent(declared -> placeUpperScope(declared, "upper-region-surface", "region", forcePlace));
    }

    private static Optional<Entry<ObjectSettings>> levelEntry(SettingsStack<ObjectSettings> declared, Level level) {
        return declared.entries().stream().filter(entry -> entry.level() == level).findFirst();
    }

    private void placeUpperScope(Entry<ObjectSettings> declared, String scope, String kind, boolean forcePlace) {
        String ownerKey = declared.source().toString();
        for (ObjectPlacement placement : surfaceObjects(declared.value())) {
            if (!rng.chance(placement.chance() + rng.d(-CHANCE_JITTER, CHANCE_JITTER))) {
                continue;
            }
            try {
                placeUpperObject(placement, forcePlace, scope);
            } catch (Throwable failure) {
                log.error("Failed to place upper-dimension objects in " + kind + " " + ownerKey + ": " + String.join(", ", placement.place())
                        + " (" + failure.getClass().getSimpleName() + ")", failure);
            }
        }
    }

    private void placeUpperObject(ObjectPlacement placement, boolean forcePlace, String scope) {
        int minX = context.originX();
        int minZ = context.originZ();
        int density = objects.density(placement, rng, minX, minZ);
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = objects.pickScaled(placement, rng);
            if (picked.isEmpty()) {
                continue;
            }
            PlaceableObject object = picked.get();
            int x = rng.i(minX, minX + CHUNK_WIDTH_BLOCKS);
            int z = rng.i(minZ, minZ + CHUNK_WIDTH_BLOCKS);
            OptionalInt upperSurface = terrain.upperDimensionY(x, z);
            if (upperSurface.isEmpty() || upperSurface.getAsInt() >= worldHeight - UPPER_SURFACE_MARGIN_BLOCKS) {
                continue;
            }
            int anchorY = upperSurface.getAsInt() - 1 - Math.floorDiv(object.height(), 2);
            if (anchorY <= MINIMUM_UPPER_ANCHOR_Y) {
                continue;
            }
            int id = rng.i(0, Integer.MAX_VALUE);
            PlacementChanges changes = PlacementChanges.NONE.withPlace(List.of(String.valueOf(object.name())))
                    .withMode(ObjectPlaceMode.CENTER_HEIGHT)
                    .withUpsideDown(true).withCarvingSupport(CarvingMode.ANYWHERE);
            if (forcePlace) {
                changes = changes.withForcePlace(true);
            }
            PlacementTags tags = tags(object, id, "upper", isTreePlacement(object, placement), placement);
            int result = chunk.placeUpper(object, x, anchorY, z, placement, changes, tags);
            if (traceRegen) {
                log.debug("Upper object placement: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name() + " anchorY="
                        + anchorY + " upperSurfaceY=" + upperSurface.getAsInt() + " resultY=" + result + " forcePlace=" + forcePlace);
            }
        }
    }

    private PlacementTags tags(PlaceableObject object, int id, String context, boolean treePlacement, ObjectPlacement placement) {
        boolean buriedTreasure = placement.isDolphinTarget() && placement.underwater();
        return new PlacementTags(placementMarker(object, id, context), treePlacement, buriedTreasure);
    }

    private PlacementRecord placementMarker(PlaceableObject object, int id, String context) {
        String key = object == null ? null : object.name();
        if (key == null || key.isEmpty() || key.equals("null")) {
            String fingerprint = context + "|" + (object == null ? "<null>" : object.getClass().getSimpleName());
            log.warnOnce(fingerprint, "Skipping placement marker write: the object has no loadKey (context=" + context + "). "
                    + "This would previously produce 'Couldn't find Object: null' warnings on chunk reload.");
            return null;
        }
        return PlacementRecord.of(this.context.world().resources().idOf(key), id);
    }

    /** Objects in {@code trees/} are trees, procedural ones in {@code procedural/trees/} too, and so is anything a placement grows from saplings. */
    private static boolean isTreePlacement(PlaceableObject object, ObjectPlacement placement) {
        String key = object == null ? null : object.name();
        if (key != null && ProceduralObjectCatalog.isProceduralKey(key)) {
            key = key.substring(ProceduralObjectCatalog.KEY_PREFIX.length());
        }
        return (key != null && key.startsWith(TREE_FOLDER)) || (placement != null && !placement.trees().isEmpty());
    }

    private void traceChance(String scope, boolean passes, ObjectPlacement placement) {
        if (!traceRegen) {
            return;
        }
        log.debug("Regen object placer chance: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " chanceResult=" + passes
                + " chanceBase=" + placement.chance() + " densityMid=" + placement.middleDensity() + " objects=" + placement.place().size());
    }

    private void traceNullObject(String scope, int index, int density, ObjectPlacement placement) {
        if (!traceRegen) {
            return;
        }
        log.debug("Regen object placement null object: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " densityIndex=" + index
                + " density=" + density + " placementKeys=" + String.join(",", placement.place()));
    }

    private void traceObjectResult(String scope, PlaceableObject object, int result, int x, int z, int index, int density) {
        if (golden) {
            log.debug("Goldendebug object result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                    + " resultY=" + result + " fallback=surface");
        }
        if (traceRegen) {
            log.debug("Regen object placement result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.name()
                    + " resultY=" + result + " px=" + x + " pz=" + z + " fallback=surface densityIndex=" + index + " density=" + density);
        }
    }

    private void logCaveDiagnostics(Column column) {
        int carvedBlocks = 0;
        int maxY = Math.min(worldHeight - 1, column.surfaceY() - DIAGNOSTIC_SURFACE_MARGIN);
        for (int y = 1; y < maxY; y++) {
            if (chunk.carved().isCarved(centerX, y, centerZ)) {
                carvedBlocks++;
            }
        }
        log.debug("Cave object diag: chunk=" + chunkX + "," + chunkZ + " surfaceBiome=" + column.surface() + " caveBiome=" + column.cave()
                + " surfaceY=" + column.surfaceY() + " maxAnchorY=" + maxY + " carvedAtCenter=" + carvedBlocks
                + " biomeCarvingObjects=" + carvingObjects(column.objects().cave()).size()
                + " regionCarvingObjects=" + carvingObjects(column.objects().region()).size()
                + " sameBiome=" + column.cave().equals(column.surface()));
    }

    private void logLayerStart(Column column) {
        log.debug("Regen object layer start: chunk=" + chunkX + "," + chunkZ + " surfaceBiome=" + column.surface() + " caveBiome=" + column.cave()
                + " region=" + column.region() + " biomeSurfacePlacers=" + surfaceObjects(column.objects().surface()).size()
                + " biomeCavePlacers=" + carvingObjects(column.objects().cave()).size()
                + " regionSurfacePlacers=" + surfaceObjects(column.objects().region()).size()
                + " regionCavePlacers=" + carvingObjects(column.objects().region()).size());
    }

    private void logLayerDone(Tally tally) {
        log.debug("Regen object layer done: chunk=" + chunkX + "," + chunkZ + " " + tally.describe()
                + " objectAttempts=" + tally.attempts + " objectPlaced=" + tally.placed + " objectRejected=" + tally.rejected
                + " objectNull=" + tally.nullObjects + " objectErrors=" + tally.errors);
    }

    private record Column(ResourceId region, ResourceId surface, ResourceId cave, int surfaceY, ColumnObjects objects,
                          ColumnProfiles profiles) {
    }

    private record ColumnObjects(ObjectSettings surface, ObjectSettings cave, ObjectSettings region) {
    }

    private record ColumnProfiles(CaveProfile surface, CaveProfile cave, CaveProfile region) {
    }

    /** What placing the objects of a chunk came to. */
    private static final class Tally {
        private final java.util.Map<String, int[]> scopes = new java.util.LinkedHashMap<>();
        private int attempts;
        private int placed;
        private int rejected;
        private int nullObjects;
        private int errors;

        void checked(String scope) {
            scopes.computeIfAbsent(scope, key -> new int[2])[0]++;
        }

        void triggered(String scope) {
            scopes.computeIfAbsent(scope, key -> new int[2])[1]++;
        }

        String describe() {
            StringBuilder text = new StringBuilder();
            scopes.forEach((scope, counts) -> text.append(scope).append("PlacersChecked=").append(counts[0])
                    .append(' ').append(scope).append("PlacersTriggered=").append(counts[1]).append(' '));
            return text.toString();
        }
    }
}
