package art.arcane.iris.nativegen;

import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureVacuumParityTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void explicitVacuumUsesNonCarvingSurfaceFittingOnModdedLoaders() {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        DesertPyramidPiece piece = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        StructureStart start = new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
        NativeStructureTerrainIntegrator.TerrainTarget target =
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:vacuum", start,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.VACUUM));

        assertTrue(NativeStructureSurfaceFitter.requiresSurfaceTerrain(target));
        assertFalse(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start, target.terrain()));
    }
}
