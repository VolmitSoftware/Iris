package art.arcane.iris.core.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class IrisLanguageHotloadSnapshotTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void capturesMissingLanguageAsStableTombstone() throws Exception {
        File language = new File(temporaryFolder.getRoot(), "languages/en_US.toml");

        LocaleHotloadSnapshot snapshot = IrisLanguage.captureHotloadSnapshot(language, "en_US");

        assertTrue(snapshot.missing());
        assertEquals("missing", snapshot.sha256());
    }

    @Test
    public void detectsSameMetadataContentReplacementBySha256() throws Exception {
        File language = new File(temporaryFolder.getRoot(), "languages/en_US.toml");
        Files.createDirectories(language.toPath().getParent());
        String firstContent = "a = \"1\"\n";
        String secondContent = "a = \"2\"\n";
        Files.writeString(language.toPath(), firstContent, StandardCharsets.UTF_8);
        FileTime fixedTime = FileTime.fromMillis(10_000L);
        Files.setLastModifiedTime(language.toPath(), fixedTime);
        LocaleHotloadSnapshot first = IrisLanguage.captureHotloadSnapshot(language, "en_US");

        Files.writeString(language.toPath(), secondContent, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(language.toPath(), fixedTime);
        LocaleHotloadSnapshot second = IrisLanguage.captureHotloadSnapshot(language, "en_US");

        assertFalse(first.missing());
        assertFalse(second.missing());
        assertEquals(firstContent.length(), secondContent.length());
        assertEquals(first.file(), second.file());
        assertEquals(firstContent, first.content());
        assertEquals(secondContent, second.content());
        assertNotEquals(first.sha256(), second.sha256());
        assertNotEquals(first, second);
    }

    @Test
    public void snapshotsRejectFilesLargerThanTwoMebibytes() throws Exception {
        File language = temporaryFolder.newFile("en_US.toml");
        Files.write(language.toPath(), new byte[2 * 1024 * 1024 + 1]);

        assertThrows(IllegalArgumentException.class, () -> IrisLanguage.captureHotloadSnapshot(language, "en_US"));
    }

    @Test
    public void snapshotsRejectInvalidUtf8() throws Exception {
        File language = temporaryFolder.newFile("en_US.toml");
        Files.write(language.toPath(), new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThrows(IOException.class, () -> IrisLanguage.captureHotloadSnapshot(language, "en_US"));
    }

    @Test
    public void snapshotsRejectDirectoriesAtTheLanguagePath() throws Exception {
        File language = temporaryFolder.newFolder("en_US.toml");

        assertThrows(IllegalArgumentException.class, () -> IrisLanguage.captureHotloadSnapshot(language, "en_US"));
    }
}
