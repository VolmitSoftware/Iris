package art.arcane.iris.localization;

import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisDownloadValidationTest {
    private static final String LOCALE = "de_DE";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private LoopbackLanguageRepository repository;
    private File dataFolder;
    private Path language;

    @Before
    public void setUp() throws Exception {
        repository = new LoopbackLanguageRepository();
        dataFolder = temporaryFolder.newFolder();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        language = dataFolder.toPath().resolve("languages/" + LOCALE + ".toml");
    }

    @After
    public void tearDown() {
        IrisLanguage.shutdown();
        repository.close();
    }

    @Test
    public void missingLanguageDownloadsOnceAndPreservesLaterEdits() throws Exception {
        repository.publish(LoopbackLanguageRepository.REVISION_ONE, LOCALE, completeCatalog());
        PluginLanguageEditor.SnapshotLoader loader = IrisLanguage.editorOptions().loader();

        assertEquals("Unbekannter Iris-Befehl", text(loader.load(LOCALE).value(IrisMessages.COMMAND_UNKNOWN)));
        assertTrue(Files.isRegularFile(language));
        assertTrue(Files.isRegularFile(dataFolder.toPath().resolve("languages/.upstream/" + LOCALE + ".toml")));
        assertEquals(1, repository.rawRequests());

        String edited = "\"iris.command.unknown\" = \"Eigener Befehl\"\n";
        Files.writeString(language, edited);

        assertEquals("Eigener Befehl", text(loader.load(LOCALE).value(IrisMessages.COMMAND_UNKNOWN)));
        assertEquals(edited, Files.readString(language));
        assertEquals(1, repository.rawRequests());
    }

    @Test
    public void existingUnreadableLanguageIsNeverReplacedByADownload() throws Exception {
        repository.publish(LoopbackLanguageRepository.REVISION_ONE, LOCALE, completeCatalog());
        Files.createDirectories(language.getParent());
        String unreadable = "[unterminated";
        Files.writeString(language, unreadable);

        assertThrows(Exception.class, () -> IrisLanguage.editorOptions().loader().load(LOCALE));
        assertEquals(unreadable, Files.readString(language));
        assertEquals(0, repository.rawRequests());
    }

    @Test
    public void malformedDownloadDoesNotCreateALanguageFile() throws Exception {
        repository.publish(LoopbackLanguageRepository.REVISION_ONE, LOCALE, "[unterminated");

        assertThrows(Exception.class, () -> IrisLanguage.editorOptions().loader().load(LOCALE));
        assertFalse(Files.exists(language));
        assertEquals(1, repository.rawRequests());
    }

    @Test
    public void partialDownloadsInstallTheirValidMessages() throws Exception {
        repository.publish(LoopbackLanguageRepository.REVISION_ONE, LOCALE, """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                "iris.command.permission_denied" = "Missing placeholder"
                "future.message" = "Future value"
                """);

        IrisLanguage.editorOptions().loader().load(LOCALE);

        LocaleOverlay overlay = IrisLanguage.parseOverlay(language.toString(), LOCALE, Files.readString(language));
        assertEquals(1, overlay.values().size());
        assertEquals("Unbekannter Iris-Befehl", text(overlay.value(IrisMessages.COMMAND_UNKNOWN.id())));
        assertEquals(1, repository.rawRequests());
    }

    @Test
    public void partialLanguageFilesRemainValid() {
        LocaleOverlay overlay = IrisLanguage.parseOverlay("language", LOCALE, """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                """);

        assertEquals(1, overlay.values().size());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(overlay)).errors().isEmpty());
    }

    private static String text(Object value) {
        return ((TextValue) value).template();
    }

    private String completeCatalog() throws IOException {
        return Files.readString(ProjectPaths.moduleFile("src/main/resources/languages").resolve(LOCALE + ".toml"));
    }
}
