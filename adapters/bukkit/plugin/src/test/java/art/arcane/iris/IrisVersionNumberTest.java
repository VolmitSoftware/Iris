package art.arcane.iris;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
            Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
            doReturn(new PluginDescriptionFile(stream)).when(plugin).getDescription();

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

    private static Iris plugin(String version) {
        Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
        doReturn(new PluginDescriptionFile("Iris", version, Iris.class.getName())).when(plugin).getDescription();
        return plugin;
    }
}
