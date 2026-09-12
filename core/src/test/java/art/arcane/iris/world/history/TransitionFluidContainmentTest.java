package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TransitionFluidContainmentTest {
    private static final BoundaryColumnGeometry.Voxel AIR = voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR);
    private static final BoundaryColumnGeometry.Voxel BANK = voxel("minecraft:deepslate", BoundaryColumnGeometry.Phase.SOLID);
    private static final BoundaryColumnGeometry.Voxel WATER = voxel("minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID);

    @Test
    public void fluidBanksUseTheSameRawHaloAcrossChunkEdgesAndRequestOrder() throws Exception {
        TransitionGenerationPlan plan = band();
        SavedTerrainChunk left = raw(0, 0);
        SavedTerrainChunk right = raw(1, 0);
        TransitionFluidContainment.ColumnSource source = (x, z) -> {
            List<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(16);
            for (int y = 0; y < 16; y++) {
                voxels.add(y == 0 ? BANK : x < 16 && y <= 4 ? WATER : AIR);
            }
            return BoundaryColumnGeometry.fromVoxels(0, voxels);
        };

        SavedTerrainChunk leftFirst = TransitionFluidContainment.contain(left, plan, source);
        SavedTerrainChunk rightSecond = TransitionFluidContainment.contain(right, plan, source);
        SavedTerrainChunk rightFirst = TransitionFluidContainment.contain(right, plan, source);
        SavedTerrainChunk leftSecond = TransitionFluidContainment.contain(left, plan, source);

        assertEquals(BANK, leftFirst.column(15, 8).geometry().voxelAt(2));
        assertEquals(WATER, leftFirst.column(14, 8).geometry().voxelAt(2));
        assertEquals(AIR, rightSecond.column(16, 8).geometry().voxelAt(2));
        assertEquals(leftFirst.column(15, 8).geometry(), leftSecond.column(15, 8).geometry());
        assertEquals(rightSecond.column(16, 8).geometry(), rightFirst.column(16, 8).geometry());
        assertEquals(WATER, left.column(15, 8).geometry().voxelAt(2));
    }

    @Test
    public void unsupportedFluidGetsABottomWhileOpenFluidSurfacesStayOpen() throws Exception {
        SavedTerrainChunk floating = SavedTerrainChunk.capture(0, 0, 0, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                return y >= 3 && y <= 5 ? WATER : AIR;
            }

            @Override
            public String biome(int x, int y, int z) {
                return "minecraft:plains";
            }
        });
        SavedTerrainChunk result = TransitionFluidContainment.contain(floating, band(),
                (x, z) -> floating.column(Math.floorMod(x, 16), Math.floorMod(z, 16)).geometry());

        assertEquals("minecraft:stone", result.column(8, 8).geometry().voxelAt(3).stateKey());
        assertEquals(WATER, result.column(8, 8).geometry().voxelAt(4));
        assertEquals(WATER, result.column(8, 8).geometry().voxelAt(5));
        assertEquals(AIR, result.column(8, 8).geometry().voxelAt(6));
    }

    @Test
    public void historicalAndOutsideBandColumnsStayUnchanged() throws Exception {
        SavedTerrainChunk raw = raw(0, 0);
        TransitionGenerationPlan historical = band();
        when(historical.isHistoricalBlock(anyInt(), anyInt())).thenReturn(true);
        TransitionGenerationPlan outside = band();
        when(outside.newEpochWeightAt(anyInt(), anyInt())).thenReturn(1D);
        TransitionFluidContainment.ColumnSource forbidden = (x, z) -> {
            throw new AssertionError("Immutable columns must not need a halo");
        };

        assertSame(raw, TransitionFluidContainment.contain(raw, historical, forbidden));
        assertSame(raw, TransitionFluidContainment.contain(raw, outside, forbidden));
    }

    @Test
    public void missingHaloFailsInsteadOfTreatingUnknownTerrainAsAir() throws Exception {
        assertThrows(IOException.class, () -> TransitionFluidContainment.contain(raw(0, 0), band(),
                (x, z) -> { throw new IOException("Missing saved boundary column"); }));
    }

    @Test
    public void protectedFluidAndWaterloggedStatesArePreserved() throws Exception {
        BoundaryColumnGeometry.Voxel protectedWater = new BoundaryColumnGeometry.Voxel(
                WATER.stateKey(), WATER.phase(), WATER.fluidStateKey(), true);
        BoundaryColumnGeometry.Voxel waterlogged = new BoundaryColumnGeometry.Voxel(
                "minecraft:oak_slab[type=bottom,waterlogged=true]", BoundaryColumnGeometry.Phase.SOLID,
                "minecraft:water[level=0]", false);
        TransitionFluidContainment.ColumnSource forbidden = (x, z) -> {
            throw new AssertionError("Protected or waterlogged blocks must not be replaced");
        };
        for (BoundaryColumnGeometry.Voxel voxel : new BoundaryColumnGeometry.Voxel[]{protectedWater, waterlogged}) {
            SavedTerrainChunk raw = uniform(voxel);
            assertSame(raw, TransitionFluidContainment.contain(raw, band(), forbidden));
        }
    }

    @Test
    public void unsupportedFluidCannotUseFallingSandAsItsBank() throws Exception {
        BoundaryColumnGeometry.Voxel sand = voxel("minecraft:sand", BoundaryColumnGeometry.Phase.SOLID);
        SavedTerrainChunk raw = SavedTerrainChunk.capture(0, 0, 0, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                return y == 0 ? sand : y == 2 ? WATER : AIR;
            }

            @Override
            public String biome(int x, int y, int z) {
                return "minecraft:plains";
            }
        });
        SavedTerrainChunk corrected = TransitionFluidContainment.contain(raw, band(),
                (x, z) -> raw.column(Math.floorMod(x, 16), Math.floorMod(z, 16)).geometry());
        assertEquals("minecraft:stone", corrected.column(8, 8).geometry().voxelAt(2).stateKey());
        assertEquals(sand, corrected.column(8, 8).geometry().voxelAt(0));
    }

    private static SavedTerrainChunk uniform(BoundaryColumnGeometry.Voxel voxel) throws IOException {
        return SavedTerrainChunk.capture(0, 0, 0, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                return voxel;
            }

            @Override
            public String biome(int x, int y, int z) {
                return "minecraft:plains";
            }
        });
    }

    private static TransitionGenerationPlan band() {
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.newEpochWeightAt(anyInt(), anyInt())).thenReturn(0.5D);
        return plan;
    }

    private static SavedTerrainChunk raw(int chunkX, int chunkZ) throws IOException {
        return SavedTerrainChunk.capture(chunkX, chunkZ, 0, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                if (y == 0) {
                    return BANK;
                }
                return chunkX * 16 + x < 16 && y <= 4 ? WATER : AIR;
            }

            @Override
            public String biome(int x, int y, int z) {
                return "minecraft:plains";
            }
        });
    }

    private static BoundaryColumnGeometry.Voxel voxel(String key, BoundaryColumnGeometry.Phase phase) {
        return new BoundaryColumnGeometry.Voxel(key, phase, phase == BoundaryColumnGeometry.Phase.FLUID ? key : "", false);
    }
}
