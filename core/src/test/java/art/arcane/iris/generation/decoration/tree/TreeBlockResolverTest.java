package art.arcane.iris.generation.decoration.tree;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class TreeBlockResolverTest {
    @Test
    public void largeSecondaryWeightsKeepTheirRelativeDistribution() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setSeed(91874L);
        tree.setLeavesPalette(palette("oak_leaves", "birch_leaves"));
        PlatformBlockState blossom = mock(PlatformBlockState.class);
        PlatformBlockState light = mock(PlatformBlockState.class);
        List<PlatformBlockState> normal = new ArrayList<>();
        List<PlatformBlockState> large = new ArrayList<>();
        TreeBlockCanvas.Cell cell = new TreeBlockCanvas.Cell(
                TreeBlockCanvas.Role.SECONDARY_LEAF, TreeBlockCanvas.Axis.NONE, false, -1, null);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:cherry_leaves", false)).thenReturn(blossom);
            blocks.when(() -> B.getStateOrNull("minecraft:shroomlight", false)).thenReturn(light);
            for (int weight : new int[]{1, Integer.MAX_VALUE}) {
                tree.setWeightedSecondaryLeaves(new KList<>(
                        new IrisTreeSecondaryLeaf("minecraft:cherry_leaves", weight),
                        new IrisTreeSecondaryLeaf("minecraft:shroomlight", weight)));
                List<PlatformBlockState> resolved = weight == 1 ? normal : large;
                for (int x = -16; x <= 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        resolved.add(TreeBlockResolver.resolve(tree, null, cell,
                                new TreeBlockCanvas.Vec(x, y, x + y), new RNG(tree.getSeed())));
                    }
                }
            }
        }
        assertTrue(normal.contains(blossom));
        assertTrue(normal.contains(light));
        assertEquals(normal, large);
    }

    @Test
    public void sharedPaletteRandomSourceMatchesFreshPerVoxelSources() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setSeed(84261L);
        tree.setTrunkPalette(palette("oak_log", "spruce_log"));
        tree.setLeavesPalette(palette("oak_leaves", "birch_leaves"));
        tree.setSecondaryTrunkPalette(palette("birch_log", "jungle_log"));
        tree.setSecondaryLeavesPalette(palette("spruce_leaves", "jungle_leaves"));
        RNG shared = new RNG(tree.getSeed());
        List<String> freshBlocks = new ArrayList<>();
        List<String> sharedBlocks = new ArrayList<>();
        Set<String> materials = new HashSet<>();
        TreeBlockCanvas.Role[] roles = {
                TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Role.LEAF,
                TreeBlockCanvas.Role.SECONDARY_TRUNK, TreeBlockCanvas.Role.SECONDARY_LEAF
        };
        for (int x = -8; x <= 8; x++) {
            for (int y = 0; y < 24; y++) {
                TreeBlockCanvas.Vec position = new TreeBlockCanvas.Vec(x, y, x - y);
                TreeBlockCanvas.Cell cell = new TreeBlockCanvas.Cell(
                        roles[Math.floorMod(x + y, roles.length)], TreeBlockCanvas.Axis.NONE, false, -1, null);
                PlatformBlockState expected = TreeBlockResolver.resolve(tree, null, cell, position, new RNG(tree.getSeed()));
                PlatformBlockState actual = TreeBlockResolver.resolve(tree, null, cell, position, shared);
                freshBlocks.add(position + ":" + expected.key());
                sharedBlocks.add(position + ":" + actual.key());
                materials.add(actual.key());
            }
        }

        assertEquals(freshBlocks, sharedBlocks);
        assertEquals(new RNG(tree.getSeed()).nextLong(), shared.nextLong());
        assertTrue(materials.size() > roles.length);
    }

    private static IrisMaterialPalette palette(String first, String second) {
        IrisBlockData firstBlock = block(first);
        IrisBlockData secondBlock = block(second);
        return new IrisMaterialPalette().setPalette(new KList<>(firstBlock, secondBlock));
    }

    private static IrisBlockData block(String material) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn("minecraft:" + material);
        IrisBlockData block = mock(IrisBlockData.class);
        when(block.getWeight()).thenReturn(1);
        when(block.getBlockData(null)).thenReturn(state);
        return block;
    }
}
