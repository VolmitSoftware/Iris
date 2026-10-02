package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TreeDecoratorApplierTest {
    @Test
    public void allDecoratorTargetsKeepPinnedOutputAcrossHashCollisionInsertionOrders() {
        long[] seeds = {101L, -73L, 90181L};
        long[] actual = new long[seeds.length];
        for (int i = 0; i < seeds.length; i++) {
            long hash = 0xcbf29ce484222325L;
            for (IrisTreeDecoratorTarget target : IrisTreeDecoratorTarget.values()) {
                TreeBlockCanvas forward = collisionCanvas(false);
                TreeBlockCanvas reverse = collisionCanvas(true);
                IrisProceduralTree tree = surfaceTree(target, true);
                tree.getDecorators().getFirst().setChance(0.41D).setLength(4);
                TreeDecoratorApplier.apply(forward, tree, seeds[i], List.of());
                TreeDecoratorApplier.apply(reverse, tree, seeds[i], List.of());
                assertEquals(target + ":" + seeds[i], forward.getCells(), reverse.getCells());
                List<TreeBlockCanvas.Vec> positions = new ArrayList<>(forward.getCells().keySet());
                positions.sort(null);
                for (TreeBlockCanvas.Vec position : positions) {
                    TreeBlockCanvas.Cell cell = forward.getCells().get(position);
                    hash = (hash ^ position.hashCode()) * 0x100000001b3L;
                    hash = (hash ^ cell.role().ordinal()) * 0x100000001b3L;
                    hash = (hash ^ cell.decoratorIndex()) * 0x100000001b3L;
                    hash = (hash ^ (cell.facing() == null ? 0 : cell.facing().hashCode())) * 0x100000001b3L;
                }
            }
            actual[i] = hash;
        }
        assertEquals(Arrays.toString(new long[]{4350422081939262412L, 3647024406982151043L, 7110665103288366817L}), Arrays.toString(actual));
    }

    @Test
    public void supportTendrilsChooseTheSameEqualDistanceEndpointsAcrossInsertionOrders() {
        TreeBlockCanvas forward = supportCanvas(false);
        TreeBlockCanvas reverse = supportCanvas(true);
        TreeSupport.ensureLeavesSupported(forward, 4);
        TreeSupport.ensureLeavesSupported(reverse, 4);
        assertEquals(forward.getCells(), reverse.getCells());
    }

    private static TreeBlockCanvas collisionCanvas(boolean reverse) {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        for (int index = 0; index < 24; index++) {
            int position = reverse ? 23 - index : index;
            int x = position - 12;
            int y = -31 * x;
            canvas.setTrunk(x, y, 0, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.Y);
            canvas.setLeaf(x, y, 1, TreeBlockCanvas.Role.LEAF);
        }
        return canvas;
    }

    private static TreeBlockCanvas supportCanvas(boolean reverse) {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int[] coordinates = reverse ? new int[]{3, -3} : new int[]{-3, 3};
        for (int x : coordinates) {
            canvas.setTrunk(x, 0, 0, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.Y);
            canvas.setLeaf(x * 4, 0, 2, TreeBlockCanvas.Role.LEAF);
            canvas.setLeaf(x * 4, 0, -2, TreeBlockCanvas.Role.LEAF);
        }
        return canvas;
    }

    @Test
    public void trunkAttachmentsFaceAwayFromTheirWoodSupport() {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        canvas.setTrunk(0, 2, 0, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.Y);
        IrisProceduralTree tree = surfaceTree(IrisTreeDecoratorTarget.TRUNK_SURFACE, true);

        TreeDecoratorApplier.apply(canvas, tree, 918L, List.of());

        assertOutwardFacings(canvas);
        assertEquals(1, canvas.getTrunk().size());
        assertEquals(5, canvas.getCells().size());
    }

    @Test
    public void leafAttachmentsFaceAwayFromTheirLeafSupport() {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        canvas.setLeaf(0, 2, 0, TreeBlockCanvas.Role.LEAF);
        IrisProceduralTree tree = surfaceTree(IrisTreeDecoratorTarget.LEAF_SURFACE, true);

        TreeDecoratorApplier.apply(canvas, tree, 918L, List.of());

        assertOutwardFacings(canvas);
        assertEquals(1, canvas.getLeaf().size());
    }

    @Test
    public void trunkAttachmentsKeepExplicitBlockFacingWhenAxisAwarenessIsDisabled() {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        canvas.setTrunk(0, 2, 0, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.Y);
        IrisProceduralTree tree = surfaceTree(IrisTreeDecoratorTarget.TRUNK_SURFACE, false);

        TreeDecoratorApplier.apply(canvas, tree, 918L, List.of());

        assertEquals(TreeBlockCanvas.Role.DECORATOR, canvas.get(1, 2, 0).role());
        assertNull(canvas.get(1, 2, 0).facing());
    }

    @Test
    public void branchTipsExtendOutwardThroughLeavesWithoutReplacingWood() {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        for (int x = -3; x <= 3; x++) {
            canvas.setTrunk(x, 4, 0, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.X);
        }
        canvas.setLeaf(-4, 4, 0, TreeBlockCanvas.Role.LEAF);
        canvas.setLeaf(4, 4, 0, TreeBlockCanvas.Role.LEAF);
        IrisTreeDecorator decorator = new IrisTreeDecorator()
                .setTarget(IrisTreeDecoratorTarget.BRANCH_TIP)
                .setBlock("minecraft:shroomlight")
                .setChance(1D)
                .setAxisAware(true);
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setDecorators(new KList<>(decorator));
        List<int[]> endpoints = List.of(new int[]{-3, 4, 0, 0, 0}, new int[]{3, 4, 0, 0, 0});

        TreeDecoratorApplier.apply(canvas, tree, 4321L, endpoints);

        assertEquals(TreeBlockCanvas.Role.DECORATOR, canvas.get(-5, 4, 0).role());
        assertEquals("west", canvas.get(-5, 4, 0).facing());
        assertEquals(TreeBlockCanvas.Role.DECORATOR, canvas.get(5, 4, 0).role());
        assertEquals("east", canvas.get(5, 4, 0).facing());
        assertEquals(7, canvas.getTrunk().size());
        assertEquals(2, canvas.getLeaf().size());
        assertEquals(11, canvas.getCells().size());

        TreeDecoratorApplier.apply(canvas, tree, 4321L, endpoints);

        assertEquals(11, canvas.getCells().size());
    }

    private static IrisProceduralTree surfaceTree(IrisTreeDecoratorTarget target, boolean axisAware) {
        IrisTreeDecorator decorator = new IrisTreeDecorator()
                .setTarget(target)
                .setBlock("minecraft:shelf_mushroom")
                .setChance(1D)
                .setAxisAware(axisAware);
        return new IrisProceduralTree().setDecorators(new KList<>(decorator));
    }

    private static void assertOutwardFacings(TreeBlockCanvas canvas) {
        assertEquals("east", canvas.get(1, 2, 0).facing());
        assertEquals("west", canvas.get(-1, 2, 0).facing());
        assertEquals("south", canvas.get(0, 2, 1).facing());
        assertEquals("north", canvas.get(0, 2, -1).facing());
    }
}
