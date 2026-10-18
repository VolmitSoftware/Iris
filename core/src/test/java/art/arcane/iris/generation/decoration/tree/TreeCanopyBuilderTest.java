package art.arcane.iris.generation.decoration.tree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeCanopyBuilderTest {
    @Test
    public void laterBranchesGrowThroughEarlierLeafClusters() {
        IrisProceduralTree tree = branchedTree();
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        double[][] offsets = TreeTrunkBuilder.build(canvas, tree, 6).getFirst().offsets();

        TreeCanopyBuilder.build(canvas, tree, 6, offsets, 0, 42L, null);

        for (int y = 0; y < 6; y++) {
            for (int z = 0; z <= 4; z++) {
                assertTrue(canvas.getTrunk().contains(new TreeBlockCanvas.Vec(0, y, z)));
                assertFalse(canvas.getLeaf().contains(new TreeBlockCanvas.Vec(0, y, z)));
            }
        }
    }

    @Test
    public void branchWoodReplacesBothLeafRolesButKeepsWoodAndAccents() {
        IrisProceduralTree tree = branchedTree();
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        double[][] offsets = TreeTrunkBuilder.build(canvas, tree, 6).getFirst().offsets();
        canvas.setLeaf(0, 1, 1, TreeBlockCanvas.Role.LEAF);
        canvas.setLeaf(0, 1, 2, TreeBlockCanvas.Role.SECONDARY_LEAF);
        canvas.setTrunk(0, 1, 3, TreeBlockCanvas.Role.SECONDARY_TRUNK, TreeBlockCanvas.Axis.X);
        canvas.setDecor(0, 1, 4, 0, "north");

        TreeCanopyBuilder.build(canvas, tree, 6, offsets, 0, 42L, null);

        assertEquals(TreeBlockCanvas.Role.TRUNK, canvas.get(0, 1, 1).role());
        assertEquals(TreeBlockCanvas.Role.TRUNK, canvas.get(0, 1, 2).role());
        assertEquals(TreeBlockCanvas.Role.SECONDARY_TRUNK, canvas.get(0, 1, 3).role());
        assertEquals(TreeBlockCanvas.Axis.X, canvas.get(0, 1, 3).axis());
        assertEquals(TreeBlockCanvas.Role.DECORATOR, canvas.get(0, 1, 4).role());
        assertFalse(canvas.getLeaf().contains(new TreeBlockCanvas.Vec(0, 1, 1)));
        assertFalse(canvas.getLeaf().contains(new TreeBlockCanvas.Vec(0, 1, 2)));
    }

    private static IrisProceduralTree branchedTree() {
        IrisProceduralTree tree = new IrisProceduralTree();
        IrisTreeBranches branches = new IrisTreeBranches();
        branches.setProbabilityFunction(IrisTreeBranchProbability.CONSTANT);
        branches.setProbabilityConstant(1);
        branches.setLengthFunction(IrisTreeFunction.CONSTANT);
        branches.setLengthConstant(4);
        branches.setAzimuthMode(IrisTreeAzimuthMode.CONSTANT);
        branches.setAzimuth(0);
        branches.setElevation(0);
        branches.setClusterRadius(2);
        branches.setClusterMode(IrisTreeLeafMode.FILLED);
        tree.getCanopy().setBranches(branches);
        return tree;
    }
}
