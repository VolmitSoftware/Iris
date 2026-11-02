package art.arcane.iris.world.tree;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.tree.IrisTree;
import art.arcane.iris.generation.decoration.tree.IrisTreeSize;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.GenerationSessionManager;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.data.Cuboid;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TreeSVCSaplingTest {
    private static final int SAPLING_Y = -20;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void saplingBelowZeroGrowsAtItsOwnHeight() {
        Fixture fixture = new Fixture(SAPLING_Y);
        StructureGrowEvent event = fixture.event();

        fixture.grow(event);

        assertTrue(event.isCancelled());
        verify(fixture.saplings).forEach(any());
        verify(fixture.world).getBlockAt(8, SAPLING_Y, 8);
        verify(fixture.world).getBlockAt(8, SAPLING_Y + 1, 8);
        verify(fixture.block, times(2)).getState();
    }

    @Test
    public void rejectedTreeKeepsTheSapling() {
        Fixture fixture = new Fixture(0);
        StructureGrowEvent event = fixture.event();

        fixture.grow(event);

        assertTrue(event.isCancelled());
        verify(fixture.saplings, never()).forEach(any());
        verify(fixture.block, never()).getState();
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        private final TreeSVC service = spy(new TreeSVC());
        private final Cuboid saplings = mock(Cuboid.class);
        private final Block block = mock(Block.class);

        private Fixture(int lowestTreeY) {
            IrisDimension dimension = new IrisDimension();
            dimension.getTreeSettings().setEnabled(true);
            IrisComplex complex = mock(IrisComplex.class);
            when(engine.getGenerationSessions()).thenReturn(new GenerationSessionManager(true));
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getBiome(8, SAPLING_Y, 8)).thenReturn(biome(lowestTreeY));

            EngineTarget target = mock(EngineTarget.class);
            when(target.getWorld()).thenReturn(mock(IrisWorld.class));
            @SuppressWarnings("unchecked")
            ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
            IrisObject tree = tree();
            when(objects.load(anyString())).thenReturn(tree);
            IrisData data = mock(IrisData.class);
            when(data.getObjectLoader()).thenReturn(objects);
            when(data.getCompatReport()).thenReturn(mock(PackCompatReport.class));
            when(generator.getEngine()).thenReturn(engine);
            when(generator.getTarget()).thenReturn(target);
            when(generator.getData()).thenReturn(data);

            BlockData blockData = mock(BlockData.class);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(block.getBlockData()).thenReturn(blockData);
            when(block.getState()).thenReturn(mock(BlockState.class));
            when(blockData.clone()).thenReturn(blockData);
            when(saplings.getCenter()).thenReturn(new Location(world, 8, SAPLING_Y, 8));
            doReturn(saplings).when(service).getSaplings(any(), any(), any());
        }

        private static IrisObject tree() {
            NativeBlockState log = mock(NativeBlockState.class);
            when(log.isSolid()).thenReturn(true);
            when(log.key()).thenReturn("minecraft:oak_log");
            when(log.materialKey()).thenReturn("minecraft:oak_log");
            when(log.nativeHandle()).thenReturn(mock(BlockData.class));
            IrisObject object = new IrisObject(3, 3, 3);
            object.setUnsigned(1, 0, 1, log);
            object.setUnsigned(1, 1, 1, log);
            return object;
        }

        private static IrisBiome biome(int lowestTreeY) {
            IrisTree tree = new IrisTree();
            tree.getTreeTypes().add(TreeType.TREE.name());
            tree.getSizes().add(new IrisTreeSize());
            IrisObjectPlacement placement = new IrisObjectPlacement();
            placement.getPlace().add("trees/oak");
            placement.getTrees().add(tree);
            placement.setRequireSurfaceSupport(false);
            placement.getClamp().setMinimumHeight(lowestTreeY);
            IrisBiome biome = new IrisBiome();
            biome.getObjects().add(placement);
            return biome;
        }

        private StructureGrowEvent event() {
            return new StructureGrowEvent(new Location(world, 8, SAPLING_Y, 8), TreeType.TREE, false, null, List.of());
        }

        private void grow(StructureGrowEvent event) {
            try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
                 MockedStatic<J> tasks = mockStatic(J.class)) {
                toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
                toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
                service.on(event);
            }
        }
    }
}
