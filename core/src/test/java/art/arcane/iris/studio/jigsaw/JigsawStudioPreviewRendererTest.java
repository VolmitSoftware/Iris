package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import org.bukkit.World;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class JigsawStudioPreviewRendererTest {
    @Test
    public void planUsesPlacedPieceCenterAndExactBlockState() throws Exception {
        PlatformBlockState stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(2, 1, 0, stone);
        PlacedStructurePiece piece = piece(object, 100, 70, -40, IrisObjectRotation.of(0, 0, 0));

        JigsawStudioPreviewRenderer.PreviewPlan plan = JigsawStudioPreviewRenderer.plan(List.of(piece));

        assertEquals(1, plan.blocks().size());
        assertEquals(
                "minecraft:stone",
                plan.blocks().get(new JigsawStudioPreviewRenderer.BlockPosition(101, 70, -41)));
        assertEquals(99, plan.bounds().minimumX());
        assertEquals(101, plan.bounds().maximumX());
        assertEquals(-41, plan.bounds().minimumZ());
        assertEquals(-39, plan.bounds().maximumZ());
    }

    @Test
    public void structureVoidRemovesAPreviouslyAuthoredPreviewCell() throws Exception {
        PlatformBlockState stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        PlatformBlockState structureVoid = mock(PlatformBlockState.class);
        when(structureVoid.key()).thenReturn("minecraft:structure_void");
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, stone);
        object.setUnsigned(0, 0, 0, structureVoid);

        JigsawStudioPreviewRenderer.PreviewPlan plan = JigsawStudioPreviewRenderer.plan(List.of(
                piece(object, 0, 65, 0, IrisObjectRotation.of(0, 0, 0))));

        assertTrue(plan.blocks().isEmpty());
        assertFalse(plan.bounds().isEmpty());
    }

    @Test
    public void emptyAssemblyProducesAnEmptyPlan() throws Exception {
        JigsawStudioPreviewRenderer.PreviewPlan plan = JigsawStudioPreviewRenderer.plan(List.of());

        assertTrue(plan.blocks().isEmpty());
        assertTrue(plan.bounds().isEmpty());
    }

    @Test
    public void spatialPreviewFloatsAboveThePlanarEditingFloor() {
        PlacedStructurePiece source = piece(
                new IrisObject(3, 3, 3),
                10,
                10,
                10,
                IrisObjectRotation.of(0, 0, 0));

        PlacedStructurePiece planar = JigsawStudioEvaluator.alignPreviewPieces(
                List.of(source),
                JigsawStudioMode.PLANAR_JIGSAW).getFirst();
        PlacedStructurePiece spatial = JigsawStudioEvaluator.alignPreviewPieces(
                List.of(source),
                JigsawStudioMode.SPATIAL_JIGSAW).getFirst();

        assertEquals(JigsawStudioLayout.FLOOR_Y + 1, planar.getMinY());
        assertEquals(JigsawStudioLayout.FLOOR_Y + 48, spatial.getMinY());
        assertEquals(planar.getMinX(), spatial.getMinX());
        assertEquals(planar.getMinZ(), spatial.getMinZ());
    }

    @Test
    public void uncertainPositionIsReappliedWhenSuccessivePlansMatch() {
        JigsawStudioPreviewRenderer.BlockPosition position =
                new JigsawStudioPreviewRenderer.BlockPosition(4, 70, 9);

        assertTrue(JigsawStudioPreviewRenderer.requiresUpdate(
                position,
                Map.of(position, "minecraft:stone"),
                Map.of(position, "minecraft:stone"),
                Set.of(position)));
        assertFalse(JigsawStudioPreviewRenderer.requiresUpdate(
                position,
                Map.of(position, "minecraft:stone"),
                Map.of(position, "minecraft:stone"),
                Set.of()));
    }

    @Test
    public void forgettingARequestDropsTrackingWithoutWorldCleanup() {
        World world = mock(World.class);
        UUID worldId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID requestId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(world.getUID()).thenReturn(worldId);
        JigsawStudioPreviewRenderer renderer = new JigsawStudioPreviewRenderer();
        renderer.render(
                world,
                requestId,
                1L,
                JigsawStudioPreviewRenderer.PreviewPlan.empty(),
                ignored -> {
                });
        assertTrue(renderer.bounds(requestId).isEmpty());
        clearInvocations(world);

        renderer.forgetRequest(requestId);

        assertNull(renderer.bounds(requestId));
        verifyNoInteractions(world);
    }

    private static PlacedStructurePiece piece(
            IrisObject object,
            int x,
            int y,
            int z,
            IrisObjectRotation rotation
    ) {
        int minimumX = x - object.getW() / 2;
        int minimumY = y - object.getH() / 2;
        int minimumZ = z - object.getD() / 2;
        return new PlacedStructurePiece(
                new IrisJigsawPiece().setObject("preview"),
                object,
                x,
                y,
                z,
                rotation,
                minimumX,
                minimumY,
                minimumZ,
                minimumX + object.getW() - 1,
                minimumY + object.getH() - 1,
                minimumZ + object.getD() - 1);
    }
}
