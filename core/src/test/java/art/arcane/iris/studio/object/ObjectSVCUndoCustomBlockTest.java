package art.arcane.iris.studio.object;

import art.arcane.iris.integration.ExternalDataSVC;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.world.task.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ObjectSVCUndoCustomBlockTest {
    @Test
    public void undoRestoresSemanticCustomStateOnItsOwningRegion() {
        Block block = mock(Block.class);
        Location location = new Location(mock(World.class), 7, 80, -9);
        when(block.getLocation()).thenReturn(location);
        Identifier id = new Identifier("craftengine", "test/lamp[lit=false]");
        BlockData custom = IrisCustomData.of(mock(BlockData.class), id);
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.placeBlock(block, id)).thenReturn(true);
        List<Runnable> ownerTasks = new ArrayList<>();
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class);
             MockedStatic<J> scheduling = mockStatic(J.class)) {
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(external);
            scheduling.when(() -> J.runGlobal(any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return true;
            });
            scheduling.when(() -> J.s(any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
                ownerTasks.add(invocation.getArgument(1, Runnable.class));
                return true;
            });
            ObjectSVC undo = new ObjectSVC();
            undo.addChanges(new HashMap<>(Map.of(block, custom)));

            undo.revertChanges(1);

            assertEquals(1, ownerTasks.size());
            verifyNoInteractions(external);
            ownerTasks.getFirst().run();
            verify(external).placeBlock(block, id);
            verify(block, times(0)).setBlockData(any(BlockData.class), anyBoolean());
        }
    }
}
