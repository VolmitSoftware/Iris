package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TreeDecoratorApplierTest {
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
