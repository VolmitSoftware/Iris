package art.arcane.iris.configuration;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Iris rewrites iris.json on every read so a file written by an older build gains the keys it is missing.
 * That write used to fail into an empty catch, which is how a read-only or full data folder turned every
 * settings change an operator made in game into something that silently vanished on the next boot.
 */
public class IrisSettingsRewriteFailureTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform capturingPlatform;
    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;

    @Before
    public void captureReports() {
        previousSettings = IrisSettings.settings;
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        capturingPlatform = mock(IrisPlatform.class);
        IrisPlatforms.bind(capturingPlatform);
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
    public void anUnwritableSettingsFileIsReported() throws Exception {
        File settingsFile = temporaryFolder.newFile("iris.json");
        Files.writeString(settingsFile.toPath(), "{}");
        assertTrue("this test needs a file the JVM can mark read-only", settingsFile.setWritable(false));
        doReturn(settingsFile).when(capturingPlatform).dataFile("iris.json");

        IrisSettings.invalidate();
        IrisSettings.get();

        verify(capturingPlatform).reportError(contains(settingsFile.getAbsolutePath()), any(Throwable.class));
    }

    @Test
    public void aWritableSettingsFileIsRewrittenQuietly() throws Exception {
        File settingsFile = temporaryFolder.newFile("iris.json");
        Files.writeString(settingsFile.toPath(), "{}");
        doReturn(settingsFile).when(capturingPlatform).dataFile("iris.json");

        IrisSettings.invalidate();
        IrisSettings.get();

        verify(capturingPlatform, never()).reportError(anyString(), any(Throwable.class));
        assertTrue(Files.readString(settingsFile.toPath()).contains("general"));
    }
}
