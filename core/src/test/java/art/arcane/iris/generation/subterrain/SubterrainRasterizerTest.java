package art.arcane.iris.generation.subterrain;

import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.mockito.MockedStatic;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class SubterrainRasterizerTest {
    @Test
    public void lavaOverridePublishesLavaBlocksAndCavernStateForCenotesAndTerraces() {
        NativeBlockState lava = mock(NativeBlockState.class);
        when(lava.key()).thenReturn("minecraft:lava");
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("minecraft:lava")).thenReturn(lava);
            for (IrisSubterrainFamily family : List.of(IrisSubterrainFamily.CENOTE, IrisSubterrainFamily.TRAVERTINE_TERRACES)) {
                IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("lava-room").setFamily(family)
                        .setFluid(IrisSubterrainFluid.LAVA).setProbability(1).setLength(64).setRadius(12)
                        .setHeight(24).setPillarSpacing(0).setFormationFraction(0)
                        .setBiome("underground/lava").setWorldYRange(new IrisRange(-48, 60));
                SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, -64, 128));
                SubterrainPlan plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
                int[] fluids = {0};
                SubterrainRasterizer.rasterize(planner, plan.centerX() >> 4, plan.centerZ() >> 4, (x, y, z, cell) -> {
                    if (cell.fluid()) {
                        fluids[0]++;
                        assertEquals(SubterrainCell.Kind.LAVA, cell.kind());
                        assertEquals("minecraft:lava", cell.material());
                        assertEquals(lava, SubterrainRasterizer.state(cell));
                        MatterCavern cavern = SubterrainRasterizer.cavern(cell);
                        assertEquals(2, cavern.getLiquid());
                        assertTrue(cavern.isLava());
                        assertFalse(cavern.isWater());
                        assertEquals("underground/lava", cavern.getCustomBiome());
                        assertEquals(cell, planner.sample(x, y, z));
                    }
                });
                assertTrue(family.toString(), fluids[0] > 0);
            }
        }
    }

    @Test
    public void sealingMaterialRejectsPartialAndUnresolvedBlocks() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("sealed-room").setSolid("minecraft:oak_slab");
        NativeBlockState partial = mock(NativeBlockState.class);
        when(partial.isSolid()).thenReturn(true);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:oak_slab", false)).thenReturn(partial);
            assertThrows(IllegalArgumentException.class, () -> SubterrainRasterizer.validateMaterials(List.of(feature)));
            feature.setSolid("minecraft:missing_block");
            assertThrows(IllegalArgumentException.class, () -> SubterrainRasterizer.validateMaterials(List.of(feature)));
            NativeBlockState full = mock(NativeBlockState.class);
            when(full.key()).thenReturn("minecraft:stone");
            when(full.isSolid()).thenReturn(true);
            when(full.isOccluding()).thenReturn(true);
            feature.setSolid("minecraft:stone");
            blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(full);
            SubterrainRasterizer.validateMaterials(List.of(feature));
            when(full.isWaterLogged()).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () -> SubterrainRasterizer.validateMaterials(List.of(feature)));
            when(full.isWaterLogged()).thenReturn(false);
            when(full.key()).thenReturn("minecraft:gravel");
            feature.setSolid("minecraft:gravel");
            blocks.when(() -> B.getStateOrNull("minecraft:gravel", false)).thenReturn(full);
            assertThrows(IllegalArgumentException.class, () -> SubterrainRasterizer.validateMaterials(List.of(feature)));
        }
    }

    @Test
    public void chunksRasterizeTheSameCellsInEitherOrderAndMatchQueries() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("retained-cenote")
                .setProbability(1).setRadius(20).setHeight(24).setPillarSpacing(12)
                .setWorldYRange(new IrisRange(-48, 60));
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, -64, 128));
        SubterrainPlan plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
        int firstX = plan.centerX() >> 4;
        int firstZ = plan.centerZ() >> 4;
        Map<SubterrainPosition, SubterrainCell> forward = new HashMap<>();
        Map<SubterrainPosition, SubterrainCell> reverse = new HashMap<>();
        for (int x = firstX - 1; x <= firstX + 1; x++) {
            SubterrainRasterizer.rasterize(planner, x, firstZ, (xx, y, z, cell) -> forward.put(new SubterrainPosition(xx, y, z), cell));
        }
        for (int x = firstX + 1; x >= firstX - 1; x--) {
            SubterrainRasterizer.rasterize(planner, x, firstZ, (xx, y, z, cell) -> reverse.put(new SubterrainPosition(xx, y, z), cell));
        }
        assertFalse(forward.isEmpty());
        assertEquals(forward, reverse);
        int wet = 0;
        for (Map.Entry<SubterrainPosition, SubterrainCell> entry : forward.entrySet()) {
            SubterrainPosition position = entry.getKey();
            SubterrainCell cell = entry.getValue();
            assertEquals(cell, planner.sample(position.x(), position.y(), position.z()));
            if (cell.fluid()) {
                wet++;
                for (int[] offset : List.of(new int[]{1, 0, 0}, new int[]{-1, 0, 0}, new int[]{0, 0, 1}, new int[]{0, 0, -1}, new int[]{0, -1, 0})) {
                    SubterrainCell neighbor = planner.sample(position.x() + offset[0], position.y() + offset[1], position.z() + offset[2]);
                    assertTrue(neighbor.solid() || neighbor.kind() == cell.kind());
                }
            }
        }
        assertTrue(wet > 0);
    }

    @Test
    public void protectionKeepsSolidAndPassageWhileAllowingRoomDecoration() {
        SubterrainRoom room = new SubterrainRoom("room", "biome", IrisSubterrainFamily.CENOTE,
                0, 0, 0, 0, 0, 0, -8, 12, 10, -4, false, false, SubterrainCell.Kind.AIR);
        assertFalse(SubterrainRasterizer.protectsPlacement(SubterrainCell.OUTSIDE));
        assertFalse(SubterrainRasterizer.protectsPlacement(new SubterrainCell(SubterrainCell.Kind.AIR, "minecraft:cave_air", room)));
        assertTrue(SubterrainRasterizer.protectsPlacement(new SubterrainCell(SubterrainCell.Kind.SOLID, "minecraft:stone", room)));
        SubterrainRoom passage = new SubterrainRoom("room", "biome", IrisSubterrainFamily.CENOTE,
                0, 0, 0, 0, 0, 0, -8, 12, 10, -4, true, false, SubterrainCell.Kind.AIR);
        assertTrue(SubterrainRasterizer.protectsPlacement(new SubterrainCell(SubterrainCell.Kind.AIR, "minecraft:cave_air", passage)));
    }
}
