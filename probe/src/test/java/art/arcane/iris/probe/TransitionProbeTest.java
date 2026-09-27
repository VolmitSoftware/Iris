package art.arcane.iris.probe;

import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.world.history.SavedTerrainChunk;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public final class TransitionProbeTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rejectsWorkloadsOutsideTheBandAndExistingOutputs() throws Exception {
        Path root = temporary.newFolder().toPath();
        String[] arguments = {root.toString(), root.toString(), "overworld", "1337", "32", "2", root.resolve("result").toString()};
        assertEquals(2, TransitionProbe.Configuration.parse(arguments).chunks());
        arguments[5] = "3";
        assertThrows(IllegalArgumentException.class, () -> TransitionProbe.Configuration.parse(arguments));
        arguments[5] = "2";
        arguments[4] = "8192";
        assertThrows(IllegalArgumentException.class, () -> TransitionProbe.Configuration.parse(arguments));
        arguments[4] = "32";
        arguments[6] = root.toString();
        assertThrows(IllegalArgumentException.class, () -> TransitionProbe.Configuration.parse(arguments));
    }

    @Test
    public void missingSourcesAreRejectedWithoutCreatingSourceOrOutputDirectories() throws Exception {
        Path root = temporary.newFolder().toPath();
        for (int missingIndex : new int[]{0, 1}) {
            Path missing = root.resolve("missing-" + missingIndex);
            Path output = root.resolve("output-" + missingIndex);
            String[] arguments = {root.toString(), root.toString(), "transition", "1337", "32", "2", output.toString()};
            arguments[missingIndex] = missing.toString();
            assertThrows(IllegalArgumentException.class, () -> TransitionProbe.main(arguments));
            assertFalse(Files.exists(missing));
            assertFalse(Files.exists(output));
        }
    }

    @Test
    public void persistsActualReceiptsAcrossNegativeRegionBoundariesAndMultipleChunks() throws Exception {
        Path world = temporary.newFolder().toPath();
        SavedTerrainChunk.VoxelSource source = new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) {
                return worldY <= 5
                        ? new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false)
                        : new BoundaryColumnGeometry.Voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
            }

            @Override
            public String biome(int localX, int worldY, int localZ) {
                return "minecraft:plains";
            }
        };
        for (int x : new int[]{-33, -32, -31, 0, 1, 32}) {
            SavedTerrainChunk chunk = SavedTerrainChunk.captureBoundary(x, -1, 0, 16, "minecraft:full", source);
            TransitionProbe.writeReceipt(world, x, -1, NativeTerrainReceipt.encode(chunk, 7, "epoch"));
        }
        for (int x : new int[]{-33, -32, -31, 0, 1, 32}) {
            assertEquals("minecraft:full", SavedTerrainChunk.readStatus(world, x, -1));
            assertEquals(5, SavedTerrainChunk.read(world, x, -1, 0, 16).column(x * 16, -16).oceanFloorHeight());
            assertEquals(7, NativeTerrainReceipt.decode(SavedTerrainChunk.readReceipt(world, x, -1), "minecraft:full").activationId());
        }
    }
}
