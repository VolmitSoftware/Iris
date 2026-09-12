package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class CommandVersionTest {
    @Test
    public void nestedVersionAndHiddenShortcutUseTheSameInstalledVersion() {
        Iris previousPlugin = Iris.instance;
        Iris plugin = mock(Iris.class);
        Iris.instance = plugin;
        CommandSender sender = mock(CommandSender.class);
        DirectorSender invocationSender = mock(DirectorSender.class);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("Iris", "3.2.1-test", "test.Plugin"));
        when(invocationSender.getName()).thenReturn("Console");
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, arguments) -> sender);
        try (MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<ComponentMessenger> messages = mockStatic(ComponentMessenger.class)) {
            DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandIris(),
                    DirectorEngineOptions.builder().contexts(contexts).build());

            assertTrue(engine.execute(new DirectorInvocation(invocationSender, "iris", List.of("debug", "version"))).isSuccess());
            assertTrue(engine.execute(new DirectorInvocation(invocationSender, "iris", List.of("version"))).isSuccess());
            messages.verify(() -> ComponentMessenger.sendMarkup(sender,
                    DirectorMiniMenu.version("Iris", "3.2.1-test", DirectorMiniMenu.Theme.irisGreen())), times(2));
            assertFalse(DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow().entries().stream()
                    .anyMatch(entry -> entry.getDescriptor().getName().equals("version")));
            assertTrue(DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow().entries().stream()
                    .anyMatch(entry -> entry.getDescriptor().getName().equals("version")));
            assertTrue(DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow().entries().stream()
                    .anyMatch(entry -> entry.getDescriptor().getName().equals("toggle")));
        } finally {
            Iris.instance = previousPlugin;
        }
    }
}
