package art.arcane.iris;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class IrisVersionNumberTest {
    @Test
    public void releaseAndQualifiedBuildsUseTheSameVersionNumber() {
        for (String version : List.of("4.3.3", "4.3.3-SNAPSHOT", "4.3.3-test")) {
            assertEquals(version, 433, plugin(version).getIrisVersion());
        }
    }

    @Test
    public void processedPluginMetadataProvidesAValidEngineVersion() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            PluginDescriptionFile description = new PluginDescriptionFile(stream);
            Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
            doReturn(description).when(plugin).getDescription();

            assertNotEquals(-1, plugin.getIrisVersion());
        }
    }

    @Test
    public void malformedAndOverflowingReleaseNumbersStayInvalid() {
        for (String version : List.of("development", "4..3", ".4.3.3", "4.3.3.", "4.3a.3-test",
                "2147483648", "999999999999999999999-SNAPSHOT", "-SNAPSHOT")) {
            assertEquals(version, -1, plugin(version).getIrisVersion());
        }
    }

    @Test
    public void supportedMinecraftVersionsRetainTheirEngineNumbers() {
        Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getVersion).thenReturn("26.1.2-74-ver/26.1.2@e4e17fc (MC: 26.1.2)");
            assertEquals(2612, plugin.getMCVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.1.2-8-ver/26.1.x@62dc0f2 (MC: 26.1.2)");
            assertEquals(2612, plugin.getMCVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.2-133 (MC: 26.2)");
            assertEquals(262, plugin.getMCVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.3-169 (MC: 26.3)");
            assertEquals(263, plugin.getMCVersion());
        }
    }

    private static Iris plugin(String version) {
        Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
        doReturn(new PluginDescriptionFile("Iris", version, Iris.class.getName())).when(plugin).getDescription();
        return plugin;
    }
}
