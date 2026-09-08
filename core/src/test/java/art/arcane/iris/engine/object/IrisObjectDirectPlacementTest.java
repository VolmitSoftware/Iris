package art.arcane.iris.engine.object;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.IrisCustomData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisObjectDirectPlacementTest {
    @Test
    public void bothDirectPlacementMethodsUseProviderWithoutPassingProxyToBukkit() {
        BlockData base = mock(BlockData.class);
        Identifier id = new Identifier("craftengine", "test/lamp[lit=true]");
        IrisCustomData custom = IrisCustomData.of(base, id);
        IrisObject object = object(custom);
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.placeBlock(block, id)).thenReturn(true);
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(external);

            object.place(new Location(world, 7, 80, -9));
            object.placeCenterY(new Location(world, 7, 80, -9));

            verify(external, times(2)).placeBlock(block, id);
            verify(block, times(0)).setBlockData(any(BlockData.class), anyBoolean());
        }
    }

    @Test
    public void unsupportedProviderPlacesOnlyBaseDataAndVanillaStillPlacesNormally() {
        BlockData base = mock(BlockData.class);
        Identifier id = new Identifier("craftengine", "test/chair[variant=alpha]");
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(external);

            object(IrisCustomData.of(base, id)).place(new Location(world, 7, 80, -9));
            object(base).placeCenterY(new Location(world, 7, 80, -9));

            verify(external).placeBlock(block, id);
            verify(block, times(2)).setBlockData(base, false);
        }
    }

    private static IrisObject object(BlockData data) {
        IrisObject object = new IrisObject(1, 1, 1);
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.nativeHandle()).thenReturn(data);
        object.setUnsigned(0, 0, 0, state);
        return object;
    }
}
