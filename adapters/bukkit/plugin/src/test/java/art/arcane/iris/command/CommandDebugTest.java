package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.command.CommandSVC;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.command.CommandSender;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandDebugTest {
    @Test
    public void nestedDumpPreservesUploadDefaultAndLocalOnlyOption() {
        Iris previousPlugin = Iris.instance;
        Iris plugin = mock(Iris.class);
        Iris.instance = plugin;
        BukkitDebugDump dump = mock(BukkitDebugDump.class);
        CommandSender sender = mock(CommandSender.class);
        DirectorSender invocationSender = mock(DirectorSender.class);
        when(plugin.debugDump()).thenReturn(dump);
        when(invocationSender.getName()).thenReturn("Console");
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, arguments) -> sender);

        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandIris(),
                    DirectorEngineOptions.builder().contexts(contexts).build());

            assertTrue(engine.execute(new DirectorInvocation(invocationSender, "iris", List.of("debug", "dump"))).isSuccess());
            assertTrue(engine.execute(new DirectorInvocation(invocationSender, "iris", List.of("debug", "dump", "upload=false"))).isSuccess());
            verify(dump).request(sender, true);
            verify(dump).request(sender, false);
            assertEquals(List.of("dump", "toggle", "version"),
                    DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow().entries().stream()
                            .map(entry -> entry.getDescriptor().getName()).sorted().toList());
        } finally {
            Iris.instance = previousPlugin;
        }
    }

    @Test
    public void canonicalDumpReachesItsOwnPermissionCheckWithoutTheAdminPermission() {
        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            new CommandSVC().executeRoot(sender, "iris", new String[]{"debug", "dump", "upload=false"});

            scheduling.verify(() -> J.aBukkit(any(Runnable.class)));
            verify(sender, never()).hasPermission("iris.all");
        }
    }

    @Test
    public void otherDebugCommandsStillRequireTheAdminPermission() {
        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<ComponentMessenger> messages = mockStatic(ComponentMessenger.class)) {
            CommandSVC commands = new CommandSVC();
            commands.executeRoot(sender, "iris", new String[]{"debug", "version"});
            commands.executeRoot(sender, "iris", new String[]{"debug", "toggle"});
            commands.executeRoot(sender, "iris", new String[]{"debug", "ver"});
            commands.executeRoot(sender, "iris", new String[]{"debug", "tog"});

            scheduling.verifyNoInteractions();
        }
    }
}
