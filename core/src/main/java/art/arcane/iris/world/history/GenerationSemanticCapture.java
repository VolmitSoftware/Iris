package art.arcane.iris.world.history;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.structure.placement.StructurePlacementScope;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GenerationSemanticCapture {
    private static final int CHUNK_SIZE = 16;
    private static final String INLET_PROFILE = HydrologyFeatureType.SEA_INLET.key();
    private static final long CAVE_POOL_SALT = 0x43415645504f4f4cL;

    private GenerationSemanticCapture() {
    }

    static ChunkGenerationSemantics captureScoped(
            Engine engine,
            GenerationHistory.GenerationStage stage,
            NativeBlockPositionPredicate caveSpace
    ) {
        return capture(engine, stage.chunkX(), stage.chunkZ(), stage.activation().activationId(), caveSpace, true);
    }

    public static ChunkGenerationSemantics capture(
            Engine engine,
            GenerationHistory.GenerationStage stage
    ) {
        return capture(engine, stage, (x, y, z) -> true);
    }

    public static ChunkGenerationSemantics capture(
            Engine engine,
            GenerationHistory.GenerationStage stage,
            NativeBlockPositionPredicate caveSpace
    ) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        GenerationHistory.GenerationStage requiredStage = Objects.requireNonNull(stage, "stage");
        return capture(
                requiredEngine,
                requiredStage.chunkX(),
                requiredStage.chunkZ(),
                requiredStage.activation().activationId(),
                caveSpace,
                false
        );
    }

    public static ChunkGenerationSemantics capture(
            Engine engine,
            int chunkX,
            int chunkZ,
            long activationId
    ) {
        return capture(engine, chunkX, chunkZ, activationId, (x, y, z) -> true, false);
    }

    private static ChunkGenerationSemantics capture(
            Engine engine,
            int chunkX,
            int chunkZ,
            long activationId,
            NativeBlockPositionPredicate caveSpace,
            boolean scoped
    ) {
        Objects.requireNonNull(caveSpace, "cave space");
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        ChunkGenerationSemantics.Builder semantics = ChunkGenerationSemantics.builder(
                chunkX,
                chunkZ,
                activationId
        );
        captureColumns(requiredEngine, chunkX, chunkZ, semantics);
        captureWater(requiredEngine, chunkX, chunkZ, semantics);
        captureMantleFacts(requiredEngine, chunkX, chunkZ, semantics, caveSpace, scoped);
        captureStructures(requiredEngine, chunkX, chunkZ, semantics);
        return semantics.seal().build();
    }

    private static void captureColumns(
            Engine engine,
            int chunkX,
            int chunkZ,
            ChunkGenerationSemantics.Builder semantics
    ) {
        IrisComplex complex = engine.getComplex();
        int minimumX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int blockX = minimumX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int blockZ = minimumZ + localZ;
                if (complex.allowsNewDiscreteContentAt(blockX, blockZ)) {
                    IrisBiome biome = complex.getTrueBiomeStream().get(blockX, blockZ);
                    if (biome != null && biome.getLoadKey() != null) {
                        semantics.addSurfaceBiome(biome.getLoadKey());
                    }
                    IrisRegion region = complex.getRegionStream().get(blockX, blockZ);
                    if (region != null && region.getLoadKey() != null) {
                        semantics.addRegion(region.getLoadKey());
                    }
                }
            }
        }
    }

    private static void captureWater(
            Engine engine,
            int chunkX,
            int chunkZ,
            ChunkGenerationSemantics.Builder semantics
    ) {
        IrisComplex complex = engine.getComplex();
        HydrologyRuntime runtime = complex.getHydrologyRuntime();
        if (runtime == null || !runtime.enabled()) {
            return;
        }
        ChunkWaterRaster raster = runtime.raster(chunkX, chunkZ);
        if (raster.provisional()) {
            throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ
                    + " cannot record its water features: its water raster is not planned on this thread.");
        }
        int minimumX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        int minimumY = engine.getDimension().getMinHeight();
        captureInlets(complex, runtime.inlets(chunkX, chunkZ), minimumX, minimumZ, minimumY, semantics);
        if (raster.empty()) {
            return;
        }
        Map<Long, Set<HydrologyFeatureType>> standing = new HashMap<>();
        Set<WaterFeature> recorded = new HashSet<>();
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int blockX = minimumX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int blockZ = minimumZ + localZ;
                if (!hasFullHydrologyWeight(complex, blockX, blockZ)) {
                    continue;
                }
                WaterRole role = complex.getWaterRoleStream().get(blockX, blockZ);
                if (!role.wet() || role == WaterRole.INLET) {
                    continue;
                }
                long id = raster.featureId(localX, localZ);
                Set<HydrologyFeatureType> types = role == WaterRole.POND
                        ? standing.computeIfAbsent(id, feature -> runtime.standingWaterTypes(chunkX, chunkZ, feature))
                        : flowingTypes(runtime, role, raster.tier(localX, localZ));
                String fluid = Objects.requireNonNull(raster.fluid(localX, localZ),
                        "Water column " + blockX + "," + blockZ + " has no fluid profile");
                int y = Math.addExact((int) Math.round(complex.getWaterHeadStream().getDouble(blockX, blockZ)), minimumY);
                for (HydrologyFeatureType type : types) {
                    if (recorded.add(new WaterFeature(type, id))) {
                        semantics.addRiverFeature(fluid, type, id, blockX, y, blockZ);
                    }
                }
            }
        }
    }

    private static void captureInlets(IrisComplex complex, List<SeaInlet> inlets, int minimumX, int minimumZ, int minimumY,
                                      ChunkGenerationSemantics.Builder semantics) {
        for (SeaInlet inlet : inlets) {
            captureInlet(complex, inlet, minimumX, minimumZ, minimumY, semantics);
        }
    }

    private static void captureInlet(IrisComplex complex, SeaInlet inlet, int minimumX, int minimumZ, int minimumY,
                                     ChunkGenerationSemantics.Builder semantics) {
        int[] column = new int[2];
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int blockX = minimumX + localX;
                int blockZ = minimumZ + localZ;
                if (SeaInletRasterizer.column(inlet, blockX, blockZ, column) && hasFullHydrologyWeight(complex, blockX, blockZ)) {
                    semantics.addRiverFeature(INLET_PROFILE, HydrologyFeatureType.SEA_INLET, inlet.id(), blockX,
                            Math.addExact(inlet.waterLevel(), minimumY), blockZ);
                    return;
                }
            }
        }
    }

    private static Set<HydrologyFeatureType> flowingTypes(HydrologyRuntime runtime, WaterRole role, HydrologyTier tier) {
        return switch (role) {
            case CHANNEL -> Set.of(runtime.channelType(tier));
            case CASCADE -> Set.of(HydrologyFeatureType.CASCADE, runtime.channelType(tier));
            case WATERFALL -> Set.of(HydrologyFeatureType.WATERFALL, runtime.channelType(tier));
            case MOUTH -> Set.of(HydrologyFeatureType.MOUTH, runtime.channelType(tier));
            case NONE, BANK, SHORE, POND, POND_SHORE, DAM, INLET ->
                    throw new IllegalArgumentException("Water role " + role + " is not flowing water");
        };
    }

    private static void captureMantleFacts(
            Engine engine,
            int chunkX,
            int chunkZ,
            ChunkGenerationSemantics.Builder semantics,
            NativeBlockPositionPredicate caveSpace,
            boolean scoped
    ) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int minimumX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        BitSet resolvedCaves = new BitSet();
        CaveBiomes caveBiomes = new CaveBiomes(engine, scoped);
        mantle.iterateChunk(chunkX, chunkZ, MatterCavern.class, (localX, y, localZ, cavern) -> {
            if (!caveSpace.test(localX, y, localZ)) {
                return;
            }
            captureCave(
                    caveBiomes,
                    semantics,
                    resolvedCaves,
                    minimumX + localX,
                    y,
                    minimumZ + localZ,
                    cavern == null ? null : cavern.getCustomBiome()
            );
        });
        IrisComplex complex = engine.getComplex();
        Map<Integer, String> poolCells = new HashMap<>();
        mantle.iterateChunk(chunkX, chunkZ, HydrologyCaveCell.class, (localX, y, localZ, cell) -> {
            if (!caveSpace.test(localX, y, localZ)) {
                return;
            }
            if (cell != null && cell.action() == HydrologyCaveAction.POOL_SOURCE) {
                poolCells.put(poolCell(localX, y, localZ), cell.fluidProfileKey());
            }
            int blockX = minimumX + localX;
            int blockZ = minimumZ + localZ;
            if (!hasFullHydrologyWeight(complex, blockX, blockZ)) {
                return;
            }
            captureCave(
                    caveBiomes,
                    semantics,
                    resolvedCaves,
                    blockX,
                    y,
                    blockZ,
                    cell == null ? null : cell.floodedBiomeKey()
            );
            if (cell != null && cell.isWet() && cell.fluidProfileKey() != null && !cell.fluidProfileKey().isBlank()
                    && !HydrologyCaveCell.SEA_FLUID.equals(cell.fluidProfileKey())) {
                semantics.addRiverProfile(cell.fluidProfileKey());
            }
        });
        if (!poolCells.isEmpty()) {
            captureCavePools(poolCells, minimumX, minimumZ, engine.getDimension().getMinHeight(), semantics);
        }
        mantle.iterateChunk(chunkX, chunkZ, MatterStructurePOI.class, (localX, y, localZ, point) -> {
            if (point != null) {
                semantics.addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest(point.getType(),
                        new ChunkGenerationSemantics.BlockPosition(Math.addExact(minimumX, localX), y,
                                Math.addExact(minimumZ, localZ))));
            }
        });
        ObjectMarkers objects = new ObjectMarkers(semantics);
        mantle.iterateChunk(chunkX, chunkZ, String.class, (localX, y, localZ, marker) -> objects.add(marker));
    }

    private static void captureCavePools(Map<Integer, String> cells, int minimumX, int minimumZ, int minimumY,
                                         ChunkGenerationSemantics.Builder semantics) {
        Set<Integer> visited = new HashSet<>();
        for (int start : cells.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            int top = start;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                if (abovePoolCell(cell, top)) {
                    top = cell;
                }
                for (int neighbour : poolNeighbours(cell)) {
                    if (neighbour >= 0 && cells.containsKey(neighbour) && visited.add(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
            int blockX = minimumX + poolX(top);
            int blockZ = minimumZ + poolZ(top);
            int y = Math.addExact(poolY(top), minimumY);
            semantics.addRiverFeature(cells.get(top), HydrologyFeatureType.CAVE_POOL, CellHash.mix(CAVE_POOL_SALT, y, blockX, blockZ),
                    blockX, y, blockZ);
        }
    }

    private static int poolCell(int localX, int y, int localZ) {
        return (y << 8) | (localX << 4) | localZ;
    }

    private static int poolX(int cell) {
        return (cell >> 4) & 15;
    }

    private static int poolZ(int cell) {
        return cell & 15;
    }

    private static int poolY(int cell) {
        return cell >>> 8;
    }

    private static boolean abovePoolCell(int cell, int top) {
        if (poolY(cell) != poolY(top)) {
            return poolY(cell) > poolY(top);
        }
        return poolX(cell) != poolX(top) ? poolX(cell) < poolX(top) : poolZ(cell) < poolZ(top);
    }

    private static int[] poolNeighbours(int cell) {
        int x = poolX(cell);
        int y = poolY(cell);
        int z = poolZ(cell);
        return new int[]{
                x < 15 ? poolCell(x + 1, y, z) : -1,
                x > 0 ? poolCell(x - 1, y, z) : -1,
                z < 15 ? poolCell(x, y, z + 1) : -1,
                z > 0 ? poolCell(x, y, z - 1) : -1,
                poolCell(x, y + 1, z),
                y > 0 ? poolCell(x, y - 1, z) : -1
        };
    }

    private static boolean hasFullHydrologyWeight(IrisComplex complex, int blockX, int blockZ) {
        TransitionDisplacementField displacement = complex.getTransitionDisplacement();
        return displacement == null || displacement.seamWeight(blockX, blockZ) == 1D;
    }

    private static void captureCave(
            CaveBiomes caveBiomes,
            ChunkGenerationSemantics.Builder semantics,
            BitSet resolvedCaves,
            int blockX,
            int y,
            int blockZ,
            String explicitBiomeKey
    ) {
        int position = (y << 8) | ((blockX & 15) << 4) | (blockZ & 15);
        if (resolvedCaves.get(position)) {
            return;
        }
        resolvedCaves.set(position);
        if (explicitBiomeKey != null && !explicitBiomeKey.isBlank()) {
            caveBiomes.add(semantics, explicitBiomeKey);
            return;
        }
        IrisBiome caveBiome = caveBiomes.resolve(blockX, y, blockZ);
        if (caveBiome != null && caveBiome.getLoadKey() != null) {
            caveBiomes.add(semantics, caveBiome.getLoadKey());
        }
    }

    private static void captureStructures(
            Engine engine,
            int chunkX,
            int chunkZ,
            ChunkGenerationSemantics.Builder semantics
    ) {
        IrisComplex complex = engine.getComplex();
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!complex.allowsNewGenerationChunk(chunkX, chunkZ)
                || !mantle.hasFlag(chunkX, chunkZ, ReservedFlag.JIGSAW)) {
            return;
        }
        for (IrisStructurePlacement placement : StructurePlacementScope.placementsAt(
                engine,
                chunkX,
                chunkZ)) {
            if (placement == null || !placement.hasIrisStructures()) {
                continue;
            }
            IrisStructureLocator.ResolvedPlacement resolved = IrisStructureLocator.resolvePlacement(
                    engine,
                    placement,
                    chunkX,
                    chunkZ
            );
            if (resolved == null || !IrisStructureLocator.allowsResolvedFootprint(engine, resolved)) {
                continue;
            }
            semantics.addStructure(
                    resolved.structureKey(),
                    resolved.originX(),
                    resolved.baseY(),
                    resolved.originZ()
            );
        }
    }

    private static final class ObjectMarkers {
        private final ChunkGenerationSemantics.Builder semantics;
        private String lastMarker;

        private ObjectMarkers(ChunkGenerationSemantics.Builder semantics) {
            this.semantics = semantics;
        }

        private void add(String marker) {
            if (marker != null && marker.equals(lastMarker)) {
                return;
            }
            StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
            if (decoded != null) {
                semantics.addObject(decoded.objectKey());
            }
            lastMarker = marker;
        }
    }

    private static final class SubterrainBiomes {
        private final Engine engine;
        private final boolean live;
        private final SubterrainPlanner planner;
        private final int minimumY;

        private SubterrainBiomes(Engine engine, boolean live, SubterrainPlanner planner, int minimumY) {
            this.engine = engine;
            this.live = live;
            this.planner = planner;
            this.minimumY = minimumY;
        }

        private static SubterrainBiomes of(Engine engine, int minimumY) {
            if (engine.getClass() != IrisEngine.class) {
                return new SubterrainBiomes(engine, false, null, 0);
            }
            IrisEngine irisEngine = (IrisEngine) engine;
            if (!irisEngine.hasGenerationRuntimeScope() && irisEngine.getGenerationHistoryRuntimeRouter().isPresent()) {
                return new SubterrainBiomes(engine, false, null, 0);
            }
            IrisComplex complex = engine.getComplex();
            IrisDimension dimension = engine.getDimension();
            SubterrainPlanner planner = complex == null || dimension == null || !dimension.allowsSubterrainFeatures()
                    || dimension.getSubterrainFeatures() == null || dimension.getSubterrainFeatures().isEmpty()
                    ? null
                    : complex.getSubterrainPlanner();
            return new SubterrainBiomes(engine, true, planner, minimumY);
        }

        private IrisBiome resolve(int x, int y, int z) {
            if (!live) {
                return engine.getSubterrainBiome(x, y, z);
            }
            if (planner == null) {
                return null;
            }
            SubterrainCell cell = planner.sample(x, y + minimumY, z);
            if (!cell.occupied() || cell.room().biome().isEmpty()) {
                return null;
            }
            return engine.getSubterrainBiome(x, y, z);
        }
    }

    private static final class CaveBiomes {
        private final Engine engine;
        private final boolean scoped;
        private final CaveFallback fallback;
        private SubterrainBiomes subterrain;
        private final IrisDimensionCarvingResolver.State fallbackState = new IrisDimensionCarvingResolver.State();
        private IrisDimensionCarvingResolver.Snapshot snapshot;
        private int minimumY;
        private String lastKey;

        private CaveBiomes(Engine engine, boolean scoped) {
            this.engine = engine;
            this.scoped = scoped;
            this.fallback = scoped && engine instanceof IrisEngine irisEngine
                    && irisEngine.hasGenerationRuntimeScope() && !engine.getPlatformHooks().isMainThread()
                    ? new CaveFallback(engine.getComplex(), engine.getDimensionStackContext())
                    : null;
        }

        private void add(ChunkGenerationSemantics.Builder semantics, String key) {
            if (key != lastKey) {
                semantics.addCaveBiome(key);
                lastKey = key;
            }
        }

        private IrisBiome resolve(int x, int y, int z) {
            if (!scoped) {
                return engine.getCaveBiome(x, y, z);
            }
            if (snapshot == null) {
                snapshot = IrisDimensionCarvingResolver.snapshot(engine);
                minimumY = engine.getWorld().minHeight();
                subterrain = SubterrainBiomes.of(engine, minimumY);
            }
            IrisBiome featureBiome = subterrain.resolve(x, y, z);
            if (featureBiome != null) {
                return featureBiome;
            }
            IrisBiome configured = snapshot.resolveBiome(x, y + minimumY, z);
            if (configured != null) {
                return configured;
            }
            return fallback == null ? engine.getCaveBiome(x, y, z, fallbackState) : fallback.resolve(x, y, z);
        }
    }

    private static final class CaveFallback {
        private final ProceduralStream<IrisBiome> surfaceBiomes;
        private final ProceduralStream<IrisBiome> caveBiomes;
        private final ProceduralStream<Double> heights;
        private final DimensionStackContext stack;
        private final CaveColumn[] columns = new CaveColumn[CHUNK_SIZE * CHUNK_SIZE];

        private CaveFallback(IrisComplex complex, DimensionStackContext stack) {
            this.surfaceBiomes = complex.getTrueBiomeStream();
            this.caveBiomes = complex.getCaveBiomeStream();
            this.heights = complex.getHeightStream();
            this.stack = stack;
        }

        private IrisBiome resolve(int x, int y, int z) {
            int index = ((x & 15) << 4) | (z & 15);
            CaveColumn column = columns[index];
            if (column == null) {
                IrisBiome surfaceBiome = surfaceBiomes.get(x, z);
                int surfaceY = heights.get(x, z).intValue();
                IrisBiome caveBiome = caveBiomes.get(x, z);
                if (caveBiome == null || caveBiome.getLoadKey() == null) {
                    caveBiome = surfaceBiome;
                    if (stack != null) {
                        DimensionStackLayout.Layer layer = stack.getLayout(x, z).surfaceLayer();
                        if (layer != null && layer.biome() != null) {
                            caveBiome = layer.biome();
                        }
                    }
                }
                column = new CaveColumn(surfaceBiome, caveBiome, surfaceY,
                        caveBiome == null ? 0 : Math.max(0, caveBiome.getCaveMinDepthBelowSurface()));
                columns[index] = column;
            }
            int depth = column.surfaceY() - y;
            return column.caveBiome() == null || depth <= 0 || depth < column.minimumDepth()
                    ? column.surfaceBiome() : column.caveBiome();
        }
    }

    private record CaveColumn(IrisBiome surfaceBiome, IrisBiome caveBiome, int surfaceY, int minimumDepth) {
    }

    private record WaterFeature(HydrologyFeatureType type, long id) {
    }
}
