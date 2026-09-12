package art.arcane.iris.pack.loading;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResourceLoaderStaticObjectValidationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;
    private File pack;
    private IrisData data;

    @Before
    public void setUp() throws Exception {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        previousSettings = IrisSettings.settings;
        IrisPlatforms.unbind();
        pack = temporaryFolder.newFolder("pack");
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.dataFolder()).thenReturn(pack);
        IrisPlatforms.bind(platform);
        IrisSettings.settings = new IrisSettings();
        data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);
        when(data.getGson()).thenReturn(new Gson());
        write("objects/tower.iob", "");
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void malformedInlineOriginNeverReachesDeserialization() throws Exception {
        File dimension = write("dimensions/main.json", """
                {"staticObjects":[{"object":"tower","position":{"x":"wrong","y":100,"z":-100}}]}
                """);

        assertNull(loader().loadFile(dimension, "main"));
        verify(data, never()).getGson();
    }

    @Test
    public void malformedPositionSnippetRejectsReloadAndPreservesThePreviousDimension() throws Exception {
        File dimension = write("dimensions/main.json", """
                {"staticObjects":[{"object":"tower","position":{"x":100,"y":100,"z":-100}}]}
                """);
        ResourceLoader<IrisDimension> loader = loader();
        IrisDimension previous = loader.loadFile(dimension, "main");
        assertNotNull(previous);
        write("snippet/position-3d/tower.json", "{\"x\":\"wrong\",\"y\":100}");
        write("dimensions/main.json", """
                {"staticObjects":[{"object":"tower","position":"snippet/tower"}]}
                """);

        assertNull(loader.loadFile(dimension, "main"));
        assertEquals(new IrisPosition(100, 100, -100), previous.getStaticObjects().getFirst().getPosition());
        verify(data, times(1)).getGson();
    }

    private ResourceLoader<IrisDimension> loader() {
        return new ResourceLoader<>(pack, data, "dimensions", "Dimension", IrisDimension.class,
                ResourceLoader.Options.datapackCompiler());
    }

    private File write(String relative, String content) throws Exception {
        Path path = pack.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path.toFile();
    }
}
