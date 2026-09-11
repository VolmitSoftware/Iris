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

package art.arcane.iris.modded;

import net.minecraft.world.level.chunk.status.ChunkStatus;

import art.arcane.iris.nativegen.NativeGenerationWriteGuard;

import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.framework.NativeStructureOwnershipStore;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.engine.object.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.IrisStaticObjectLayer;
import art.arcane.iris.nativegen.NativeStructureGenerationException;
import art.arcane.iris.nativegen.NativeStructureLocatePersistence;
import art.arcane.iris.nativegen.NativeStructureLocateResults;
import art.arcane.iris.nativegen.NativeStructureOwnershipRecovery;
import art.arcane.iris.nativegen.NativeStructurePostProcessor;
import art.arcane.iris.nativegen.NativeStructureReferenceEnvelope;
import art.arcane.iris.nativegen.NativeStructureSurfaceFitter;
import art.arcane.iris.nativegen.NativeStructureTerrainIntegrator;
import art.arcane.iris.nativegen.NativeStructureVegetationClearer;
import art.arcane.iris.nativegen.NativeStructureVerticalPlacer;
import art.arcane.iris.nativegen.NativeStructureVanillaLocator;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

/**
 * Native (vanilla registry) structure stage for {@link IrisModdedChunkGenerator}. The generator keeps the
 * {@link net.minecraft.world.level.chunk.ChunkGenerator} overrides because they issue {@code super} calls;
 * everything they do beyond that lives here.
 */
final class ModdedNativeStructureStage {
    private static final int WORLD_CHECK_SHIFT_RECORD_LIMIT = 4096;
    private static final boolean WORLD_CHECK_ENABLED = Boolean.getBoolean("iris.worldcheck");

    private final IrisModdedChunkGenerator generator;
    private final ConcurrentHashMap<NativeStructureStartKey, Integer> worldCheckStructureShifts = new ConcurrentHashMap<>();
    private volatile StructureStepCache structureStepCache;

    ModdedNativeStructureStage(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    void installVolumeIndex(ServerLevel level, Engine engine) {
        WeakReference<ServerLevel> levelReference = new WeakReference<>(level);
        WeakReference<ChunkGenerator> generatorReference = new WeakReference<>(generator);
        WeakReference<BiomeSource> biomeSourceReference = new WeakReference<>(generator.structureBiomeSource);
        NativeStructureVolumeIndex.install(engine, new NativeStructureVolumeIndex.Context(
                level.registryAccess(),
                level.getServer().getStructureManager(),
                level.dimension(),
                LevelHeightAccessor.create(level.getMinY(), level.getHeight()),
                generatorReference::get,
                biomeSourceReference::get,
                () -> {
                    ServerLevel active = levelReference.get();
                    return active == null ? null : active.getChunkSource().getGeneratorState();
                }));
    }

    Pair<BlockPos, Holder<Structure>> findNearestIrisStructure(ServerLevel level,
                                                              HolderSet<Structure> holders,
                                                              BlockPos pos, int radius, boolean findUnexplored,
                                                              Engine current,
                                                              NativeStructureVanillaLocator.Candidate nativeCandidate) {
        Pair<BlockPos, Holder<Structure>> nativeLocated =
                nativeCandidate == null ? null : nativeCandidate.result();
        Runnable nativeReference = () -> {
            if (nativeCandidate != null) {
                nativeCandidate.reference(level.structureManager());
            }
        };
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<IrisNativeLocateSearch> searches = new ArrayList<>(holders.size());
        NativeStructureLocatePersistence.ProbeBudget budget = NativeStructureLocatePersistence.probeBudget();
        for (Holder<Structure> holder : holders) {
            Identifier id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure locate received an unregistered structure holder");
            }
            String structureId = id.toString();
            if (!IrisStructureLocator.hasNativePlacement(current, structureId)) {
                continue;
            }
            NativeStructureLocatePersistence.Probe probe = NativeStructureLocatePersistence.probe(
                    level, holder.value(), findUnexplored, budget);
            searches.add(new IrisNativeLocateSearch(
                    holder, structureId, NativeStructureLocatePersistence.search(
                    current, structureId, pos.getX(), pos.getZ(), radius, probe)));
        }
        searches.sort(Comparator.comparing(IrisNativeLocateSearch::structureId));
        for (int attempt = 0; attempt < NativeStructureLocatePersistence.MAX_SELECTED_CANDIDATE_RETRIES; attempt++) {
            IrisNativeLocateSearch bestSearch = null;
            IrisStructureLocator.LocateResult bestResult = null;
            long bestDistance = Long.MAX_VALUE;
            for (IrisNativeLocateSearch search : searches) {
                IrisStructureLocator.LocateResult result = search.search().predict();
                if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                    throw new IllegalStateException("Iris structure locate reached its safety limit for "
                            + search.structureId() + " within " + radius + " placement rings");
                }
                if (!result.found()) {
                    continue;
                }
                long dx = (long) result.originX() - pos.getX();
                long dz = (long) result.originZ() - pos.getZ();
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSearch = search;
                    bestResult = result;
                }
            }
            if (bestSearch == null) {
                return NativeStructureLocateResults.selectAndReference(
                        pos, null, () -> { }, nativeLocated, nativeReference);
            }
            Pair<BlockPos, Holder<Structure>> predicted = Pair.of(
                    new BlockPos(bestResult.originX(), bestResult.baseY(), bestResult.originZ()),
                    bestSearch.holder());
            if (NativeStructureLocateResults.nearest(pos, predicted, nativeLocated) != predicted) {
                return NativeStructureLocateResults.selectAndReference(
                        pos, predicted, () -> { }, nativeLocated, nativeReference);
            }
            NativeStructureLocatePersistence.VerifiedStart verified =
                    bestSearch.search().verify(bestResult);
            if (verified == null) {
                bestSearch.search().reject(bestResult);
                continue;
            }
            BlockPos located = new BlockPos(
                    bestResult.originX(), verified.ownership().locatorY(),
                    bestResult.originZ());
            Pair<BlockPos, Holder<Structure>> irisLocated = Pair.of(located, bestSearch.holder());
            IrisNativeLocateSearch selectedSearch = bestSearch;
            NativeStructureLocatePersistence.VerifiedStart selectedStart = verified;
            return NativeStructureLocateResults.selectAndReference(
                    pos, irisLocated, () -> selectedSearch.search().reference(selectedStart),
                    nativeLocated, nativeReference);
        }
        throw new IllegalStateException("Iris structure locate rejected too many selected candidates within "
                + radius + " placement rings");
    }

    HolderSet<Structure> filterReachableNativeStructures(ServerLevel level, HolderSet<Structure> holders,
                                                        Engine current) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> kept = new ArrayList<>(holders.size());
        for (Holder<Structure> holder : holders) {
            Identifier id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure filtering received an unregistered structure holder");
            }
            String key = id.toString();
            IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(current,
                    key, NativeStructureVegetationClearer.isUndergroundStep(holder.value().step()));
            if (!decision.generate() || !generator.structureBiomeSource.isStructureReachable(holder)) {
                continue;
            }
            kept.add(holder);
        }
        return kept.size() == holders.size() ? holders : HolderSet.direct(kept);
    }

    void adjustGeneratedStructures(RegistryAccess registryAccess, ChunkAccess chunk,
                                   Map<Structure, StructureStart> previousStarts,
                                   Map<Structure, NativeStructureStartPlan> configuredStarts,
                                   Engine current,
                                   StructureTemplateManager templateManager) {
        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = chunk.getPos();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid() || previousStarts.get(structure) == start) {
                continue;
            }
            Identifier id = registry.getKey(structure);
            String structureId = id == null ? null : id.toString();
            if (structureId == null) {
                throw NativeStructureGenerationException.failure(
                        "resolution", null, chunkPos.x(), chunkPos.z());
            }
            BoundingBox footprint = start.getBoundingBox();
            if (!current.getComplex().allowsNewGenerationFootprint(
                    footprint.minX(),
                    footprint.minZ(),
                    footprint.maxX(),
                    footprint.maxZ())) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                if (configuredStarts.containsKey(structure)) {
                    NativeStructureOwnershipStore.discard(
                            current, structureId, chunkPos.x(), chunkPos.z());
                }
                continue;
            }
            if (configuredStarts.containsKey(structure)) {
                recordWorldCheckStructureShift(
                        configuredStarts.get(structure).source().getStructure(), start.getChunkPos(), 0);
                continue;
            }
            boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            IrisNativeStructureDecision decision;
            try {
                decision = NativeStructureGenerationPolicy.resolve(current,
                        structureId, undergroundStep);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "policy resolution", structureId, chunkPos.x(), chunkPos.z(), error);
            }
            if (!decision.generate()) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            int offsetY;
            try {
                offsetY = NativeStructureVerticalPlacer.applyVerticalPlacement(
                        start,
                        structureId,
                        decision.yShift(),
                        generator.getSeaLevel(),
                        chunk.getMinY(),
                        chunk.getMinY() + chunk.getHeight(),
                        undergroundStep,
                        decision.preserveSourceY(),
                        decision.yBand(),
                        (x, z) -> Engine.hostHeight(current, x, z, true) + current.getMinHeight());
                StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(
                        start, structure, start.getReferences(),
                        NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                        structureId);
                chunk.setStartForStructure(structure, wrapped);
                if (!wrapped.isValid()) {
                    continue;
                }
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "vertical adjustment", structureId, chunkPos.x(), chunkPos.z(), error);
            }
            recordWorldCheckStructureShift(structureId, start.getChunkPos(), offsetY);
        }
    }

    void placeVanillaStructures(WorldGenLevel world, ChunkAccess chunk, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures()) {
            ChunkPos disabledChunk = chunk.getPos();
            throw new IllegalStateException("Iris cannot generate native structures in chunk "
                    + disabledChunk.x() + "," + disabledChunk.z()
                    + " because generate-structures=false disables them outside the pack. That flag is fixed when "
                    + "the world is created (server.properties generate-structures, or the Generate Structures "
                    + "toggle in singleplayer), so it cannot be changed for this world: create a new world with "
                    + "structures enabled, then deny families through importedStructures.disabled or complete keys "
                    + "through importedStructures.disabledExact");
        }
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, world.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<List<Structure>> byStep = structuresByStep(registry);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(world.getSeed(), origin.getX(), origin.getZ());
        BoundingBox area = writableArea(chunk);
        int steps = GenerationStep.Decoration.values().length;
        Engine current = generator.engine();
        List<NativePlacementGroup> placementGroups = new ArrayList<>();
        List<StructureStart> heightmapStarts = new ArrayList<>();
        List<StructureStart> vegetationTargets = new ArrayList<>();
        List<NativeStructureTerrainIntegrator.TerrainTarget> terrainTargets = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            int index = 0;
            for (Structure structure : byStep.get(step)) {
                Identifier id = registry.getKey(structure);
                String structureId = id == null ? null : id.toString();
                if (structureId == null) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", null, chunkPos.x(), chunkPos.z());
                }
                try {
                    IrisNativeStructureDecision sourceDecision = NativeStructureGenerationPolicy.resolve(current,
                            structureId, NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
                    List<StructureStart> starts = structureManager.startsForStructure(sectionPos, structure);
                    List<NativePlacement> resolvedPlacements = new ArrayList<>(starts.size());
                    for (StructureStart start : starts) {
                        BoundingBox footprint = start.getBoundingBox();
                        if (!isHistoricalStructureStart(current, world, start)
                                && !current.getComplex().allowsNewGenerationFootprint(
                                footprint.minX(),
                                footprint.minZ(),
                                footprint.maxX(),
                                footprint.maxZ())) {
                            continue;
                        }
                        NativeStructureOwnershipRecord ownership =
                                NativeStructureOwnershipRecovery.resolve(
                                        current, world.getLevel(), structureId, structure, start);
                        IrisNativeStructureDecision decision =
                                ownership == null ? sourceDecision : ownership.restoredDecision();
                        if (!decision.generate()) {
                            continue;
                        }
                        resolvedPlacements.add(new NativePlacement(start, decision));
                        heightmapStarts.add(start);
                        terrainTargets.add(new NativeStructureTerrainIntegrator.TerrainTarget(
                                structureId, start,
                                NativeStructureTerrainIntegrator.resolveNativeTerrain(
                                        start, decision.terrain())));
                        vegetationTargets.add(start);
                    }
                    if (!resolvedPlacements.isEmpty()) {
                        placementGroups.add(new NativePlacementGroup(
                                structureId, index, step, List.copyOf(resolvedPlacements)));
                    }
                } catch (Throwable error) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", structureId, chunkPos.x(), chunkPos.z(), error);
                }
                index++;
            }
        }
        if (!placementGroups.isEmpty()) {
            ServerLevel level = world.getLevel();
            visitExistingPois(chunk, (position, state) -> level.updatePOIOnBlockStateChange(
                    position, Blocks.AIR.defaultBlockState(), state));
        }
        try {
            int runtimeMinY = world.getMinY();
            WorldgenTerrainHeightmaps.primeStructurePlacement(
                    world, chunkPos, heightmapStarts,
                    worldgenSurfaceHeight(current, runtimeMinY),
                    worldgenFloorHeight(current, runtimeMinY));
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "heightmap priming", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        IrisStaticObjectLayer staticObjects = current.getDimension().getStaticObjectLayer(current.getData());
        Predicate<BlockPos> protectedPosition = NativeGenerationWriteGuard.protectedPositions(
                staticObjects, current.getDimensionStackContext(), current.getMinHeight());
        WorldGenLevel boundedWorld = ModdedNativeStructureWorldgenAccess.create(
                world, chunkPos, worldgenSurfaceHeight(current, world.getMinY()), worldgenFloorHeight(current, world.getMinY()),
                current.getDimensionStackContext() != null,
                protectedPosition);
        try {
            NativeStructureVegetationClearer.clearIntersectingVegetation(
                    boundedWorld, chunk, area, vegetationTargets);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "vegetation cleanup", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        NativeStructureSurfaceFitter.SurfaceTerrainPlan surfaceTerrainPlan;
        try {
            surfaceTerrainPlan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                    boundedWorld, area, terrainTargets,
                    (x, z) -> Engine.hostHeight(current, x, z, true) + current.getMinHeight());
            surfaceTerrainPlan.primeHeightmaps(chunk);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "terrain integration", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        try {
            NativeStructurePostProcessor.prepareTerrain(
                    boundedWorld, area, terrainTargets, this::resolvePaletteBlock);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "terrain preparation", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        for (NativePlacementGroup group : placementGroups) {
            random.setFeatureSeed(decorationSeed, group.featureIndex(), group.step());
            try {
                for (NativePlacement placement : group.placements()) {
                    placeVanillaStructure(boundedWorld, structureManager, random, area, chunkPos,
                            group.structureId(), placement.start(), placement.decision());
                }
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "placement", group.structureId(), chunkPos.x(), chunkPos.z(), error);
            }
        }
        try {
            NativeStructureSurfaceFitter.repairVacuumFoundations(
                    boundedWorld, area, surfaceTerrainPlan);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "foundation repair", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
    }

    static void visitExistingPois(ChunkAccess chunk, BiConsumer<BlockPos, BlockState> visitor) {
        chunk.findBlocks(PoiTypes::hasPoi, visitor);
    }

    private static String nativeStructureBatchContext(List<NativePlacementGroup> placementGroups) {
        if (placementGroups.isEmpty()) {
            return "<no resolved native structures>";
        }
        StringBuilder context = new StringBuilder("[");
        for (int i = 0; i < placementGroups.size(); i++) {
            if (i > 0) {
                context.append(", ");
            }
            context.append(placementGroups.get(i).structureId());
        }
        return context.append(']').toString();
    }

    private void placeVanillaStructure(WorldGenLevel world, StructureManager structureManager,
                                       WorldgenRandom random, BoundingBox area, ChunkPos chunkPos,
                                       String structureId, StructureStart start,
                                       IrisNativeStructureDecision decision) {
        Engine current = generator.engine();
        WorldGenLevel boundedWorld = world instanceof ModdedNativeStructureWorldgenAccess ? world : ModdedNativeStructureWorldgenAccess.create(
                world,
                chunkPos,
                worldgenSurfaceHeight(current, world.getMinY()),
                worldgenFloorHeight(current, world.getMinY()),
                current.getDimensionStackContext() != null,
                NativeGenerationWriteGuard.protectedPositions(
                        current.getDimension().getStaticObjectLayer(current.getData()),
                        current.getDimensionStackContext(), current.getMinHeight()));
        world.setCurrentlyGenerating(() -> "Iris native structure " + structureId);
        try {
            NativeStructurePostProcessor.place(
                    boundedWorld, structureManager, generator, random, area, chunkPos,
                    structureId, start, decision, this::resolvePaletteBlock,
                    (x, z) -> Engine.hostHeight(current, x, z, true) + current.getMinHeight());
        } finally {
            world.setCurrentlyGenerating(null);
        }
    }

    private List<List<Structure>> structuresByStep(Registry<Structure> registry) {
        StructureStepCache cached = structureStepCache;
        if (cached != null && cached.registry() == registry) {
            return cached.structures();
        }
        synchronized (generator) {
            cached = structureStepCache;
            if (cached != null && cached.registry() == registry) {
                return cached.structures();
            }
            int steps = GenerationStep.Decoration.values().length;
            List<List<Structure>> grouped = new ArrayList<>(steps);
            for (int step = 0; step < steps; step++) {
                grouped.add(new ArrayList<>());
            }
            for (Structure structure : registry) {
                grouped.get(structure.step().ordinal()).add(structure);
            }
            for (int step = 0; step < steps; step++) {
                grouped.set(step, List.copyOf(grouped.get(step)));
            }
            List<List<Structure>> resolved = List.copyOf(grouped);
            structureStepCache = new StructureStepCache(registry, resolved);
            return resolved;
        }
    }

    private void recordWorldCheckStructureShift(String structureId, ChunkPos startChunk, int offsetY) {
        if (!WORLD_CHECK_ENABLED || structureId == null) {
            return;
        }
        if (worldCheckStructureShifts.size() >= WORLD_CHECK_SHIFT_RECORD_LIMIT) {
            worldCheckStructureShifts.clear();
        }
        worldCheckStructureShifts.put(new NativeStructureStartKey(structureId, startChunk.pack()), offsetY);
    }

    Integer worldCheckStructureShift(String structureId, ChunkPos startChunk) {
        if (structureId == null || startChunk == null) {
            return null;
        }
        return worldCheckStructureShifts.get(new NativeStructureStartKey(structureId, startChunk.pack()));
    }

    void clearWorldCheckStructureShifts() {
        worldCheckStructureShifts.clear();
    }

    private BlockState resolvePaletteBlock(IrisMaterialPalette palette, RNG rng,
                                          int x, int y, int z) {
        PlatformBlockState platformState = palette.get(rng, x, y, z, generator.engine().getData());
        if (platformState == null || !(platformState.nativeHandle() instanceof BlockState blockState)) {
            throw new IllegalStateException("Configured native structure palette did not resolve a Minecraft block at "
                    + x + "," + y + "," + z);
        }
        return blockState;
    }

    private boolean isHistoricalStructureStart(Engine current, WorldGenLevel world, StructureStart start) {
        ChunkPos origin = start.getChunkPos();
        if (!current.getComplex().allowsMantleChunkWrite(origin.x(), origin.z())) {
            return true;
        }
        ChunkAccess source = world.getChunk(origin.x(), origin.z(), ChunkStatus.EMPTY, false);
        return source != null && NativeGenerationWriteGuard.isHistoricalStructure(
                current, ModdedNativeTerrainReceipts.structureActivation(source));
    }

    private BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinY() + 1;
        int maxY = chunk.getMinY() + chunk.getHeight() - 1;
        return new BoundingBox(minX, minY, minZ, minX + 15, maxY, minZ + 15);
    }

    private IntBinaryOperator worldgenSurfaceHeight(Engine generationEngine, int runtimeMinY) {
        return (x, z) -> Engine.hostHeight(generationEngine, x, z, false) + runtimeMinY + 1;
    }

    private IntBinaryOperator worldgenFloorHeight(Engine generationEngine, int runtimeMinY) {
        return (x, z) -> Engine.hostHeight(generationEngine, x, z, true) + runtimeMinY + 1;
    }

    private record NativeStructureStartKey(String structureId, long chunkPosition) {
    }

    private record NativePlacement(StructureStart start, IrisNativeStructureDecision decision) {
    }

    private record NativePlacementGroup(String structureId, int featureIndex, int step,
                                        List<NativePlacement> placements) {
    }

    private record IrisNativeLocateSearch(Holder<Structure> holder, String structureId,
                                          NativeStructureLocatePersistence.Search search) {
    }

    private record StructureStepCache(Registry<Structure> registry, List<List<Structure>> structures) {
    }
}
