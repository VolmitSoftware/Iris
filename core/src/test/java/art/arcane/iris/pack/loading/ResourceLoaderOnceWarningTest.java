package art.arcane.iris.pack.loading;

import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.testsupport.CapturedLog;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * A pack typo is reached per chunk, so a loader miss or an unreadable file must be stated once per pack,
 * resource type and key, and stated again after the pack's caches are cleared (hotload).
 */
public class ResourceLoaderOnceWarningTest {
    private static final String MISSING = "Couldn't find ";
    private static final String UNREADABLE = "Couldn't read ";
    private static final byte[] GARBAGE = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CapturedLog log;
    private IrisData data;

    @Before
    public void openPack() throws Exception {
        log = CapturedLog.bind(temporaryFolder.newFolder("platform-data"));
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        data = openPack("pack");
        assertNotNull(log.messages() + " " + log.reportedErrors(), data.getObjectLoader());
    }

    private IrisData openPack(String name) throws Exception {
        IrisData opened = IrisData.openRuntime(temporaryFolder.newFolder(name));
        opened.bindGenerationRegistryContract(GenerationRegistryContract.empty());
        return opened;
    }

    @After
    public void closePack() {
        data.close();
        IrisServices.remove(PreservationRegistry.class);
        log.close();
    }

    @Test
    public void theSameMissingKeyWarnsOnce() {
        data.getObjectLoader().load("trees/typo");
        data.getObjectLoader().load("trees/typo");
        data.getObjectLoader().findFile("trees/typo");

        assertEquals(1, log.warnings(MISSING).size());
    }

    @Test
    public void aDifferentKeyWarnsSeparately() {
        data.getObjectLoader().load("trees/typo");
        data.getObjectLoader().load("trees/other-typo");

        assertEquals(2, log.warnings(MISSING).size());
    }

    @Test
    public void theSameKeyInAnotherResourceTypeWarnsSeparately() {
        data.getObjectLoader().load("shared");
        data.getMatterLoader().load("shared");
        data.getImageLoader().load("shared");

        assertEquals(3, log.warnings(MISSING).size());
    }

    @Test
    public void theSameKeyInAnotherPackWarnsSeparately() throws Exception {
        IrisData other = openPack("other-pack");
        try {
            data.getObjectLoader().load("shared");
            other.getObjectLoader().load("shared");

            assertEquals(2, log.warnings(MISSING).size());
        } finally {
            other.close();
        }
    }

    @Test
    public void literalNullRefusalWarnsOnce() {
        data.getObjectLoader().load("null");
        data.getObjectLoader().load("null");
        data.getObjectLoader().findFile("null");
        data.getObjectLoader().findFile("null");

        assertEquals(2, log.warnings("Refusing ").size());
    }

    @Test
    public void clearingTheListsLetsTheSameMissingKeyWarnAgain() {
        data.getObjectLoader().load("trees/typo");
        data.getObjectLoader().load("trees/typo");
        assertEquals(1, log.warnings(MISSING).size());

        data.clearLists();
        data.getObjectLoader().load("trees/typo");

        assertEquals(2, log.warnings(MISSING).size());
    }

    /** Studio hotload rebuilds the pack's loaders through hotloaded() (or reopens it) without clearLists(). */
    @Test
    public void rebuildingThePackLoadersLetsTheSameMissingKeyWarnAgain() {
        data.getObjectLoader().load("trees/typo");

        data.hotloaded();
        data.getObjectLoader().load("trees/typo");

        assertEquals(2, log.warnings(MISSING).size());
    }

    @Test
    public void reopeningThePackFolderLetsTheSameMissingKeyWarnAgain() {
        data.getObjectLoader().load("trees/typo");

        IrisData reopened = IrisData.openRuntime(data.getDataFolder());
        try {
            reopened.getObjectLoader().load("trees/typo");

            assertEquals(2, log.warnings(MISSING).size());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void clearingOnePackKeepsAnotherPacksKeysClaimed() throws Exception {
        IrisData other = openPack("other-pack");
        try {
            data.getObjectLoader().load("shared");
            other.getObjectLoader().load("shared");

            data.clearLists();
            data.getObjectLoader().load("shared");
            other.getObjectLoader().load("shared");

            assertEquals(3, log.warnings(MISSING).size());
        } finally {
            other.close();
        }
    }

    @Test
    public void aPackWhosePathIsAPrefixOfAnotherIsNotReset() throws Exception {
        IrisData longer = openPack("pack-two");
        try {
            longer.getObjectLoader().load("shared");

            data.clearLists();
            longer.getObjectLoader().load("shared");

            assertEquals(1, log.warnings(MISSING).size());
        } finally {
            longer.close();
        }
    }

    @Test
    public void anUnreadableObjectWarnsOnceAndReportsItsStackOnce() throws Exception {
        writeGarbage(data.getObjectLoader(), "broken.iob");

        data.getObjectLoader().load("broken");
        data.getObjectLoader().load("broken");
        data.getObjectLoader().load("broken");

        assertEquals(1, log.warnings(UNREADABLE).size());
        assertEquals(1, log.reportedErrors().size());
    }

    @Test
    public void anUnreadableMatterObjectWarnsOnceAndReportsItsStackOnce() throws Exception {
        writeGarbage(data.getMatterLoader(), "broken.mat");

        data.getMatterLoader().load("broken");
        data.getMatterLoader().load("broken");

        assertEquals(1, log.warnings(UNREADABLE).size());
        assertEquals(1, log.reportedErrors().size());
    }

    @Test
    public void anUnreadableImageWarnsOnce() throws Exception {
        writeGarbage(data.getImageLoader(), "broken.png");

        data.getImageLoader().load("broken");
        data.getImageLoader().load("broken");

        assertEquals(1, log.warnings(UNREADABLE).size());
    }

    @Test
    public void aDifferentUnreadableFileWarnsSeparately() throws Exception {
        writeGarbage(data.getObjectLoader(), "broken.iob");
        writeGarbage(data.getObjectLoader(), "also-broken.iob");

        data.getObjectLoader().load("broken");
        data.getObjectLoader().load("also-broken");

        assertEquals(2, log.warnings(UNREADABLE).size());
        assertEquals(2, log.reportedErrors().size());
    }

    @Test
    public void clearingTheListsReportsTheStackAgainForTheNextFirstWarning() throws Exception {
        writeGarbage(data.getObjectLoader(), "broken.iob");
        data.getObjectLoader().load("broken");

        data.clearLists();
        data.getObjectLoader().load("broken");

        assertEquals(2, log.warnings(UNREADABLE).size());
        assertEquals(2, log.reportedErrors().size());
    }

    private void writeGarbage(ResourceLoader<?> loader, String fileName) throws Exception {
        File folder = new File(temporaryFolder.getRoot(), "pack/" + loader.getFolderName());
        Files.createDirectories(folder.toPath());
        Files.write(new File(folder, fileName).toPath(), GARBAGE);
    }
}
