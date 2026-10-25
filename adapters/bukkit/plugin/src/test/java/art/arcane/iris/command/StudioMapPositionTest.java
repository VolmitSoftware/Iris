package art.arcane.iris.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.studio.map.MapWindow;
import art.arcane.iris.studio.map.PaletteExplorerStart;
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.task.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class StudioMapPositionTest {
    @Test
    public void mapCapturesItsStartingPositionOnThePlayerScheduler() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(true);
        World world = mock(World.class);
        Player player = mock(Player.class);
        VolmitSender sender = mock(VolmitSender.class);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        Engine engine = mock(Engine.class);
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        AtomicBoolean owned = new AtomicBoolean();
        AtomicReference<Runnable> task = new AtomicReference<>();
        AtomicReference<PaletteExplorerStart> start = new AtomicReference<>();
        when(world.getUID()).thenReturn(worldId);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenAnswer(invocation -> {
            assertTrue("world read must use the player scheduler", owned.get());
            return world;
        });
        when(player.getLocation()).thenAnswer(invocation -> {
            assertTrue("location read must use the player scheduler", owned.get());
            return new Location(world, 33.25D, 91D, -72.5D);
        });
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player);
        when(generator.getEngine()).thenReturn(engine);
        CommandStudio command = new CommandStudio() {
            @Override
            public VolmitSender sender() {
                return sender;
            }
        };
        try (MockedStatic<GuiHost> host = mockStatic(GuiHost.class);
             MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<MapWindow> windows = mockStatic(MapWindow.class)) {
            host.when(GuiHost::isAvailable).thenReturn(true);
            toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(0), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        task.set(invocation.getArgument(1));
                        return true;
                    });
            windows.when(() -> MapWindow.openMap(any(PaletteExplorerStart.class))).thenAnswer(invocation -> {
                start.set(invocation.getArgument(0));
                return null;
            });

            command.map(world);
            assertNull(start.get());
            owned.set(true);
            task.get().run();

            assertEquals(engine, start.get().engine());
            assertEquals(playerId, start.get().playerId());
            assertEquals(worldId, start.get().worldId());
            assertEquals(33.25D, start.get().centerX(), 0D);
            assertEquals(-72.5D, start.get().centerZ(), 0D);
        } finally {
            IrisSettings.settings = previous;
        }
    }
}
