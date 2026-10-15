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
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.objects.CavePlacement;
import art.arcane.iris.builtin.objects.ObjectChunk;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.PlacementChanges;
import art.arcane.iris.builtin.objects.PlacementSource;
import art.arcane.iris.builtin.objects.PlacementTags;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.objects.RequireSurfaceSupportSettings;
import art.arcane.iris.builtin.objects.SurfaceSupportBufferSettings;
import art.arcane.iris.builtin.objects.UpperObjectsForceSettings;
import art.arcane.iris.builtin.objects.UpperObjectsSettings;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralObjects;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.TerrainSampler;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Places what the objects of one chunk's region and biomes say: the objects of its surface, caves and the terrain stacked above,
 * and its procedural objects, in that order, all drawing from the one random of the chunk.
 */
final class ChunkObjectPlacing {
    private static final int CENTER_OFFSET_BLOCKS = 8;
    private static final int CHUNK_SHIFT = 4;
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

    private final ObjectHost host;
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

    ChunkObjectPlacing(ObjectHost host, ModuleLog log, MantleStageContext context) {
        this.host = host;
        this.log = log;
        this.context = context;
        this.chunk = host.chunk();
        this.terrain = context.terrain();
        this.chunkX = context.originX() >> CHUNK_SHIFT;
        this.chunkZ = context.originZ() >> CHUNK_SHIFT;
        this.centerX = CENTER_OFFSET_BLOCKS + (chunkX << CHUNK_SHIFT);
        this.centerZ = CENTER_OFFSET_BLOCKS + (chunkZ << CHUNK_SHIFT);
        this.rng = host.chunkRandom(chunkX, chunkZ);
        this.traceRegen = Thread.currentThread().getName().startsWith(REGEN_THREAD_PREFIX) && host.debugging();
        this.golden = chunk.goldenDebug(chunkX, chunkZ);
        this.worldHeight = context.mantle().bounds().maxY();
        this.caveLavaHeight = host.caveLavaHeight();
        this.effective = new EffectivePlacements(dimensionRequiresSurfaceSupport(), dimensionSurfaceSupportBuffer());
        this.caves = new CaveSettings(dimensionLevel(CaveProfile.class));
    }

    /** Places what originates in this chunk. */
    void place() {
        Column column = readColumn();
        if (host.debugging() && (chunkX & DIAGNOSTIC_CHUNK_MASK) == 0 && (chunkZ & DIAGNOSTIC_CHUNK_MASK) == 0) {
            logCaveDiagnostics(column);
        }
        if (traceRegen) {
            logLayerStart(column);
        }
        Tally tally = placeObjects(column);
        placeProceduralObjects(column);
        if (upperObjectsEnabled()) {
            placeUpperObjects(column);
        }
        if (traceRegen) {
            logLayerDone(tally);
        }
    }

    /** The region and biomes at the chunk's centre and what each declares. */
    private Column readColumn() {
        ResourceId region = host.regionAt(centerX, centerZ);
        ResourceId surface = host.surfaceBiomeAt(centerX, centerZ);
        int surfaceY = surfaceY(centerX, centerZ);
        ResourceId cave = resolveCaveObjectBiome(surfaceY, surface);
        return new Column(region, surface, cave, surfaceY, objectsOf(cave, region, surface), proceduralOf(cave, region, surface),
                profilesOf(cave, region, surface));
    }

    private int surfaceY(int x, int z) {
        return (int) Math.round(terrain.surfaceY(x, z));
    }

    private ResourceId resolveCaveObjectBiome(int surfaceY, ResourceId surfaceBiome) {
        ResourceId legacy = caveBiomeAt(Math.max(1, surfaceY - LEGACY_CAVE_SAMPLE_DEPTH)).orElse(surfaceBiome);
        ResourceId ladderChoice = null;
        for (int depth : CAVE_BIOME_SAMPLE_DEPTHS) {
            Optional<ResourceId> sampled = caveBiomeAt(Math.max(1, surfaceY - depth));
            if (sampled.isEmpty() || sampled.get().equals(surfaceBiome)) {
                continue;
            }
            if (hasCarvingObjects(host.biomeDeclaration(sampled.get(), ObjectSettings.class))) {
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
        List<Entry<ObjectSettings>> entries = context.settings().at(ObjectSettings.class, centerX, centerZ);
        return new ColumnObjects(level(entries, Target.BIOME), caveSettings(cave, surface, entries, ObjectSettings.class), level(entries, Target.REGION));
    }

    private ColumnProcedural proceduralOf(ResourceId cave, ResourceId region, ResourceId surface) {
        List<Entry<ProceduralObjectSettings>> entries = context.settings().at(ProceduralObjectSettings.class, centerX, centerZ);
        return new ColumnProcedural(level(entries, Target.BIOME),
                caveSettings(cave, surface, entries, ProceduralObjectSettings.class), level(entries, Target.REGION));
    }

    private ColumnProfiles profilesOf(ResourceId cave, ResourceId region, ResourceId surface) {
        List<Entry<CaveProfile>> entries = context.settings().at(CaveProfile.class, centerX, centerZ);
        return new ColumnProfiles(level(entries, Target.BIOME), caveSettings(cave, surface, entries, CaveProfile.class), level(entries, Target.REGION));
    }

    private <F extends IrisFeature> F caveSettings(ResourceId cave, ResourceId surface, List<Entry<F>> entries, Class<F> type) {
        if (cave.equals(surface)) {
            return level(entries, Target.BIOME);
        }
        return host.biomeDeclaration(cave, type).orElse(null);
    }

    private <F extends IrisFeature> F dimensionLevel(Class<F> type) {
        return level(context.settings().at(type, centerX, centerZ), Target.DIMENSION);
    }

    private static <F extends IrisFeature> F level(List<Entry<F>> entries, Target level) {
        for (Entry<F> entry : entries) {
            if (entry.level() == level) {
                return entry.value();
            }
        }
        return null;
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
            boolean passes = rng.chance(placement.chance() + rng.d(-CHANCE_JITTER, CHANCE_JITTER));
            traceChance(scope, passes, placement);
            if (!passes) {
                continue;
            }
            tally.triggered(scope);
            try {
                placeObject(tally, placement, scope);
            } catch (Throwable failure) {
                tally.errors++;
                logPlacementFailure("Failed to place objects in the following " + ownerKind(scope) + ": " + ownerName, placement, failure);
            }
        }
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
        int blockX = chunkX << CHUNK_SHIFT;
        int blockZ = chunkZ << CHUNK_SHIFT;
        int density = host.density(placement, rng, blockX, blockZ);
        for (int i = 0; i < density; i++) {
            tally.attempts++;
            Optional<PlaceableObject> picked = host.pickScaled(placement, rng);
            if (picked.isEmpty()) {
                tally.nullObjects++;
                traceNullObject(scope, i, density, placement);
                continue;
            }
            PlaceableObject object = picked.get();
            int x = rng.i(blockX, blockX + CHUNK_WIDTH_BLOCKS);
            int z = rng.i(blockZ, blockZ + CHUNK_WIDTH_BLOCKS);
            PlacementChanges changes = effective.forSurface(placement, object);
            boolean treePlacement = isTreePlacement(object, placement);
            int id = rng.i(0, Integer.MAX_VALUE);
            String goldenTag = golden ? scope + "/" + object.key() : null;
            if (golden) {
                log.debug("Goldendebug object attempt: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
                        + " densityIndex=" + i + " xx=" + x + " zz=" + z + " mode=" + placement.mode());
            }
            try {
                PlacementTags tags = tags(object, id, "surface", treePlacement, placement);
                int result = chunk.placeOnSurface(object, x, z, placement, changes, tags, rng, goldenTag);
                if (result >= 0) {
                    tally.placed++;
                } else {
                    tally.rejected++;
                }
                traceObjectResult(scope, object, result, x, z, i, density);
            } catch (Throwable failure) {
                tally.errors++;
                log.error("Regen object placement exception: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
                        + " densityIndex=" + i + " density=" + density + " error=" + failure.getClass().getSimpleName() + ":" + failure.getMessage(), failure);
            }
        }
    }

    private void placeCaveObject(Tally tally, ObjectPlacement placement, CaveProfile profile, String scope,
                                 ResourceId expectedCaveBiome, CaveAnchors anchors) {
        int minX = chunkX << CHUNK_SHIFT;
        int minZ = chunkZ << CHUNK_SHIFT;
        int density = host.density(placement, rng, minX, minZ);
        CaveAnchors.Search search = caves.searchFor(placement, profile);
        for (int i = 0; i < density; i++) {
            tally.attempts++;
            Optional<PlaceableObject> picked = host.pickScaled(placement, rng);
            if (picked.isEmpty()) {
                tally.nullObjects++;
                traceNullObject(scope, i, density, placement);
                CaveRejects.log(host, log, scope, "NULL_OBJECT", chunkX, chunkZ, placement, null, i, density, search, null, null);
                continue;
            }
            PlaceableObject object = picked.get();
            CaveAnchors.Anchor anchor = anchors.find(rng, minX, minZ, search, expectedCaveBiome, placement.underwater());
            if (anchor == null) {
                tally.rejected++;
                CaveRejects.log(host, log, scope, "NO_ANCHOR", chunkX, chunkZ, placement, object, i, density, search, null, null);
                continue;
            }
            int id = rng.i(0, Integer.MAX_VALUE);
            PlacementChanges changes = effective.forCave(placement, object, profile);
            try {
                PlacementTags tags = tags(object, id, "cave", isTreePlacement(object, placement), placement);
                CavePlacement contained = chunk.placeInCave(object, anchor.x(), anchor.y(), anchor.z(), placement, changes,
                        search.minDepthBelowSurface(), tags, rng, null);
                tallyCave(tally, contained, scope, object, placement, i, density, search, anchor);
            } catch (Throwable failure) {
                tally.errors++;
                log.error("Regen cave object placement exception: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
                        + " densityIndex=" + i + " density=" + density + " error=" + failure.getClass().getSimpleName() + ":" + failure.getMessage(), failure);
                CaveRejects.log(host, log, scope, "EXCEPTION", chunkX, chunkZ, placement, object, i, density, search, anchor.y(), failure);
            }
        }
    }

    private void tallyCave(Tally tally, CavePlacement contained, String scope, PlaceableObject object, ObjectPlacement placement,
                           int index, int density, CaveAnchors.Search search, CaveAnchors.Anchor anchor) {
        if (contained.wroteBlocks()) {
            tally.placed++;
        } else {
            tally.rejected++;
            CaveRejects.log(host, log, scope, rejectReason(contained), chunkX, chunkZ, placement, object, index, density, search, anchor.y(), null);
        }
        if (traceRegen) {
            log.debug("Regen cave object placement result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
                    + " resultY=" + contained.resultY() + " anchorY=" + anchor.y() + " px=" + anchor.x() + " pz=" + anchor.z()
                    + " wroteBlocks=" + contained.wroteBlocks() + " densityIndex=" + index + " density=" + density);
        }
    }

    private static String rejectReason(CavePlacement contained) {
        if (contained.resultY() < 0) {
            return "PLACE_NEGATIVE";
        }
        return contained.commit() == CavePlacement.Commit.REJECTED_BOUNDS ? "CONTAINMENT" : "NO_WRITES";
    }

    private void placeProceduralObjects(Column column) {
        List<ProceduralPlacement> surface = proceduralPlacements(column.procedural().surface());
        List<ProceduralPlacement> region = proceduralPlacements(column.procedural().region());
        CaveProfile surfaceProfile = caves.resolve(column.profiles().surface(), column.profiles().region());
        CaveProfile regionProfile = caves.resolve(column.profiles().region(), column.profiles().cave());
        placeProceduralFrom(surface, column.surface().toString(), surfaceProfile, column.surface());
        placeProceduralFrom(region, column.region().toString(), regionProfile, null);
        if (!column.cave().equals(column.surface())) {
            CaveProfile caveProfile = caves.resolve(column.profiles().cave(), column.profiles().region());
            placeProceduralFrom(proceduralPlacements(column.procedural().cave()), column.cave().toString(), caveProfile, column.cave());
        }
    }

    private List<ProceduralPlacement> proceduralPlacements(ProceduralObjectSettings settings) {
        return settings == null ? List.of() : ResolvedProceduralObjects.of(host, settings);
    }

    private void placeProceduralFrom(List<ProceduralPlacement> placements, String scope, CaveProfile caveProfile, ResourceId expectedCaveBiome) {
        if (placements.isEmpty()) {
            return;
        }
        int blockX = chunkX << CHUNK_SHIFT;
        int blockZ = chunkZ << CHUNK_SHIFT;
        CaveAnchors anchors = newAnchors();
        for (ProceduralPlacement placement : placements) {
            boolean chancePassed = passesProceduralChance(rng, placement.chance());
            if (golden) {
                log.debug("Goldendebug procedural chance: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " placement=" + placement.name() + " passed=" + chancePassed);
            }
            if (!chancePassed) {
                continue;
            }
            placeProcedural(placement, scope, caveProfile, expectedCaveBiome, anchors, blockX, blockZ);
        }
    }

    private void placeProcedural(ProceduralPlacement placement, String scope, CaveProfile caveProfile, ResourceId expectedCaveBiome,
                                 CaveAnchors anchors, int blockX, int blockZ) {
        boolean carving = placement.carvingSupport() == CarvingMode.CARVING_ONLY;
        CaveAnchors.Search search = caves.searchFor(placement, caveProfile);
        int density = Math.max(1, placement.density());
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = placement.variant(rng);
            if (picked.isEmpty()) {
                if (golden) {
                    log.debug("Goldendebug procedural pick: chunk=" + chunkX + "," + chunkZ + " placement=" + placement.name() + " densityIndex=" + i + " variant=null");
                }
                continue;
            }
            PlaceableObject variant = picked.get();
            CaveAnchors.Anchor anchor = carving ? anchors.find(rng, blockX, blockZ, search, expectedCaveBiome, placement.underwater()) : null;
            if (carving && anchor == null) {
                if (golden) {
                    log.debug("Goldendebug procedural cave anchor rejected: chunk=" + chunkX + "," + chunkZ + " placement=" + placement.name()
                            + " minDepthBelowSurface=" + search.minDepthBelowSurface());
                }
                continue;
            }
            int x = anchor == null ? rng.i(blockX, blockX + CHUNK_WIDTH_BLOCKS) : anchor.x();
            int z = anchor == null ? rng.i(blockZ, blockZ + CHUNK_WIDTH_BLOCKS) : anchor.z();
            int id = rng.i(0, Integer.MAX_VALUE);
            if (golden) {
                logProceduralPick(placement, variant, i, x, z, carving);
            }
            try {
                placeProceduralVariant(placement, variant, scope, caveProfile, search, anchor, x, z, id);
            } catch (Throwable failure) {
                log.error("Failed to place procedural object '" + placement.name() + "' in " + scope, failure);
            }
        }
    }

    private void placeProceduralVariant(ProceduralPlacement placement, PlaceableObject variant, String scope, CaveProfile caveProfile,
                                        CaveAnchors.Search search, CaveAnchors.Anchor anchor, int x, int z, int id) {
        String goldenTag = golden ? scope + "/" + placement.name() : null;
        PlacementTags tags = tags(variant, id, "procedural", placement.tree(), false);
        int resultY;
        String commit = "n/a";
        if (anchor != null) {
            PlacementChanges changes = effective.forCave(placement, variant, caveProfile);
            CavePlacement contained = chunk.placeInCave(variant, x, anchor.y(), z, placement, changes, search.minDepthBelowSurface(), tags, rng, goldenTag);
            resultY = contained.resultY();
            commit = contained.commit().toString();
        } else {
            resultY = chunk.placeOnSurface(variant, x, z, placement, PlacementChanges.NONE, tags, rng, goldenTag);
        }
        if (golden) {
            log.debug("Goldendebug procedural result: chunk=" + chunkX + "," + chunkZ + " placement=" + placement.name()
                    + " variant=" + variant.key() + " xx=" + x + " zz=" + z + " resultY=" + resultY + " commit=" + commit);
        }
    }

    private void logProceduralPick(ProceduralPlacement placement, PlaceableObject variant, int index, int x, int z, boolean carving) {
        List<PlaceableObject> pool = placement.variants();
        log.debug("Goldendebug procedural pick: chunk=" + chunkX + "," + chunkZ + " placement=" + placement.name() + " densityIndex=" + index
                + " variant=" + variant.key() + " variantIndex=" + pool.indexOf(variant) + "/" + pool.size() + " xx=" + x + " zz=" + z
                + " mode=" + placement.mode() + " carving=" + carving);
    }

    static boolean passesProceduralChance(RNG rng, double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 1.0) {
            return true;
        }
        return rng.chance(Math.max(0.0, Math.min(1.0, chance + rng.d(-CHANCE_JITTER, CHANCE_JITTER))));
    }

    private void placeUpperObjects(Column column) {
        Optional<ObjectHost.UpperObjects> upper = host.upperObjects(centerX, centerZ);
        if (upper.isEmpty() || (upper.get().biome() == null && upper.get().region() == null)) {
            return;
        }
        boolean forcePlace = upperObjectsForced();
        placeUpperScope(upper.get().biome(), upper.get().biomeKey(), "upper-biome-surface", "biome", forcePlace);
        placeUpperScope(upper.get().region(), upper.get().regionKey(), "upper-region-surface", "region", forcePlace);
    }

    private void placeUpperScope(ObjectSettings settings, String ownerKey, String scope, String kind, boolean forcePlace) {
        for (ObjectPlacement placement : surfaceObjects(settings)) {
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
        int minX = chunkX << CHUNK_SHIFT;
        int minZ = chunkZ << CHUNK_SHIFT;
        int density = host.density(placement, rng, minX, minZ);
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = host.pickScaled(placement, rng);
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
            PlacementChanges changes = PlacementChanges.NONE.withPlace(List.of(String.valueOf(object.key())))
                    .withMode(ObjectPlaceMode.CENTER_HEIGHT)
                    .withUpsideDown(true).withCarvingSupport(CarvingMode.ANYWHERE);
            if (forcePlace) {
                changes = changes.withForcePlace(true);
            }
            PlacementTags tags = tags(object, id, "upper", isTreePlacement(object, placement), placement);
            int result = chunk.placeUpper(object, x, anchorY, z, placement, changes, tags, rng);
            if (traceRegen) {
                log.debug("Upper object placement: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key() + " anchorY="
                        + anchorY + " upperSurfaceY=" + upperSurface.getAsInt() + " resultY=" + result + " forcePlace=" + forcePlace);
            }
        }
    }

    private PlacementTags tags(PlaceableObject object, int id, String context, boolean treePlacement, PlacementSource placement) {
        return tags(object, id, context, treePlacement, placement.isDolphinTarget() && placement.underwater());
    }

    private PlacementTags tags(PlaceableObject object, int id, String context, boolean treePlacement, boolean buriedTreasure) {
        return new PlacementTags(placementMarker(object, id, context), treePlacement, buriedTreasure);
    }

    private String placementMarker(PlaceableObject object, int id, String context) {
        String key = object == null ? null : object.key();
        if (key == null || key.isEmpty() || key.equals("null")) {
            String fingerprint = context + "|" + (object == null ? "<null>" : object.getClass().getSimpleName());
            log.warnOnce(fingerprint, "Skipping placement marker write: the object has no loadKey (context=" + context + "). "
                    + "This would previously produce 'Couldn't find Object: null' warnings on chunk reload.");
            return null;
        }
        return key + "@" + id;
    }

    private static boolean isTreePlacement(PlaceableObject object, ObjectPlacement placement) {
        String key = object == null ? null : object.key();
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
            log.debug("Goldendebug object result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
                    + " resultY=" + result + " fallback=surface");
        }
        if (traceRegen) {
            log.debug("Regen object placement result: chunk=" + chunkX + "," + chunkZ + " scope=" + scope + " object=" + object.key()
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
                          ColumnProcedural procedural, ColumnProfiles profiles) {
    }

    private record ColumnObjects(ObjectSettings surface, ObjectSettings cave, ObjectSettings region) {
    }

    private record ColumnProcedural(ProceduralObjectSettings surface, ProceduralObjectSettings cave, ProceduralObjectSettings region) {
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
