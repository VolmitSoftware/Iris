package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.volmlib.util.director.compat.DirectorAnnotationCompatibility;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandPackUpdateWorldTest {
    @Test
    public void packNamespaceExposesAnExplicitConfirmation() throws Exception {
        DirectorNodeDescriptor command = DirectorAnnotationCompatibility.fromMethod(
                CommandPack.class.getDeclaredMethod("updateWorld", World.class, IrisDimension.class, boolean.class)
        ).orElseThrow();

        assertEquals("update-world", command.getName());
        assertTrue(command.getParameters().get(0).isRequired());
        assertTrue(command.getParameters().get(1).isRequired());
        assertFalse(command.getParameters().get(2).isRequired());
        assertEquals("false", command.getParameters().get(2).getDefaultValue());
    }

    @Test
    public void unconfirmedAndNonPersistentWorldUpdatesCannotPublish() {
        VolmitSender sender = mock(VolmitSender.class);
        World world = mock(World.class);
        IrisDimension pack = new IrisDimension();
        pack.setLoadKey("overworld");
        when(world.getName()).thenReturn("example");
        try (MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            CommandPack.stageWorldUpdate(sender, world, pack, true);
            toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
            toolbelt.when(() -> IrisToolbelt.isIrisStudioWorld(world)).thenReturn(true);
            CommandPack.stageWorldUpdate(sender, world, pack, true);
            toolbelt.when(() -> IrisToolbelt.isIrisStudioWorld(world)).thenReturn(false);
            CommandPack.stageWorldUpdate(sender, world, pack, false);

            iris.verifyNoInteractions();
            verify(sender, times(2)).sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_UPDATE_WORLD_REQUIRES_PERSISTENT_WORLD));
            verify(sender).sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_DEVELOPER_UPDATE_WORLD_WARNING,
                    MessageArgument.untrusted("world", "example"),
                    MessageArgument.untrusted("pack", "overworld")));
        }
    }

    @Test
    public void updateResultsRenderTheWorldPackAndVersion() {
        for (TextKey key : List.of(BukkitRuntimeMessages.COMMAND_PACK_UPDATE_WORLD_STAGED,
                BukkitRuntimeMessages.COMMAND_PACK_UPDATE_WORLD_UNCHANGED)) {
            String message = IrisLanguage.text(key,
                    MessageArgument.untrusted("world", "example"),
                    MessageArgument.untrusted("pack", "overworld"),
                    MessageArgument.untrusted("version", 4011));

            assertTrue(message.contains("example"));
            assertTrue(message.contains("overworld"));
            assertTrue(message.contains("4011"));
            assertFalse(message.contains("{world}"));
            assertFalse(message.contains("{pack}"));
            assertFalse(message.contains("{version}"));
        }
    }

    @Test
    public void confirmedUpdateUsesTheExistingWorldFolderAndSeed() {
        VolmitSender sender = mock(VolmitSender.class);
        World world = mock(World.class);
        IrisDimension pack = new IrisDimension();
        File folder = new File("example");
        StudioSVC studio = mock(StudioSVC.class);
        when(world.getName()).thenReturn("example");
        when(world.getWorldFolder()).thenReturn(folder);
        when(world.getSeed()).thenReturn(42L);
        try (MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            iris.when(() -> Iris.service(StudioSVC.class)).thenReturn(studio);
            toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);

            CommandPack.stageWorldUpdate(sender, world, pack, true);

            verify(studio).replaceIntoWorld(sender, pack, folder, 42L);
        }
    }
}
