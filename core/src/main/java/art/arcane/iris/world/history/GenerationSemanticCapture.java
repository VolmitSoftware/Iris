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
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;

import java.util.BitSet;
import java.util.Objects;

public final class GenerationSemanticCapture {
    private static final int CHUNK_SIZE = 16;

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
                if (!hasFullHydrologyWeight(complex, blockX, blockZ)) {
                    continue;
                }
                HydrologyColumnSample hydrology = complex.sampleHydrologyColumn(blockX, blockZ);
                if (hydrology == null) {
                    continue;
                }
                for (HydrologyColumnLayer layer : hydrology.layers()) {
                    semantics.addRiverProfile(layer.profileKey());
                    semantics.addRiverFeature(
                            layer.profileKey(),
                            layer.feature().type(),
                            layer.feature().id(),
                            layer.feature().x(),
                            Math.addExact(
                                    layer.feature().y(),
                                    engine.getDimension().getMinHeight()
                            ),
                            layer.feature().z()
                    );
                }
            }
        }
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
        mantle.iterateChunk(chunkX, chunkZ, HydrologyCaveCell.class, (localX, y, localZ, cell) -> {
            if (!caveSpace.test(localX, y, localZ)) {
                return;
            }
            int blockX = minimumX + localX;
            int blockZ = minimumZ + localZ;
            if (!hasFullHydrologyWeight(engine.getComplex(), blockX, blockZ)) {
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
            if (cell != null && cell.fluidProfileKey() != null && !cell.fluidProfileKey().isBlank()) {
                semantics.addRiverProfile(cell.fluidProfileKey());
            }
        });
        mantle.iterateChunk(chunkX, chunkZ, MatterStructurePOI.class, (localX, y, localZ, point) -> {
            if (point != null) {
                semantics.addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest(point.getType(),
                        new ChunkGenerationSemantics.BlockPosition(Math.addExact(minimumX, localX), y,
                                Math.addExact(minimumZ, localZ))));
            }
        });
        mantle.iterateChunk(chunkX, chunkZ, String.class, (localX, y, localZ, marker) -> {
            StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
            if (decoded != null) {
                semantics.addObject(decoded.objectKey());
            }
        });
    }

    private static boolean hasFullHydrologyWeight(IrisComplex complex, int blockX, int blockZ) {
        TransitionGenerationPlan transition = complex.getTransitionGenerationPlan();
        return transition == null || transition.hydrologyWeightAt(blockX, blockZ) == 1D;
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
            semantics.addCaveBiome(explicitBiomeKey);
            return;
        }
        IrisBiome caveBiome = caveBiomes.resolve(blockX, y, blockZ);
        if (caveBiome != null && caveBiome.getLoadKey() != null) {
            semantics.addCaveBiome(caveBiome.getLoadKey());
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

    private static final class CaveBiomes {
        private final Engine engine;
        private final boolean scoped;
        private final CaveFallback fallback;
        private final IrisDimensionCarvingResolver.State fallbackState = new IrisDimensionCarvingResolver.State();
        private IrisDimensionCarvingResolver.Snapshot snapshot;
        private int minimumY;

        private CaveBiomes(Engine engine, boolean scoped) {
            this.engine = engine;
            this.scoped = scoped;
            this.fallback = scoped && engine instanceof IrisEngine irisEngine
                    && irisEngine.hasGenerationRuntimeScope() && !engine.getPlatformHooks().isMainThread()
                    ? new CaveFallback(engine.getComplex(), engine.getDimensionStackContext())
                    : null;
        }

        private IrisBiome resolve(int x, int y, int z) {
            if (!scoped) {
                return engine.getCaveBiome(x, y, z);
            }
            if (snapshot == null) {
                snapshot = IrisDimensionCarvingResolver.snapshot(engine);
                minimumY = engine.getWorld().minHeight();
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

}
