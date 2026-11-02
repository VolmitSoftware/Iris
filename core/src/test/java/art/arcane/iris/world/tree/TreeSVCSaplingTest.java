package art.arcane.iris.world.tree;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.tree.IrisTree;
import art.arcane.iris.generation.decoration.tree.IrisTreeSize;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.GenerationSessionManager;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.object.HydrologyWaterFixture;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.data.Cuboid;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TreeSVCSaplingTest {
    private static final int SAPLING_Y = -20;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void provisionalTerrainCancelsGrowthAndKeepsTheSapling() {
        Fixture fixture = new Fixture(true);
        StructureGrowEvent event = fixture.event();

        fixture.grow(event);

        assertTrue(event.isCancelled());
        verify(fixture.saplings, never()).forEach(any());
    }

    @Test
    public void rejectedTreeKeepsTheSapling() {
        Fixture fixture = new Fixture(false);
        StructureGrowEvent event = fixture.event();

        fixture.grow(event);

        assertTrue(event.isCancelled());
        verify(fixture.saplings, never()).forEach(any());
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        private final TreeSVC service = spy(new TreeSVC());
        private final Cuboid saplings = mock(Cuboid.class);

        private Fixture(boolean provisionalWater) {
            IrisDimension dimension = new IrisDimension();
            dimension.getTreeSettings().setEnabled(true);
            IrisComplex complex = mock(IrisComplex.class);
            when(complex.hasWaterOrShore(anyInt(), anyInt())).thenAnswer(invocation -> {
                if (provisionalWater) {
                    ProvisionalSampling.mark();
                }
                return false;
            });
            when(engine.getGenerationSessions()).thenReturn(new GenerationSessionManager(true));
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getBiome(8, SAPLING_Y, 8)).thenReturn(biome());

            EngineTarget target = mock(EngineTarget.class);
            when(target.getWorld()).thenReturn(mock(IrisWorld.class));
            @SuppressWarnings("unchecked")
            ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
            IrisObject tree = HydrologyWaterFixture.pillar();
            when(objects.load(anyString())).thenReturn(tree);
            IrisData data = mock(IrisData.class);
            when(data.getObjectLoader()).thenReturn(objects);
            when(data.getCompatReport()).thenReturn(mock(PackCompatReport.class));
            when(generator.getEngine()).thenReturn(engine);
            when(generator.getTarget()).thenReturn(target);
            when(generator.getData()).thenReturn(data);

            Block block = mock(Block.class);
            BlockData blockData = mock(BlockData.class);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(block.getBlockData()).thenReturn(blockData);
            when(blockData.clone()).thenReturn(blockData);
            when(saplings.getCenter()).thenReturn(new Location(world, 8, SAPLING_Y, 8));
            doReturn(saplings).when(service).getSaplings(any(), any(), any());
        }

        private static IrisBiome biome() {
            IrisTree tree = new IrisTree();
            tree.getTreeTypes().add(TreeType.TREE.name());
            tree.getSizes().add(new IrisTreeSize());
            IrisObjectPlacement placement = new IrisObjectPlacement();
            placement.getPlace().add("trees/oak");
            placement.getTrees().add(tree);
            IrisBiome biome = new IrisBiome();
            biome.getObjects().add(placement);
            return biome;
        }

        private StructureGrowEvent event() {
            return new StructureGrowEvent(new Location(world, 8, SAPLING_Y, 8), TreeType.TREE, false, null, List.of());
        }

        private void grow(StructureGrowEvent event) {
            try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
                toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
                service.on(event);
            }
        }
    }
}
