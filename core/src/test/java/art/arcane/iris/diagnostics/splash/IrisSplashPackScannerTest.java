package art.arcane.iris.diagnostics.splash;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IrisSplashPackScannerTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingVersionRemainsVisibleWithUnknownVersion() throws Exception {
        File pack = temporary.newFolder("overworld");
        File dimensions = new File(pack, "dimensions");
        Files.createDirectories(dimensions.toPath());
        Files.writeString(new File(dimensions, "overworld.json").toPath(), "{}");
        IrisSplashPackScanner.SplashPackMetadata metadata = IrisSplashPackScanner.read(pack, null);
        assertNotNull(metadata);
        assertEquals("unknown", metadata.version());
        assertEquals(1, IrisSplashPackScanner.collect(temporary.getRoot(), null).size());
    }
}
