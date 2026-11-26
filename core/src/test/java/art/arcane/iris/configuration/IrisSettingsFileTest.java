package art.arcane.iris.configuration;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.IrisRuntimeSchedulerMode;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisSettingsFileTest {
    private static final FileTime LONG_AGO = FileTime.fromMillis(1_000_000L);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;
    private File settingsFile;

    @Before
    public void bindPlatform() throws Exception {
        previousSettings = IrisSettings.settings;
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        File dataFolder = temporaryFolder.newFolder("Iris");
        settingsFile = new File(dataFolder, IrisSettingsFile.FILE_NAME);
        IrisPlatform platform = mock(IrisPlatform.class);
        doReturn(settingsFile).when(platform).dataFile(IrisSettingsFile.FILE_NAME);
        IrisPlatforms.bind(platform);
    }

    @After
    public void restore() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void writtenSettingsReadBackUnchanged() throws Exception {
        IrisSettings edited = new IrisSettings();
        edited.getGeneral().setLanguage("de_DE");
        edited.getGeneral().setDebug(true);
        edited.getWorld().setAsyncTickIntervalMS(1_250L);
        edited.getWorld().setTargetSpawnEntitiesPerChunk(1.5D);
        edited.getPregen().setRuntimeSchedulerMode(IrisRuntimeSchedulerMode.FOLIA);
        edited.getPerformance().getEngineSVC().setParallelism(6);

        String rendered = IrisSettingsFile.render(edited);
        IrisSettings readBack = IrisSettingsFile.parse(rendered);

        assertEquals(new Gson().toJson(edited), new Gson().toJson(readBack));
        assertEquals(rendered, IrisSettingsFile.render(readBack));
    }

    @Test
    public void missingFileIsCreatedFromDefaults() throws Exception {
        IrisSettings loaded = IrisSettingsFile.load();

        assertEquals(new Gson().toJson(new IrisSettings()), new Gson().toJson(loaded));
        assertEquals(IrisSettingsFile.render(new IrisSettings()), Files.readString(settingsFile.toPath()));
    }

    @Test
    public void brokenTomlIsNotOverwritten() throws Exception {
        String broken = "[general]\ndebug = tru\n";
        Files.writeString(settingsFile.toPath(), broken);

        IrisSettings loaded = IrisSettingsFile.load();

        assertFalse(loaded.getGeneral().isDebug());
        assertEquals(broken, Files.readString(settingsFile.toPath()));
    }

    @Test
    public void upToDateFileIsNotRewritten() throws Exception {
        Files.writeString(settingsFile.toPath(), IrisSettingsFile.render(new IrisSettings()));
        Files.setLastModifiedTime(settingsFile.toPath(), LONG_AGO);

        IrisSettingsFile.load();

        assertEquals(LONG_AGO, Files.getLastModifiedTime(settingsFile.toPath()));
    }

    @Test
    public void missingKeysAreAddedOnLoad() throws Exception {
        Files.writeString(settingsFile.toPath(), "[general]\ndebug = true\n");

        IrisSettingsFile.load();

        String rewritten = Files.readString(settingsFile.toPath());
        assertTrue(rewritten.contains("\ndebug = true"));
        assertTrue(rewritten.contains("\n[studio]"));
    }

    @Test
    public void documentedSettingsCarryTheirComment() {
        String rendered = IrisSettingsFile.render(new IrisSettings());

        assertTrue(rendered.contains("# Turns unresolved pack content keys and bad block-state properties into blocking pack errors.\n"
                + "# Effect: The -Diris.strictContent system property overrides this.\n"
                + "strictContentKeys = false\n"));
    }

    @Test
    public void undocumentedSettingsHaveNoComment() {
        String rendered = IrisSettingsFile.render(new IrisSettings());

        assertFalse(lineBefore(rendered, "debug = false").startsWith("#"));
    }

    private static String lineBefore(String text, String line) {
        List<String> lines = text.lines().toList();
        int index = lines.indexOf(line);
        assertTrue(index > 0);
        return lines.get(index - 1);
    }
}
