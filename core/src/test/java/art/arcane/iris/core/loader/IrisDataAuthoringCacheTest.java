package art.arcane.iris.core.loader;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.spi.IrisServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisDataAuthoringCacheTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private IrisData source;
    private IrisData epoch;
    private IrisSettings previousSettings;

    @Before
    public void registerServices() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
    }

    @After
    public void closeData() {
        if (source != null) {
            source.close();
        }
        if (epoch != null) {
            epoch.close();
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void externalEditsRefreshOnlyTheEditableSourceAndKeyLists() throws Exception {
        Path sourceRoot = temporary.newFolder("source").toPath();
        Path epochRoot = temporary.newFolder("epoch").toPath();
        Files.createDirectories(sourceRoot.resolve("generators"));
        Files.createDirectories(epochRoot.resolve("generators"));
        Files.writeString(sourceRoot.resolve("generators/sample.json"), "{\"zoom\":2.0}");
        Files.writeString(epochRoot.resolve("generators/sample.json"), "{\"zoom\":3.0}");
        source = IrisData.get(new File(sourceRoot.toFile(), "."));
        epoch = IrisData.openRuntime(epochRoot.toFile());
        assertEquals(2.0, source.getGeneratorLoader().load("sample").getZoom(), 0.0);
        IrisGenerator retained = epoch.getGeneratorLoader().load("sample");
        assertFalse(Arrays.asList(source.getGeneratorLoader().getPossibleKeys()).contains("added"));
        Files.writeString(sourceRoot.resolve("generators/sample.json"), "{\"zoom\":4.0}");
        Files.writeString(sourceRoot.resolve("generators/added.json"), "{\"zoom\":5.0}");

        IrisData.invalidateLoadedAuthoringResources(sourceRoot.toFile());

        assertEquals(4.0, source.getGeneratorLoader().load("sample").getZoom(), 0.0);
        assertTrue(Arrays.asList(source.getGeneratorLoader().getPossibleKeys()).contains("added"));
        assertSame(retained, epoch.getGeneratorLoader().load("sample"));
        assertEquals(3.0, retained.getZoom(), 0.0);
        assertEquals("{\"zoom\":3.0}", Files.readString(epochRoot.resolve("generators/sample.json")));
    }
}
