package art.arcane.iris.localization;

import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisLanguageEditorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File directory;
    private PluginLanguageService languages;
    private PluginLanguageEditor editor;

    @Before
    public void prepareEditor() throws Exception {
        directory = temporaryFolder.newFolder();
        assertTrue(IrisLanguage.reload(directory, "en_US"));
        Path language = directory.toPath().resolve("languages/fr_FR.toml");
        Files.createDirectories(language.getParent());
        Files.copy(ProjectPaths.moduleFile("src/main/resources/languages/fr_FR.toml"), language);
        PluginLanguageEditor.Options options = IrisLanguage.editorOptions();
        LocalizationSnapshot english = LocalizationSnapshot.create(
                LocalizationCandidate.english(IrisLanguage.catalog(), PluralSelector.oneOther()));
        languages = new PluginLanguageService(new PluginLanguageService.Options(
                directory.toPath().resolve("players.properties"), VolmitLocales::all, () -> "en_US", () -> english,
                options.loader()::load, (locale, snapshot) -> {
                    throw new AssertionError("Editing must not select a server language");
                }, Logger.getLogger("IrisLanguageEditorTest")));
        editor = new PluginLanguageEditor(languages, options);
    }

    @After
    public void closeEditor() {
        editor.close();
        languages.close();
        assertTrue(IrisLanguage.reload(directory, "en_US"));
        IrisLanguage.shutdown();
    }

    @Test
    public void savesOneLocaleAndRefreshesItsPersonalSnapshotWithoutSelectingIt() throws Exception {
        UUID player = UUID.randomUUID();
        Path english = directory.toPath().resolve("languages/en_US.toml");
        byte[] englishBefore = Files.readAllBytes(english);
        languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
        PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
        TextValue value = new TextValue("Langue \"{locale}\" rechargee.\nSuite.");
        editor.save(new PluginLanguageEditor.Edit("fr_FR", IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                original.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS), value)).get(5, TimeUnit.SECONDS);

        Path file = directory.toPath().resolve("languages/fr_FR.toml");
        assertTrue(Files.isRegularFile(file));
        assertEquals(value, IrisLanguage.editorOptions().loader().load("fr_FR").value(IrisMessages.COMMAND_RELOAD_SUCCESS));
        assertEquals(value, languages.snapshot(player).value(IrisMessages.COMMAND_RELOAD_SUCCESS));
        assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
        assertEquals("en_US", languages.defaultLocale());
        assertEquals("en_US", IrisLanguage.activeLocale());
        assertArrayEquals(englishBefore, Files.readAllBytes(english));
    }

    @Test
    public void invalidMessageLeavesTheLocaleFileIntact() throws Exception {
        PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
        editor.save(new PluginLanguageEditor.Edit("fr_FR", IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                original.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS), new TextValue("Langue {locale}")))
                .get(5, TimeUnit.SECONDS);
        Path file = directory.toPath().resolve("languages/fr_FR.toml");
        byte[] before = Files.readAllBytes(file);
        PluginLanguageEditor.Document saved = editor.load("fr_FR").get(5, TimeUnit.SECONDS);

        assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
                IrisMessages.COMMAND_RELOAD_SUCCESS.id(), saved.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS),
                new TextValue("Missing placeholder"))).get(5, TimeUnit.SECONDS));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    public void missingLocaleDownloadsAndCanBeEditedWithoutBeingSelected() throws Exception {
        byte[] complete = Files.readAllBytes(ProjectPaths.moduleFile("src/main/resources/languages/fr_FR.toml"));
        AtomicInteger requests = new AtomicInteger();
        Field remoteField = IrisLanguage.class.getDeclaredField("remote");
        remoteField.setAccessible(true);
        RemoteLanguageCatalog previous = IrisLanguage.remote(directory);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (RemoteLanguageCatalog catalog = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                "Iris",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                "languages",
                ".toml",
                "iris-language-source.properties",
                IrisLanguage.class.getClassLoader()
        ))) {
            server.createContext(catalog.sourceUri("fr_FR").getPath(),
                    exchange -> respond(exchange, complete, requests));
            server.start();
            remoteField.set(null, catalog);
            Path language = directory.toPath().resolve("languages/fr_FR.toml");
            Files.delete(language);

            PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
            assertArrayEquals(complete, Files.readAllBytes(language));
            TextValue value = new TextValue("Langue {locale}");
            PluginLanguageEditor.Document saved = editor.save(new PluginLanguageEditor.Edit("fr_FR", IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                    original.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS), value)).get(5, TimeUnit.SECONDS);

            assertEquals(value, saved.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS));
            assertEquals("en_US", languages.defaultLocale());
            assertEquals("en_US", IrisLanguage.activeLocale());
            assertEquals(value, IrisLanguage.editorOptions().loader().load("fr_FR").value(IrisMessages.COMMAND_RELOAD_SUCCESS));
            assertEquals(1, requests.get());
        } finally {
            remoteField.set(null, previous);
            server.stop(0);
        }
    }

    @Test
    public void savingOneMessagePreservesUnknownValuesAndLeadingComments() throws Exception {
        Path file = directory.toPath().resolve("languages/fr_FR.toml");
        String header = "# Operator customization\n";
        String extension = "\n[future]\nvalue = \"Preserve this\"\nenabled = true\norder = [1, 2]\n";
        String source = header + Files.readString(file) + extension;
        Files.writeString(file, source);
        JsonElement unknown = TomlCodec.toJsonElement(source).getAsJsonObject().get("future");
        PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
        TextValue value = new TextValue("Langue {locale}");

        editor.save(new PluginLanguageEditor.Edit("fr_FR", IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                original.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS), value)).get(5, TimeUnit.SECONDS);

        String saved = Files.readString(file);
        assertTrue(saved.startsWith(header));
        assertEquals(unknown, TomlCodec.toJsonElement(saved).getAsJsonObject().get("future"));
        assertEquals(value, IrisLanguage.editorOptions().loader().load("fr_FR").value(IrisMessages.COMMAND_RELOAD_SUCCESS));
    }

    @Test
    public void staleEditorSaveDoesNotReplaceANewerLocalEdit() throws Exception {
        PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
        Path file = directory.toPath().resolve("languages/fr_FR.toml");
        String external = TomlLanguageEditor.upsert(Files.readString(file), IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                new TextValue("Modification externe {locale}")).content();
        Files.writeString(file, external);

        assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
                IrisMessages.COMMAND_RELOAD_SUCCESS.id(), original.snapshot().value(IrisMessages.COMMAND_RELOAD_SUCCESS),
                new TextValue("Modification ancienne {locale}"))).get(5, TimeUnit.SECONDS));

        assertEquals(external, Files.readString(file));
    }

    @Test
    public void editingDefaultEnglishRefreshesServerMessagesWithoutSelectingAnotherLocale() throws Exception {
        PluginLanguageEditor.Document original = editor.load("en_US").get(5, TimeUnit.SECONDS);
        TextValue value = new TextValue("Custom Iris command");

        editor.save(new PluginLanguageEditor.Edit("en_US", IrisMessages.COMMAND_UNKNOWN.id(),
                original.snapshot().value(IrisMessages.COMMAND_UNKNOWN), value)).get(5, TimeUnit.SECONDS);

        assertEquals("Custom Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        assertEquals("en_US", IrisLanguage.activeLocale());
        assertEquals("en_US", languages.defaultLocale());
        assertEquals(value, IrisLanguage.editorOptions().loader().load("en_US").value(IrisMessages.COMMAND_UNKNOWN));
    }

    private static void respond(HttpExchange exchange, byte[] response, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        try {
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }
}
