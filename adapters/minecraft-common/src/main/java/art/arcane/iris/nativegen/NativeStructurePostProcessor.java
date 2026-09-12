package art.arcane.iris.nativegen;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.function.IntBinaryOperator;

public final class NativeStructurePostProcessor {
    private NativeStructurePostProcessor() {
    }

    public static void place(WorldGenLevel world, StructureManager structureManager, ChunkGenerator generator,
                             WorldgenRandom random, BoundingBox area, ChunkPos chunkPos, String structureId,
                             StructureStart start, IrisNativeStructureDecision decision,
                             PaletteBlockResolver paletteBlockResolver,
                             IntBinaryOperator surfaceHeight) {
        IrisStructureStiltSettings stilt = decision.stilt();
        NativeStructureVerticalPlacer.ensureMonumentSeaLevelAlignment(start, structureId, decision.yShift(),
                generator.getSeaLevel(), area.minY(), area.maxY() + 1);
        start.placeInChunk(world, structureManager, generator, random, area, chunkPos);
        if (stilt != null) {
            NativeStructureFoundationBuilder.placeStilts(world, area, structureId, start, stilt,
                    paletteBlockResolver, surfaceHeight,
                    !NativeStructureVegetationClearer.isUndergroundStep(start.getStructure().step()));
        }
    }

    public static void prepareTerrain(WorldGenLevel world, BoundingBox area,
                                      List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
                                      PaletteBlockResolver paletteBlockResolver) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceTerrain =
                NativeStructureTerrainIntegrator.captureSourceTerrain(world, area, targets);
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            NativeStructureTerrainIntegrator.integrateTerrain(world, area, target.structureId(), target.start(),
                    target.terrain(), paletteBlockResolver, sourceTerrain);
        }
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            if (NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                    target.start(), target.terrain())) {
                NativeStructureTerrainIntegrator.clearLegacyTemplateAir(
                        world, area, target.start(), () -> world.getLevel().getStructureManager());
            }
        }
        NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world, area, targets, () -> world.getLevel().getStructureManager());
    }

    @FunctionalInterface
    public interface PaletteBlockResolver {
        BlockState resolve(IrisMaterialPalette palette, RNG rng, int x, int y, int z);
    }
}
