package art.arcane.iris.modded;

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
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVolumeSource;
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

final class ModdedNativeStructurePolicy implements NativeModdedStructureStage.Policy<
        Engine, NativeStructureStartPlan, NativeStructureOwnershipRecord> {
    private static final int WORLD_CHECK_SHIFT_RECORD_LIMIT = 4096;
    private static final boolean WORLD_CHECK_ENABLED = Boolean.getBoolean("iris.worldcheck");

    private final IrisModdedChunkGenerator generator;
    private final ConcurrentHashMap<StructureStartKey, Integer> worldCheckStructureShifts = new ConcurrentHashMap<>();

    ModdedNativeStructurePolicy(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Engine current() {
        return generator.engine();
    }

    @Override
    public StructureReferencePolicy<NativeStructureStartPlan, NativeStructureOwnershipRecord> structurePolicy(Engine context) {
        return new IrisStructurePolicy(context);
    }

    @Override
    public StructureVolumePolicy<Engine, NativeStructureStartPlan> volumePolicy() {
        return IrisStructureVolumePolicy.INSTANCE;
    }

    @Override
    public void installVolumeSource(Engine context, NativeStructureVolumeSource<Engine, NativeStructureStartPlan> source) {
        NativeStructureVolumeIndex.install(context, source::volumesAt);
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
        return ((IrisMaterialPalette) source).get(rng, x, y, z, generator.engine().getData());
    }

    @Override
    public RuntimeException structuresDisabled(int chunkX, int chunkZ) {
        return new IllegalStateException("Iris cannot generate native structures in chunk "
                + chunkX + "," + chunkZ
                + " because generate-structures=false disables them outside the pack. That flag is fixed when "
                + "the world is created (server.properties generate-structures, or the Generate Structures "
                + "toggle in singleplayer), so it cannot be changed for this world: create a new world with "
                + "structures enabled, then deny families through importedStructures.disabled or complete keys "
                + "through importedStructures.disabledExact");
    }

    @Override
    public String generationLabel(String structureId) {
        return "Iris native structure " + structureId;
    }

    @Override
    public void recordShift(String structureId, long startChunk, int offsetY) {
        if (!WORLD_CHECK_ENABLED || structureId == null) {
            return;
        }
        if (worldCheckStructureShifts.size() >= WORLD_CHECK_SHIFT_RECORD_LIMIT) {
            worldCheckStructureShifts.clear();
        }
        worldCheckStructureShifts.put(new StructureStartKey(structureId, startChunk), offsetY);
    }

    @Override
    public Integer recordedShift(String structureId, long startChunk) {
        return worldCheckStructureShifts.get(new StructureStartKey(structureId, startChunk));
    }

    @Override
    public void clearShifts() {
        worldCheckStructureShifts.clear();
    }

    private record StructureStartKey(String structureId, long chunkPosition) {
    }
}
