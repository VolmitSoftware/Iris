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
import static org.mockito.Mockito.mockStatic;

public class IrisVersionNumberTest {
    @Test
    public void releaseAndQualifiedBuildsUseTheSameVersionNumber() {
        for (String version : List.of("4.3.3", "4.3.3-SNAPSHOT", "4.3.3-test")) {
            assertEquals(version, 433, IrisRuntime.irisVersion(version));
        }
    }

    @Test
    public void processedPluginMetadataProvidesAValidEngineVersion() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            PluginDescriptionFile description = new PluginDescriptionFile(stream);

            assertNotEquals(-1, IrisRuntime.irisVersion(description.getVersion()));
        }
    }

    @Test
    public void malformedAndOverflowingReleaseNumbersStayInvalid() {
        for (String version : List.of("development", "4..3", ".4.3.3", "4.3.3.", "4.3a.3-test",
                "2147483648", "999999999999999999999-SNAPSHOT", "-SNAPSHOT")) {
            assertEquals(version, -1, IrisRuntime.irisVersion(version));
        }
    }

    @Test
    public void supportedMinecraftVersionsRetainTheirEngineNumbers() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getVersion).thenReturn("26.1.2-74-ver/26.1.2@e4e17fc (MC: 26.1.2)");
            assertEquals(2612, IrisRuntime.minecraftVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.1.2-8-ver/26.1.x@62dc0f2 (MC: 26.1.2)");
            assertEquals(2612, IrisRuntime.minecraftVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.2-133 (MC: 26.2)");
            assertEquals(262, IrisRuntime.minecraftVersion());
            bukkit.when(Bukkit::getVersion).thenReturn("26.3-169 (MC: 26.3)");
            assertEquals(263, IrisRuntime.minecraftVersion());
        }
    }
}
