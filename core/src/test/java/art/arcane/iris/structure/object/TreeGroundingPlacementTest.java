package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;
import art.arcane.iris.generation.decoration.tree.ProceduralTreeGenerator;
import art.arcane.iris.generation.decoration.tree.TreeBlockCanvas;
import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeGroundingPlacementTest {
    private static final int SURFACE_Y = 80;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private IrisData data;
    private NativeBlockState wood;
    private NativeBlockState leaves;

    @Before
    public void bindStates() {
        wood = state("minecraft:oak_log");
        leaves = state("minecraft:oak_leaves");
        when(leaves.isFoliage()).thenReturn(true);
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
    }

    @Test
    public void flatGroundKeepsTheAuthoredBase() {
        IrisObject tree = tree(true);
        Terrain terrain = new Terrain();

        assertTrue(place(tree, terrain, placement()) >= 0);

        assertEquals(tree.getBlocks().size(), terrain.written.size());
        assertSame(wood, terrain.written.get(new Position(0, 80, 0)));
        assertSame(wood, terrain.written.get(new Position(1, 81, 0)));
        assertFalse(terrain.written.containsKey(new Position(1, 79, 0)));
    }

    @Test
    public void gentleSlopeExtendsOnlyUnsupportedRootColumns() {
        Terrain terrain = new Terrain();
        terrain.heights.put(new Column(1, 0), 77);

        assertTrue(place(tree(true), terrain, placement()) >= 0);

        for (int y = 77; y <= 81; y++) {
            assertSame(wood, terrain.written.get(new Position(1, y, 0)));
        }
        assertFalse(terrain.written.containsKey(new Position(0, 79, 0)));
        assertFalse(terrain.written.containsKey(new Position(2, 79, 0)));
    }

    @Test
    public void cliffRejectionWritesNoPartOfTheTreeOrListener() {
        Terrain terrain = new Terrain();
        terrain.heights.put(new Column(1, 0), 70);
        Map<Position, NativeBlockState> observed = new HashMap<>();

        int result = tree(true).place(0, -1, 0, terrain.placer, placement(), new RNG(42L),
                (position, state) -> observed.put(new Position(position.getX(), position.getY(), position.getZ()), state),
                null, data);

        assertEquals(-1, result);
        assertTrue(terrain.written.isEmpty());
        assertTrue(observed.isEmpty());
    }

    @Test
    public void carvedRootContactRejectsBeforeWriting() {
        Terrain terrain = new Terrain();
        terrain.carved.add(new Position(1, 79, 0));

        assertEquals(-1, place(tree(true), terrain, placement()));

        assertTrue(terrain.written.isEmpty());
    }

    @Test
    public void rotatedTranslatedRootsUseTheirFinalWorldCoordinates() {
        Terrain terrain = new Terrain();
        terrain.heights.put(new Column(13, 17), 78);
        IrisObjectPlacement placement = placement();
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(2).setY(1).setZ(3));

        int result = tree(true).place(10, -1, 20, terrain.placer, placement, new RNG(42L), data);

        assertTrue(result >= 0);
        assertSame(wood, terrain.written.get(new Position(13, 81, 18)));
        for (int y = 78; y <= 82; y++) {
            assertSame(wood, terrain.written.get(new Position(13, y, 17)));
        }
        assertFalse(terrain.written.containsKey(new Position(11, 80, 20)));
    }

    @Test
    public void rootlessTreesRequireContactWithoutAddingRoots() {
        Terrain flat = new Terrain();
        IrisObject rootless = tree(false);
        assertTrue(place(rootless, flat, placement()) >= 0);
        assertEquals(rootless.getBlocks().size(), flat.written.size());

        Terrain slope = new Terrain();
        slope.heights.put(new Column(1, 0), 79);

        assertEquals(-1, place(rootless, slope, placement()));
        assertTrue(slope.written.isEmpty());
    }

    @Test
    public void placementListenerReceivesEveryGroundingBlock() {
        Terrain terrain = new Terrain();
        terrain.heights.put(new Column(1, 0), 77);
        Map<Position, NativeBlockState> observed = new HashMap<>();

        int result = tree(true).place(0, -1, 0, terrain.placer, placement(), new RNG(42L),
                (position, state) -> observed.put(new Position(position.getX(), position.getY(), position.getZ()), state),
                null, data);

        assertTrue(result >= 0);
        assertEquals(terrain.written, observed);
        assertSame(wood, observed.get(new Position(1, 77, 0)));
    }

    @Test
    public void repeatedPlacementDoesNotMutateCachedVariants() {
        IrisObject tree = tree(true);
        Map<Position, NativeBlockState> original = snapshot(tree);
        TreeGrounding originalGrounding = tree.getTreeGrounding();
        Terrain first = new Terrain();
        first.heights.put(new Column(1, 0), 77);
        Terrain cliff = new Terrain();
        cliff.heights.put(new Column(1, 0), 60);
        Terrain second = new Terrain();
        second.heights.put(new Column(1, 0), 77);

        assertTrue(place(tree, first, placement()) >= 0);
        assertEquals(-1, place(tree, cliff, placement()));
        assertTrue(place(tree, second, placement()) >= 0);

        assertEquals(first.written, second.written);
        assertEquals(original, snapshot(tree));
        assertSame(originalGrounding, tree.getTreeGrounding());
    }

    @Test
    public void canopyOverhangDoesNotBecomeAGroundSupportFootprint() {
        Terrain terrain = new Terrain();
        terrain.heights.put(new Column(4, 0), 20);
        terrain.carved.add(new Position(4, 20, 0));

        assertTrue(place(tree(true), terrain, placement()) >= 0);

        assertSame(leaves, terrain.written.get(new Position(4, 84, 0)));
        assertFalse(terrain.written.containsKey(new Position(4, 80, 0)));
    }

    @Test
    public void genericStiltingDoesNotRepeatTheRootBase() {
        IrisObject tree = tree(true);
        Terrain ordinary = new Terrain();
        ordinary.heights.put(new Column(1, 0), 77);
        Terrain stilted = new Terrain();
        stilted.heights.put(new Column(1, 0), 77);

        assertTrue(place(tree, ordinary, placement()) >= 0);
        assertTrue(place(tree, stilted, placement().setMode(ObjectPlaceMode.CENTER_STILT)) >= 0);

        assertEquals(ordinary.written, stilted.written);
    }

    @Test
    public void generatedLeaningTreesKeepTheirTrunkAtThePlacementOrigin() {
        NativeBlockState stone = state("minecraft:stone");
        when(platform.registries().block(anyString())).thenReturn(stone);
        when(platform.registries().blockOrNull(anyString(), anyBoolean())).thenReturn(stone);
        IrisProceduralTree definition = new IrisProceduralTree();
        definition.setTrunk("minecraft:stone");
        definition.setLeaves("minecraft:stone");
        definition.setLeanAngle(40);
        definition.setLeanAzimuth(90);
        definition.setRoots(false);
        definition.setPlausible(false);

        IrisObject generated = ProceduralTreeGenerator.generate(definition, 20, new RNG(42L), data);

        assertNotNull(generated);
        assertNotNull(generated.getTreeGrounding());
        List<TreeGrounding.Root> roots = generated.getTreeGrounding().roots();
        assertEquals(1, roots.size());
        assertEquals(0, roots.getFirst().x());
        assertEquals(0, roots.getFirst().z());
        assertEquals(1 - generated.getCenter().getBlockY(), roots.getFirst().y());
        Terrain terrain = new Terrain();
        assertTrue(place(generated, terrain, placement()) >= 0);
        assertSame(stone, terrain.written.get(new Position(0, 81, 0)));
    }

    @Test
    public void scaledVariantsRetainGroundingAtCliffs() {
        IrisObject source = tree(true);
        Map<Position, NativeBlockState> original = snapshot(source);
        for (double scale : new double[]{1.5, 2, 3}) {
            IrisObjectPlacement placement = placement();
            IrisObject scaled = placement.scaleObject(new RNG(42L), source,
                    new IrisDimension().setAllObjectScaleFactor(scale));
            assertNotSame(source, scaled);
            assertGroundingMatchesBlocks(scaled);
            Terrain terrain = new Terrain();
            for (TreeGrounding.Root root : scaled.getTreeGrounding().roots()) {
                if (root.x() != 0 || root.z() != 0) {
                    terrain.heights.put(new Column(root.x(), root.z()), 40);
                }
            }
            assertFalse(terrain.heights.isEmpty());

            assertEquals(-1, place(scaled, terrain, placement));

            assertTrue(terrain.written.isEmpty());
            assertEquals(original, snapshot(source));
        }
    }

    @Test
    public void scaledRootMetadataMatchesBlocksForBothOrigins() {
        IrisObject source = tree(true);
        TreeGrounding grounding = source.getTreeGrounding();
        for (double scale : new double[]{0.5, 1.5, 2, 3}) {
            IrisObject centered = source.scaled(scale, IrisObjectPlacementScaleInterpolator.NONE);
            IrisObject savedOrigin = source.scaledAroundOrigin(scale, IrisObjectPlacementScaleInterpolator.NONE);

            assertGroundingMatchesBlocks(centered);
            assertGroundingMatchesBlocks(savedOrigin);
            assertEquals((int) Math.ceil(3 * scale), centered.getTreeGrounding().maxExtension());
            assertEquals((int) Math.ceil(3 * scale), savedOrigin.getTreeGrounding().maxExtension());
            assertSame(grounding, source.getTreeGrounding());
        }
    }

    @Test
    public void rotatedCopiesKeepMetadataOnTheirActualRootBlocks() {
        IrisObject source = tree(true);
        Map<Position, NativeBlockState> original = snapshot(source);
        TreeGrounding grounding = source.getTreeGrounding();
        for (int angle : new int[]{45, 90, 180, 270}) {
            IrisObject rotated = source.rotateCopy(IrisObjectRotation.of(0, angle, 0));

            assertGroundingMatchesBlocks(rotated);
            assertEquals(3, rotated.getTreeGrounding().maxExtension());
            assertEquals(original, snapshot(source));
            assertSame(grounding, source.getTreeGrounding());
        }
    }

    @Test
    public void shrinkwrapMovesRootMetadataAndInvalidatesItsFootprint() {
        IrisObject source = tree(true);
        Map<Position, NativeBlockState> original = snapshot(source);
        IrisObject shrunk = source.copy();
        List<IrisBlockVector> oldFootprint = List.copyOf(shrunk.getSurfaceSupportOffsets());

        shrunk.shrinkwrap();

        assertGroundingMatchesBlocks(shrunk);
        assertFalse(oldFootprint.equals(shrunk.getSurfaceSupportOffsets()));
        assertEquals(shrunk.getTreeGrounding().supportOffsets(), shrunk.getSurfaceSupportOffsets());
        assertEquals(original, snapshot(source));
    }

    @Test
    public void overlappingCanopiesPreserveWoodAndSkipListenerWrites() {
        for (Palette palette : List.of(new Palette("minecraft:oak_wood", "minecraft:birch_leaves"),
                new Palette("minecraft:warped_hyphae", "minecraft:warped_wart_block"),
                new Palette("test:trunk", "test:canopy"))) {
            wood = state(palette.trunk());
            leaves = state(palette.canopy());
            Terrain terrain = new Terrain();
            IrisObject earlier = tree(true);
            earlier.getBlocks().put(new IrisBlockVector(0, 1, 0), wood);
            assertTrue(earlier.place(4, -1, 0, terrain.placer, placement(), new RNG(42L), data) >= 0);
            IrisObject later = tree(true);
            later.getBlocks().put(new IrisBlockVector(4, 2, 0), leaves);
            later.setTreeGrounding(TreeGrounding.create(later.getTreeGrounding().roots(), 3,
                    Set.of(new TreeBlockCanvas.Vec(4, 1, 0), new TreeBlockCanvas.Vec(4, 2, 0))));
            Map<Position, NativeBlockState> observed = new HashMap<>();

            int result = later.place(0, -1, 0, terrain.placer, placement(), new RNG(42L),
                    (position, state) -> observed.put(new Position(position.getX(), position.getY(), position.getZ()), state),
                    null, data);

            assertTrue(result >= 0);
            assertSame(wood, terrain.written.get(new Position(4, 84, 0)));
            assertFalse(observed.containsKey(new Position(4, 84, 0)));
            assertSame(leaves, observed.get(new Position(4, 85, 0)));
        }
    }

    @Test
    public void canopyRolesRemainDistinctWhenWoodAndLeavesShareAMaterial() {
        leaves = wood;
        NativeBlockState existing = state("test:older_trunk");
        Terrain terrain = new Terrain();
        terrain.written.put(new Position(4, 84, 0), existing);
        terrain.written.put(new Position(0, 83, 0), existing);

        assertTrue(place(tree(true), terrain, placement()) >= 0);

        assertSame(existing, terrain.written.get(new Position(4, 84, 0)));
        assertSame(wood, terrain.written.get(new Position(0, 83, 0)));
    }

    @Test
    public void treeWoodStillReplacesOverlappingCanopy() {
        Terrain terrain = new Terrain();
        terrain.written.put(new Position(0, 83, 0), leaves);

        assertTrue(place(tree(true), terrain, placement()) >= 0);

        assertSame(wood, terrain.written.get(new Position(0, 83, 0)));
    }

    @Test
    public void ordinaryObjectCanopiesKeepTheirPlacementSemantics() {
        Terrain terrain = new Terrain();
        terrain.written.put(new Position(4, 84, 0), wood);
        IrisObject ordinary = tree(true);
        ordinary.setTreeGrounding(null);

        assertTrue(place(ordinary, terrain, placement()) >= 0);

        assertSame(leaves, terrain.written.get(new Position(4, 84, 0)));
    }

    @Test
    public void transformedCanopyRolesStillProtectExistingSolids() {
        IrisObject source = tree(true);
        IrisObject shrunk = source.copy();
        shrunk.shrinkwrap();
        for (IrisObject transformed : List.of(source.copy(), shrunk,
                source.rotateCopy(IrisObjectRotation.of(0, 90, 0)),
                source.scaled(2, IrisObjectPlacementScaleInterpolator.NONE),
                source.scaledAroundOrigin(2, IrisObjectPlacementScaleInterpolator.NONE))) {
            assertGroundingMatchesBlocks(transformed);
            TreeBlockCanvas.Vec canopy = transformed.getTreeGrounding().canopy().iterator().next();
            Position target = new Position(canopy.x(), 100 + canopy.y(), canopy.z());
            NativeBlockState existing = state("test:older_trunk");
            Terrain terrain = new Terrain();
            terrain.written.put(target, existing);

            assertTrue(transformed.place(0, 100, 0, terrain.placer, placement(), new RNG(42L), data) >= 0);

            assertSame(existing, terrain.written.get(target));
        }
    }

    private int place(IrisObject tree, Terrain terrain, IrisObjectPlacement placement) {
        return tree.place(0, -1, 0, terrain.placer, placement, new RNG(42L), data);
    }

    private IrisObjectPlacement placement() {
        return new IrisObjectPlacement().setMode(ObjectPlaceMode.CENTER_HEIGHT)
                .setRotation(IrisObjectRotation.of(0, 0, 0)).setSurfaceSupportBuffer(0);
    }

    private IrisObject tree(boolean rooted) {
        IrisObject tree = new IrisObject(9, 7, 3);
        tree.setLoader(data);
        tree.getBlocks().put(new IrisBlockVector(0, -2, 0), wood);
        tree.getBlocks().put(new IrisBlockVector(0, -1, 0), wood);
        tree.getBlocks().put(new IrisBlockVector(0, 0, 0), wood);
        tree.getBlocks().put(new IrisBlockVector(1, -2, 0), wood);
        tree.getBlocks().put(new IrisBlockVector(4, 0, 0), wood);
        tree.getBlocks().put(new IrisBlockVector(4, 1, 0), leaves);
        List<TreeGrounding.Root> roots = new ArrayList<>();
        if (rooted) {
            tree.getBlocks().put(new IrisBlockVector(0, -3, 0), wood);
            roots.add(new TreeGrounding.Root(0, -3, 0, wood));
        } else {
            roots.add(new TreeGrounding.Root(0, -2, 0, wood));
        }
        roots.add(new TreeGrounding.Root(1, -2, 0, wood));
        tree.setTreeGrounding(TreeGrounding.create(roots, rooted ? 3 : 0,
                Set.of(new TreeBlockCanvas.Vec(4, 1, 0))));
        return tree;
    }

    private static Map<Position, NativeBlockState> snapshot(IrisObject tree) {
        Map<Position, NativeBlockState> snapshot = new HashMap<>();
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : tree.getBlocks()) {
            IrisBlockVector position = entry.getKey();
            snapshot.put(new Position(position.getBlockX(), position.getBlockY(), position.getBlockZ()), entry.getValue());
        }
        return snapshot;
    }

    private void assertGroundingMatchesBlocks(IrisObject tree) {
        TreeGrounding grounding = tree.getTreeGrounding();
        assertNotNull(grounding);
        assertFalse(grounding.roots().isEmpty());
        Set<Column> columns = new HashSet<>();
        for (TreeGrounding.Root root : grounding.roots()) {
            assertTrue(columns.add(new Column(root.x(), root.z())));
            assertSame(root.state(), tree.getBlocks().get(new IrisBlockVector(root.x(), root.y(), root.z())));
            assertFalse(root.state().isFoliage());
        }
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : tree.getBlocks()) {
            IrisBlockVector cell = entry.getKey();
            assertEquals(entry.getValue() == leaves, grounding.isCanopy(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ()));
        }
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(true);
        when(state.isOccluding()).thenReturn(true);
        return state;
    }

    private static final class Terrain {
        private final IObjectPlacer placer = mock(IObjectPlacer.class);
        private final Map<Column, Integer> heights = new HashMap<>();
        private final Map<Position, NativeBlockState> written = new HashMap<>();
        private final Set<Position> carved = new HashSet<>();

        private Terrain() {
            when(placer.beginTreeCanopy(any())).thenCallRealMethod();
            when(placer.getHighest(anyInt(), anyInt(), any())).thenAnswer(invocation ->
                    surface(invocation.getArgument(0), invocation.getArgument(1)));
            when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenAnswer(invocation ->
                    surface(invocation.getArgument(0), invocation.getArgument(1)));
            when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int x = invocation.getArgument(0);
                int y = invocation.getArgument(1);
                int z = invocation.getArgument(2);
                return y >= 0 && y <= surface(x, z) && !carved.contains(new Position(x, y, z));
            });
            when(placer.isCarved(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    carved.contains(new Position(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
            when(placer.get(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    written.get(new Position(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
            doAnswer(invocation -> {
                Position position = new Position(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                NativeBlockState state = invocation.getArgument(3);
                written.put(position, state);
                return null;
            }).when(placer).set(anyInt(), anyInt(), anyInt(), any());
        }

        private int surface(int x, int z) {
            return heights.getOrDefault(new Column(x, z), SURFACE_Y);
        }
    }

    private record Column(int x, int z) {
    }

    private record Position(int x, int y, int z) {
    }

    private record Palette(String trunk, String canopy) {
    }
}
