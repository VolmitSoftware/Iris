package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TreeDecoratorApplierTest {
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
}
