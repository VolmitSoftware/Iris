package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.nativelib.terrain.structure.StructureCachePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.nativegen.GenerationWritePolicy;
import art.arcane.iris.structure.nativegen.IrisStructurePolicy;
import art.arcane.iris.structure.nativegen.IrisStructureVolumePolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.structure.nativegen.StructureLocateSearch;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateSearchAccess;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePalette;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;
import art.arcane.volmlib.util.math.RNG;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

public final class BukkitStructureStagePolicy implements StructureStagePolicy<
        Engine, NativeStructureStartPlan, NativeStructureOwnershipRecord>, StructureCachePolicy<IrisDimension> {
    private final Engine engine;

    public BukkitStructureStagePolicy(Engine engine) {
        this.engine = engine;
    }

    @Override
    public IrisDimension dimension() {
        return engine.getDimension();
    }

    @Override
    public int runtimeId() {
        return engine.getCacheID();
    }

    @Override
    public Engine current() {
        return engine;
    }

    @Override
    public StructureReferencePolicy<NativeStructureStartPlan, NativeStructureOwnershipRecord> structurePolicy(Engine context) {
        return new IrisStructurePolicy(context);
    }

    @Override
    public boolean hasPlacement(Engine context, String key) {
        return IrisStructureLocator.hasNativePlacement(context, key);
    }

    @Override
    public <S> StructureLocateSearchAccess<S, NativeStructureOwnershipRecord> locateSearch(
            Engine context, StructureStagePolicy.LocateRequest<S, NativeStructureOwnershipRecord> request) {
        return new StructureLocateSearch<>(new StructureLocateSearch.Options<>(
                context, request.key(), request.x(), request.z(), request.radius(), request.probe(), request.ownership()));
    }

    @Override
    public int locatorY(NativeStructureOwnershipRecord ownership) {
        return ownership.locatorY();
    }

    @Override
    public StructurePlacementDecision restoredDecision(NativeStructureOwnershipRecord ownership) {
        return ownership.restoredDecision();
    }

    @Override
    public boolean allowsFootprint(Engine context, StructureStagePolicy.Footprint footprint) {
        return context.getComplex().allowsNewGenerationFootprint(
                footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ());
    }

    @Override
    public boolean allowsChunkWrite(Engine context, int chunkX, int chunkZ) {
        return context.getComplex().allowsMantleChunkWrite(chunkX, chunkZ);
    }

    @Override
    public boolean historicalStructure(Engine context, long activation) {
        return GenerationWritePolicy.isHistoricalStructure(context, activation);
    }

    @Override
    public boolean stacked(Engine context) {
        return context.getDimensionStackContext() != null;
    }

    @Override
    public NativeBlockPositionPredicate protectedPositions(Engine context) {
        return GenerationWritePolicy.protectedPositions(context.getDimension().getStaticObjectLayer(context.getData()),
                context.getDimensionStackContext(), context.getMinHeight());
    }

    @Override
    public IntBinaryOperator surfaceHeight(Engine context) {
        return (x, z) -> Engine.hostHeight(context, x, z, true) + context.getMinHeight();
    }

    @Override
    public IntBinaryOperator worldgenHeight(Engine context, int runtimeMinY, boolean floor) {
        return (x, z) -> Engine.hostHeight(context, x, z, floor) + runtimeMinY + 1;
    }

    @Override
    public NativeBlockState paletteBlock(StructurePalette source, RNG rng, int x, int y, int z) {
        return ((IrisMaterialPalette) source).get(rng, x, y, z, engine.getData());
    }

    @Override
    public RuntimeException structuresDisabled(int chunkX, int chunkZ) {
        return new IllegalStateException("Iris cannot generate native structures in chunk "
                + chunkX + "," + chunkZ
                + " because structure generation is disabled outside the pack; enable native structure generation "
                + "and deny families through importedStructures.disabled or complete keys through importedStructures.disabledExact");
    }

    @Override
    public String generationLabel(String structureId) {
        return "Iris native structure " + structureId;
    }

}
