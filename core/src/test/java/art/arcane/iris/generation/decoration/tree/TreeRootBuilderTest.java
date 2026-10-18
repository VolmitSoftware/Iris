package art.arcane.iris.generation.decoration.tree;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeRootBuilderTest {
    @Test
    public void everyRootHasAFaceConnectedPathToExistingWood() {
        for (IrisTreeRootStyle style : IrisTreeRootStyle.values()) {
            for (int width : new int[]{1, 2, 5}) {
                for (long seed : new long[]{1L, 42L, 5923L}) {
                    IrisProceduralTree tree = new IrisProceduralTree();
                    tree.setRootStyle(style);
                    tree.setTrunkWidth(width);
                    tree.setRootDepth(8);
                    tree.setRootFlare(7);
                    tree.setLeanAngle(40);
                    tree.setLeanAzimuth(45);
                    TreeBlockCanvas canvas = trunk(tree, 24);
                    Set<TreeBlockCanvas.Vec> original = new HashSet<>(canvas.getTrunk());

                    TreeRootBuilder.build(canvas, tree, 24, seed);

                    assertAllRootsConnected(canvas, original);
                }
            }
        }
    }

    @Test
    public void automaticFlareRemainsCompactForTallThinTrees() {
        IrisProceduralTree tree = new IrisProceduralTree();
        for (int height : new int[]{8, 30, 80}) {
            TreeBlockCanvas canvas = trunk(tree, height);

            TreeRootBuilder.build(canvas, tree, height, 42L);

            for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
                if (cell.y() < 0) {
                    assertTrue(Math.abs(cell.x()) <= 2);
                    assertTrue(Math.abs(cell.z()) <= 2);
                }
            }
        }
    }

    @Test
    public void taprootNarrowsToOneCellWithoutExpandingTheBase() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunkWidth(5);
        tree.setRootDepth(7);
        tree.setRootStyle(IrisTreeRootStyle.TAPROOT);
        TreeBlockCanvas canvas = trunk(tree, 20);
        Set<TreeBlockCanvas.Vec> original = new HashSet<>(canvas.getTrunk());

        TreeRootBuilder.build(canvas, tree, 20, 42L);

        int previousSize = 25;
        for (int depth = 1; depth <= 7; depth++) {
            int size = 0;
            for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
                if (cell.y() == -depth) {
                    size++;
                    assertTrue(original.contains(new TreeBlockCanvas.Vec(cell.x(), 0, cell.z())));
                }
            }
            assertTrue(size > 0);
            assertTrue(size <= previousSize);
            previousSize = size;
        }
        assertEquals(1, previousSize);
    }

    @Test
    public void buttressTipsEndAtDifferentDepths() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setRootDepth(8);
        tree.setRootFlare(7);
        TreeBlockCanvas canvas = trunk(tree, 20);

        TreeRootBuilder.build(canvas, tree, 20, 42L);

        Map<TreeBlockCanvas.Vec, Integer> outerDepths = new HashMap<>();
        for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
            if (cell.y() < 0 && Math.hypot(cell.x(), cell.z()) >= 5) {
                TreeBlockCanvas.Vec column = new TreeBlockCanvas.Vec(cell.x(), 0, cell.z());
                outerDepths.merge(column, cell.y(), Math::min);
            }
        }
        assertTrue(new HashSet<>(outerDepths.values()).size() > 1);
    }

    @Test
    public void explicitFlareKeepsWideStiltRoots() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setRootStyle(IrisTreeRootStyle.STILT);
        tree.setRootFlare(8);
        tree.setRootDepth(5);
        TreeBlockCanvas canvas = trunk(tree, 24);
        Set<TreeBlockCanvas.Vec> original = new HashSet<>(canvas.getTrunk());

        TreeRootBuilder.build(canvas, tree, 24, 42L);

        double reach = 0;
        boolean aboveGround = false;
        for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
            if (!original.contains(cell)) {
                reach = Math.max(reach, Math.hypot(cell.x(), cell.z()));
                aboveGround |= cell.y() > 0;
            }
        }
        assertTrue(reach >= 6);
        assertTrue(aboveGround);
        assertAllRootsConnected(canvas, original);
    }

    @Test
    public void stiltRootsAttachToTheActualLeaningTrunk() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setRootStyle(IrisTreeRootStyle.STILT);
        tree.setLeanAngle(55);
        tree.setLeanAzimuth(90);
        tree.setRootFlare(3);
        TreeBlockCanvas canvas = trunk(tree, 24);
        Set<TreeBlockCanvas.Vec> original = new HashSet<>(canvas.getTrunk());

        TreeRootBuilder.build(canvas, tree, 24, 42L);

        boolean attachedAboveGround = false;
        for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
            if (!original.contains(cell) && cell.y() >= 4) {
                for (TreeBlockCanvas.Vec neighbor : neighbors(cell)) {
                    attachedAboveGround |= original.contains(neighbor);
                }
            }
        }
        assertTrue(attachedAboveGround);
        assertAllRootsConnected(canvas, original);
    }

    @Test
    public void intersectingFoliageDoesNotSeverRoots() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setRootDepth(5);
        tree.setRootFlare(5);
        TreeBlockCanvas canvas = trunk(tree, 20);
        Set<TreeBlockCanvas.Vec> original = new HashSet<>(canvas.getTrunk());
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                canvas.setLeaf(x, -1, z, TreeBlockCanvas.Role.LEAF);
            }
        }

        TreeRootBuilder.build(canvas, tree, 20, 42L);

        assertAllRootsConnected(canvas, original);
        assertEquals(TreeBlockCanvas.Role.TRUNK, canvas.get(0, -1, 0).role());
    }

    @Test
    public void rootGeometryIsDeterministic() {
        for (IrisTreeRootStyle style : IrisTreeRootStyle.values()) {
            IrisProceduralTree tree = new IrisProceduralTree();
            tree.setRootStyle(style);
            tree.setTrunkWidth(2);
            TreeBlockCanvas first = trunk(tree, 30);
            TreeBlockCanvas second = trunk(tree, 30);

            TreeRootBuilder.build(first, tree, 30, 5923L);
            TreeRootBuilder.build(second, tree, 30, 5923L);

            assertEquals(first.getCells(), second.getCells());
        }
    }

    private static TreeBlockCanvas trunk(IrisProceduralTree tree, int height) {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        TreeTrunkBuilder.build(canvas, tree, height);
        return canvas;
    }

    private static void assertAllRootsConnected(TreeBlockCanvas canvas, Set<TreeBlockCanvas.Vec> original) {
        Set<TreeBlockCanvas.Vec> reached = new HashSet<>(original);
        ArrayDeque<TreeBlockCanvas.Vec> pending = new ArrayDeque<>(original);
        while (!pending.isEmpty()) {
            TreeBlockCanvas.Vec cell = pending.removeFirst();
            for (TreeBlockCanvas.Vec neighbor : neighbors(cell)) {
                if (canvas.getTrunk().contains(neighbor) && reached.add(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        Set<TreeBlockCanvas.Vec> roots = new HashSet<>(canvas.getTrunk());
        roots.removeAll(original);
        assertFalse(roots.isEmpty());
        assertEquals(canvas.getTrunk(), reached);
    }

    private static TreeBlockCanvas.Vec[] neighbors(TreeBlockCanvas.Vec cell) {
        return new TreeBlockCanvas.Vec[]{
                new TreeBlockCanvas.Vec(cell.x() - 1, cell.y(), cell.z()),
                new TreeBlockCanvas.Vec(cell.x() + 1, cell.y(), cell.z()),
                new TreeBlockCanvas.Vec(cell.x(), cell.y() - 1, cell.z()),
                new TreeBlockCanvas.Vec(cell.x(), cell.y() + 1, cell.z()),
                new TreeBlockCanvas.Vec(cell.x(), cell.y(), cell.z() - 1),
                new TreeBlockCanvas.Vec(cell.x(), cell.y(), cell.z() + 1)
        };
    }
}
