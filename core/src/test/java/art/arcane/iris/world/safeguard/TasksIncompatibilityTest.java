package art.arcane.iris.world.safeguard;

import art.arcane.iris.testsupport.BukkitTestServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The incompatibility check used to ask the plugin manager for the exact strings "dynmap" and "Stratos".
 * Both plugins register under a different case than that - Dynmap's plugin.yml name is "dynmap" on some
 * builds and "Dynmap" on others - so the check silently passed on the servers it exists for.
 */
public class TasksIncompatibilityTest {
    private PluginManager pluginManager;

    @Before
    public void installServer() {
        BukkitTestServer.install();
        pluginManager = Bukkit.getServer().getPluginManager();
        doReturn(new Plugin[0]).when(pluginManager).getPlugins();
    }

    @After
    public void clearPlugins() {
        doReturn(new Plugin[0]).when(pluginManager).getPlugins();
    }

    @Test
    public void anIncompatiblePluginIsFoundWhateverCaseItRegistersUnder() {
        doReturn(new Plugin[]{namedPlugin("DynMap")}).when(pluginManager).getPlugins();

        CheckResult result = incompatibilities().run();

        assertEquals(Mode.WARNING, result.mode());
        assertTrue(headings(result).toString(), headings(result).contains("Dynmap"));
    }

    @Test
    public void aSecondIncompatiblePluginIsReportedTogetherWithTheFirst() {
        doReturn(new Plugin[]{namedPlugin("stratos"), namedPlugin("dynmap")}).when(pluginManager).getPlugins();

        CheckResult result = incompatibilities().run();

        assertEquals(Mode.WARNING, result.mode());
        assertTrue(headings(result).toString(), headings(result).containsAll(List.of("Dynmap", "Stratos")));
    }

    @Test
    public void aCleanPluginSetIsStable() {
        doReturn(new Plugin[]{namedPlugin("BlueMap")}).when(pluginManager).getPlugins();

        assertEquals(Mode.STABLE, incompatibilities().run().mode());
    }

    private static Task incompatibilities() {
        for (Task task : Tasks.getTasks()) {
            if ("incompatibilities".equals(task.getId())) {
                return task;
            }
        }
        throw new IllegalStateException("The incompatibilities check is missing from the safeguard task list");
    }

    private static List<String> headings(CheckResult result) {
        return result.diagnostics().stream().map(Diagnostic::getMessage).toList();
    }

    private static Plugin namedPlugin(String name) {
        Plugin plugin = mock(Plugin.class);
        doReturn(name).when(plugin).getName();
        return plugin;
    }
}
