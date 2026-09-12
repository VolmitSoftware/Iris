package art.arcane.iris.command;

import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CommandWhatBiomeTest {
    @Test
    public void scheduledBiomeDescriptionUsesThePlayersWorldAfterCommandContextClears() {
        VolmitSender sender = mock(VolmitSender.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        Engine engine = mock(Engine.class);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        IrisBiome biome = mock(IrisBiome.class);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        when(sender.player()).thenReturn(player);
        when(sender.isPlayer()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 19, 70, -3));
        when(world.getMinHeight()).thenReturn(-64);
        when(generator.getEngine()).thenReturn(engine);
        when(engine.getBiome(19, 134, -3)).thenReturn(biome);
        when(biome.getLoadKey()).thenReturn("retained-flat");
        try (MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<IrisLanguage> language = mockStatic(IrisLanguage.class)) {
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                scheduled.set(invocation.getArgument(1));
                return true;
            });
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            language.when(() -> IrisLanguage.plain(RuntimeUiMessages.STATUS_UNREGISTERED)).thenReturn("unregistered");
            language.when(() -> IrisLanguage.text(eq(BukkitCommandMessagesExtended.COMMAND_WHAT_IBIOME),
                    any(MessageArgument.class), any(MessageArgument.class))).thenReturn("Iris biome: retained-flat");
            DirectorContext.touch(sender);
            try {
                new CommandWhat().biome();
            } finally {
                DirectorContext.remove();
            }
            assertNull(DirectorContext.get());
            assertNotNull(scheduled.get());
            verifyNoInteractions(engine);

            scheduled.get().run();

            verify(engine).getBiome(19, 134, -3);
            verify(sender).sendMessage("Iris biome: retained-flat");
            verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        }
    }
}
