package art.arcane.iris.modded;

import art.arcane.iris.nativegen.NativeStructureGenerationException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NativeStructureFailureContractTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void structureLocateDoesNotCatchAndFallThroughToAnotherImplementation() throws IOException {
        String source = moddedSource("IrisModdedChunkGenerator.java");
        int locateStart = source.indexOf("public Pair<BlockPos, Holder<Structure>> findNearestMapStructure");
        int locateEnd = source.indexOf("public boolean isNativeStructureReachable", locateStart);
        String locate = source.substring(locateStart, locateEnd);
        String stage = moddedSource("ModdedNativeStructureStage.java");
        int filterStart = stage.indexOf("HolderSet<Structure> filterReachableNativeStructures");
        int filterEnd = stage.indexOf("void adjustGeneratedStructures", filterStart);
        String filter = stage.substring(filterStart, filterEnd);

        assertTrue(locate.contains("Engine current = engine();"));
        assertFalse(locate.contains("catch (Throwable"));
        assertFalse(locate.contains("return null;\n        } catch"));
        assertTrue(filter.contains("unregistered structure holder"));
        assertFalse(filter.contains("catch (Throwable"));
        assertFalse(filter.contains("failed closed"));
    }

    @Test
    public void disabledGlobalStructuresNeitherRefuseBindingNorFailPlacement() throws IOException {
        String generator = moddedSource("IrisModdedChunkGenerator.java");
        String stage = moddedSource("ModdedNativeStructureStage.java");

        assertFalse(generator.contains("generateStructures()"));
        assertFalse(generator.contains("requireGlobalStructureGeneration"));
        assertFalse(stage.contains("shouldGenerateStructures()"));
    }

    @Test
    public void structureVegetationCleanupPrecedesTerrainPreparationAndPlacement() throws IOException {
        String source = moddedSource("ModdedNativeStructureStage.java");
        int placementStart = source.indexOf("void placeVanillaStructures");
        int placementEnd = source.indexOf("private static String nativeStructureBatchContext", placementStart);
        String placement = source.substring(placementStart, placementEnd);

        assertTrue(placement.contains("\"terrain fitting\""));
        assertFalse(placement.contains("\"terrain carving\""));
        assertTrue(placement.contains("visitExistingPois(chunk"));
        assertTrue(placement.contains("level.updatePOIOnBlockStateChange("));
        assertTrue(placement.contains("Blocks.AIR.defaultBlockState(), state"));
        assertTrue(placement.indexOf("visitExistingPois(chunk")
                < placement.indexOf("WorldgenTerrainHeightmaps.primeStructurePlacement("));
        assertTrue(placement.contains(".beforePieces("));
        assertTrue(placement.contains("clearIntersectingVegetation"));
        assertTrue(placement.indexOf("clearIntersectingVegetation")
                < placement.indexOf(".beforePieces("));
        assertTrue(placement.indexOf(".beforePieces(")
                < placement.indexOf("for (NativePlacementGroup group"));
        assertTrue(placement.indexOf("fit.afterPieces(")
                > placement.indexOf("for (NativePlacementGroup group"));
    }

    @Test
    public void structurePlacementPrimesNeighbourWorldgenHeightmapsBeforeTerrainPreparation() throws IOException {
        String source = moddedSource("ModdedNativeStructureStage.java");
        int placementStart = source.indexOf("void placeVanillaStructures");
        int placementEnd = source.indexOf("private static String nativeStructureBatchContext", placementStart);
        String placement = source.substring(placementStart, placementEnd);

        assertTrue(source.contains("import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;"));
        assertTrue(placement.contains("WorldgenTerrainHeightmaps.primeStructurePlacement("));
        assertTrue(placement.contains("\"heightmap priming\""));
        assertTrue(placement.contains("heightmapStarts.add(start);"));
        assertTrue(placement.indexOf("WorldgenTerrainHeightmaps.primeStructurePlacement(")
                < placement.indexOf(".beforePieces("));
        assertTrue(source.contains("generationEngine.getHeight(x, z, false) + runtimeMinY + 1"));
        assertTrue(source.contains("generationEngine.getHeight(x, z, true) + runtimeMinY + 1"));
        assertTrue(source.contains("int minY = chunk.getMinY() + 1;"));
    }

    @Test
    public void nativeStructurePostProcessingUsesBoundedWorldgenAccess() throws IOException {
        String source = moddedSource("ModdedNativeStructureStage.java");
        int placementStart = source.indexOf("private void placeVanillaStructure");
        int placementEnd = source.indexOf("private List<List<Structure>> structuresByStep", placementStart);
        String placement = source.substring(placementStart, placementEnd);

        assertTrue(placement.contains("ModdedNativeStructureWorldgenAccess.create("));
        assertTrue(placement.contains("start.placeInChunk(boundedWorld,"));
        assertFalse(placement.contains("start.placeInChunk(world,"));
        assertTrue(placement.contains("world.setCurrentlyGenerating(null);"));
    }

    @Test
    public void structureFailurePreservesPhaseIdentityChunkAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("broken placement");
        NativeStructureGenerationException error = NativeStructureGenerationException.failure(
                "placement", "minecraft:monument", 12, -8, cause);

        assertSame(cause, error.getCause());
        assertTrue(error.getMessage().contains("placement"));
        assertTrue(error.getMessage().contains("minecraft:monument"));
        assertTrue(error.getMessage().contains("12,-8"));
        assertTrue(error.getMessage().contains("aborted"));
    }

    private static String moddedSource(String fileName) throws IOException {
        Path sourcePath = Path.of(System.getProperty("iris.moddedCommonSources"),
                "art", "arcane", "iris", "modded", fileName);
        return Files.readString(sourcePath);
    }
}
