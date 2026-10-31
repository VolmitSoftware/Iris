package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectSourceTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        NativeBlockState stone = blockState("minecraft:stone");
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(stone);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(platform.minecraftVersion()).thenReturn("26.3");
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void containedObjectKeepsItsBox() {
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 0, 1, blockState("minecraft:oak_log[axis=y]"));
        object.setUnsigned(0, 2, 2, blockState("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        ObjectSource source = ObjectSource.of(object, "objects/bush", diagnostics);

        assertTrue(diagnostics.isEmpty());
        assertEquals(0, source.baseY());
        assertEquals(3, source.volume().sizeX());
        assertEquals(3, source.volume().sizeY());
        assertEquals(3, source.volume().sizeZ());
        assertEquals(2, source.volume().blocks().size());
    }

    @Test
    public void waterlogIsLeftToThePlacementSite() {
        IrisObject object = new IrisObject(1, 2, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:tube_coral_block"));
        object.setUnsigned(0, 1, 0, blockState("minecraft:sea_pickle[pickles=2,waterlogged=true]"));

        ObjectSource source = ObjectSource.of(object, "objects/reef", new ArrayList<>());

        TemplateBlock pickle = blockAt(source, 0, 1, 0);
        assertEquals("false", pickle.state().properties().get("waterlogged"));
        assertEquals("2", pickle.state().properties().get("pickles"));
    }

    @Test
    public void rowsAndColumnsPastTheNominalBoxAreKeptAroundTheCenter() {
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, -1, 1, blockState("minecraft:oak_log[axis=y]"));
        object.setUnsigned(1, 0, 1, blockState("minecraft:oak_log[axis=y]"));
        object.setUnsigned(-1, 1, 1, blockState("minecraft:oak_log[axis=x]"));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        ObjectSource source = ObjectSource.of(object, "objects/tree", diagnostics);

        assertTrue(diagnostics.isEmpty());
        assertEquals(3, source.volume().blocks().size());
        assertEquals(-1, source.baseY());
        int centerX = source.volume().sizeX() / 2;
        int centerZ = source.volume().sizeZ() / 2;
        TemplateBlock root = blockAt(source, centerX, 0, centerZ);
        assertEquals("minecraft:oak_log", root.state().name());
        TemplateBlock branch = blockAt(source, centerX - 2, 2, centerZ);
        assertEquals("x", branch.state().properties().get("axis"));

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/tree/a", source, VariantSpec.builder().build());

        assertEquals(-1, variant.baseY());
        assertEquals(3, variant.volume().blocks().size());
    }

    @Test
    public void treeRootsFollowTheTemplateShift() {
        IrisObject object = new IrisObject(3, 3, 3);
        NativeBlockState root = blockState("minecraft:oak_wood[axis=y]");
        object.setUnsigned(1, -1, 1, root);
        object.setUnsigned(0, -2, 1, root);
        object.setUnsigned(0, -1, 1, root);
        object.setUnsigned(1, 0, 1, blockState("minecraft:oak_log[axis=y]"));
        object.setTreeGrounding(TreeGrounding.create(List.of(
                new TreeGrounding.Root(0, -2, 0, root),
                new TreeGrounding.Root(-1, -3, 0, root),
                new TreeGrounding.Root(-1, -2, 0, root)), 3, Set.of()));

        ObjectSource source = ObjectSource.of(object, "procedural/tree", new ArrayList<>());

        assertEquals(-2, source.baseY());
        assertEquals(3, source.roots().reach());
        assertEquals(List.of("0,0,1", "1,1,1"), cells(source.roots()));
        assertEquals("minecraft:oak_wood", source.roots().cells().getFirst().state().name());
    }

    @Test
    public void objectsWithoutGroundingHaveNoRoots() {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:stone"));

        assertFalse(ObjectSource.of(object, "objects/rock", new ArrayList<>()).roots().present());
    }

    private static List<String> cells(TreeRoots roots) {
        List<String> cells = new ArrayList<>();
        for (TemplateBlock cell : roots.cells()) {
            cells.add(cell.x() + "," + cell.y() + "," + cell.z());
        }
        return cells;
    }

    private static TemplateBlock blockAt(ObjectSource source, int x, int y, int z) {
        for (TemplateBlock block : source.volume().blocks()) {
            if (block.x() == x && block.y() == y && block.z() == z) {
                return block;
            }
        }
        throw new AssertionError("No block at " + x + "," + y + "," + z + " in " + source.volume().blocks());
    }

    private static NativeBlockState blockState(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.namespace()).thenReturn(key.substring(0, key.indexOf(':')));
        return state;
    }
}
