package art.arcane.iris.core.loader;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisDataAuthoringCacheTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

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

    @Test
    public void resourceKeyDiscoveryPreservesNestedNamesAndCachedArrays() throws Exception {
        Path root = temporary.newFolder("resource-keys").toPath();
        for (String name : List.of(
                "generators/plain.json", "generators/nested.json/ridge.json.alt.json", "generators/ignored.JSON",
                "images/plain.png", "images/nested.png/ridge.png.alt.png", "images/ignored.PNG",
                "matter/plain.mat", "matter/nested.mat/ridge.mat.alt.mat", "matter/ignored.MAT")) {
            Path file = root.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[0]);
        }
        source = IrisData.get(root.toFile());
        Map<ResourceLoader<?>, Set<String>> expected = Map.of(
                source.getGeneratorLoader(), Set.of("plain", "nested/ridge.alt"),
                source.getImageLoader(), Set.of("plain", "nested.png/ridge.alt"),
                source.getMatterLoader(), Set.of("plain", "nested.mat/ridge.alt"));

        for (Map.Entry<ResourceLoader<?>, Set<String>> entry : expected.entrySet()) {
            String[] keys = entry.getKey().getPossibleKeys();
            assertEquals(entry.getValue(), Set.of(keys));
            assertEquals(entry.getValue().size(), keys.length);
            assertSame(keys, entry.getKey().getPossibleKeys());
        }
    }
}
