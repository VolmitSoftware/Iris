package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.tree.TreeBlockCanvas;
import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Vector3i;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectFractionalScalingTest {
    @Test
    public void fractionalReductionRetainsBothLayersWhenTwoOutputVoxelsRemain() {
        NativeBlockState stem = state("minecraft:mushroom_stem");
        NativeBlockState cap = state("minecraft:red_mushroom_block");
        IrisObject source = layers(stem, cap);
        for (boolean savedOrigin : new boolean[]{false, true}) {
            for (double factor : new double[]{0.6D, 0.75D, 0.9D}) {
                IrisObject scaled = scale(source, factor, savedOrigin);

                assertEquals(2, scaled.getH());
                assertEquals(2, scaled.getBlocks().size());
                assertSame(stem, scaled.getBlocks().get(scaled.getSigned(0, 0, 0)));
                assertSame(cap, scaled.getBlocks().get(scaled.getSigned(0, 1, 0)));
            }
        }
        assertSame(stem, source.getBlocks().get(source.getSigned(0, 0, 0)));
        assertSame(cap, source.getBlocks().get(source.getSigned(0, 1, 0)));
    }

    @Test
    public void fractionalEnlargementUsesTheDominantMaterialInSharedOutputVoxels() {
        NativeBlockState lower = state("minecraft:stone");
        NativeBlockState upper = state("minecraft:dirt");
        IrisObject source = layers(lower, upper);
        for (boolean savedOrigin : new boolean[]{false, true}) {
            IrisObject scaled = scale(source, 1.75D, savedOrigin);

            assertEquals(4, scaled.getH());
            for (int y = 0; y < 4; y++) {
                assertSame(y < 2 ? lower : upper, scaled.getBlocks().get(scaled.getSigned(0, y, 0)));
            }
        }
    }

    @Test
    public void fractionalReductionResolvesMaterialCoverageAcrossAllThreeAxes() {
        IrisObject source = new IrisObject(2, 2, 2);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    source.setUnsigned(x, y, z, state("test:voxel_" + x + y + z));
                }
            }
        }

        IrisObject scaled = source.scaled(0.75D, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(8, scaled.getBlocks().size());
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    assertSame(source.getBlocks().get(source.getSigned(x, y, z)),
                            scaled.getBlocks().get(scaled.getSigned(x, y, z)));
                }
            }
        }
    }

    @Test
    public void fractionalSavedOriginPreservesNegativeCoordinatesAndTheirTiles() {
        NativeBlockState lower = state("minecraft:chest");
        NativeBlockState upper = state("minecraft:barrel");
        IrisObject source = new IrisObject(1, 4, 1);
        source.setCenter(new Vector3i(0, 3, 0));
        source.setUnsigned(0, 1, 0, lower);
        source.setUnsignedTile(0, 1, 0, tile("minecraft:chest", "lower"));
        source.setUnsigned(0, 2, 0, upper);
        source.setUnsignedTile(0, 2, 0, tile("minecraft:barrel", "upper"));

        IrisObject scaled = source.scaledAroundOrigin(0.75D, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(new Vector3i(0, 3, 0), scaled.getCenter());
        assertEquals(-3, scaled.getAABB().min().getY());
        assertEquals(0, scaled.getAABB().max().getY());
        assertSame(lower, scaled.getBlocks().get(new IrisBlockVector(0, -2, 0)));
        assertSame(upper, scaled.getBlocks().get(new IrisBlockVector(0, -1, 0)));
        assertEquals("lower", scaled.getStates().get(new IrisBlockVector(0, -2, 0)).getProperties().get("name"));
        assertEquals("upper", scaled.getStates().get(new IrisBlockVector(0, -1, 0)).getProperties().get("name"));
    }

    @Test
    public void sameMaterialTilesFollowTheirDominantSourceVoxel() {
        NativeBlockState chest = state("minecraft:chest");
        IrisObject source = layers(chest, chest);
        TileData lower = tile("minecraft:chest", "lower");
        TileData upper = tile("minecraft:chest", "upper");
        source.setUnsignedTile(0, 0, 0, lower);
        source.setUnsignedTile(0, 1, 0, upper);

        IrisObject scaled = source.scaled(0.75D, IrisObjectPlacementScaleInterpolator.NONE);

        TileData scaledLower = scaled.getStates().get(scaled.getSigned(0, 0, 0));
        TileData scaledUpper = scaled.getStates().get(scaled.getSigned(0, 1, 0));
        assertEquals(lower, scaledLower);
        assertEquals(upper, scaledUpper);
        assertNotSame(lower, scaledLower);
        assertNotSame(lower.getProperties(), scaledLower.getProperties());
        assertNotSame(upper, scaledUpper);
        assertNotSame(scaledLower.getProperties(), scaledUpper.getProperties());
    }

    @Test
    public void sameMaterialCanopyRolesAndRootContactsFollowTheDominantSourceVoxel() {
        NativeBlockState wood = state("minecraft:spruce_wood");
        IrisObject source = layers(wood, wood);
        TreeGrounding grounding = TreeGrounding.create(List.of(new TreeGrounding.Root(0, 0, 0, wood)),
                8, Set.of(new TreeBlockCanvas.Vec(0, 1, 0)));
        source.setTreeGrounding(grounding);
        for (boolean savedOrigin : new boolean[]{false, true}) {
            IrisObject scaled = scale(source, 0.75D, savedOrigin);
            int bottom = -scaled.getCenter().getY();
            TreeGrounding result = scaled.getTreeGrounding();

            assertEquals(List.of(new TreeGrounding.Root(0, bottom, 0, wood)), result.roots());
            assertFalse(result.isCanopy(0, bottom, 0));
            assertTrue(result.isCanopy(0, bottom + 1, 0));
            assertEquals(6, result.maxExtension());
        }
        assertSame(grounding, source.getTreeGrounding());
    }

    @Test
    public void canopyWinningAnUnresolvableRootCollisionDoesNotBecomeAGroundContact() {
        NativeBlockState wood = state("minecraft:spruce_wood");
        IrisObject source = layers(wood, wood);
        source.setTreeGrounding(TreeGrounding.create(List.of(new TreeGrounding.Root(0, 0, 0, wood)),
                8, Set.of(new TreeBlockCanvas.Vec(0, 1, 0))));

        IrisObject scaled = source.scaled(0.5D, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(1, scaled.getBlocks().size());
        assertTrue(scaled.getTreeGrounding().roots().isEmpty());
        assertTrue(scaled.getTreeGrounding().isCanopy(0, 0, 0));
    }

    private static IrisObject layers(NativeBlockState lower, NativeBlockState upper) {
        IrisObject source = new IrisObject(1, 2, 1);
        source.setCenter(new Vector3i(0, 0, 0));
        source.setUnsigned(0, 0, 0, lower);
        source.setUnsigned(0, 1, 0, upper);
        return source;
    }

    private static IrisObject scale(IrisObject source, double scale, boolean savedOrigin) {
        return savedOrigin ? source.scaledAroundOrigin(scale, IrisObjectPlacementScaleInterpolator.NONE)
                : source.scaled(scale, IrisObjectPlacementScaleInterpolator.NONE);
    }

    private static TileData tile(String material, String name) {
        KMap<String, Object> properties = new KMap<>();
        properties.put("name", name);
        return new TileData(material, properties);
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        return state;
    }
}
