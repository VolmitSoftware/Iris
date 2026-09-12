package art.arcane.iris.command;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.IrisCustomData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandObjectCustomBlockPlacementTest {
    @Test
    public void customPasteUsesProviderAndKeepsOriginalSemanticUndoState() {
        World world = mock(World.class);
        Block target = mock(Block.class);
        when(world.getBlockAt(7, 80, -9)).thenReturn(target);
        when(target.getType()).thenReturn(Material.STONE);
        BlockData previous = mock(BlockData.class);
        when(target.getBlockData()).thenReturn(previous);
        BlockData previousCustom = IrisCustomData.of(previous, new Identifier("craftengine", "test/lamp[lit=false]"));
        Identifier id = new Identifier("craftengine", "test/lamp[lit=true]");
        BlockData base = mock(BlockData.class);
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.nativeHandle()).thenReturn(IrisCustomData.of(base, id));
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.captureBlockData(previous)).thenReturn(previousCustom);
        when(external.placeBlock(target, id)).thenReturn(true);
        Map<Block, BlockData> changes = new HashMap<>();
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(external);
            IObjectPlacer placer = CommandObject.createPlacer(world, changes, null);

            placer.set(7, 80, -9, state);
            placer.set(7, 80, -9, state);

            assertSame(previousCustom, changes.get(target));
            verify(external).captureBlockData(previous);
            verify(external, times(2)).placeBlock(target, id);
            verify(target, times(0)).setBlockData(any(BlockData.class), anyBoolean());
        }
    }

    @Test
    public void unsupportedFurniturePreservesBaseOnlyPreview() {
        World world = mock(World.class);
        Block target = mock(Block.class);
        when(world.getBlockAt(7, 80, -9)).thenReturn(target);
        when(target.getType()).thenReturn(Material.STONE);
        BlockData base = mock(BlockData.class);
        Identifier id = new Identifier("craftengine", "test/chair[variant=alpha]");
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.nativeHandle()).thenReturn(IrisCustomData.of(base, id));
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(external);

            CommandObject.createPlacer(world, new HashMap<>(), null).set(7, 80, -9, state);

            verify(external).placeBlock(target, id);
            verify(target).setBlockData(base, false);
        }
    }
}
