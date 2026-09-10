package art.arcane.iris.core.localization;

import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisDownloadValidationTest {
    private static final String LOCALE = "de_DE";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void missingLanguageDownloadsOnceAndPreservesLaterEdits() throws Exception {
        String complete = completeCatalog();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(complete, requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path language = temporaryFolder.newFolder().toPath().resolve("languages/de_DE.toml");
            assertEquals(complete, catalog.readOrInstall(LOCALE, language, IrisLanguage::validateDownload));
            assertEquals(complete, Files.readString(language));
            assertEquals(1, requests.get());

            String edited = "\"iris.command.unknown\" = \"Eigener Befehl\"\n";
            Files.writeString(language, edited);
            assertEquals(edited, catalog.readOrInstall(LOCALE, language, IrisLanguage::validateDownload));
            assertEquals(edited, Files.readString(language));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void existingUnreadableLanguageIsNeverReplacedByADownload() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(completeCatalog(), requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path language = temporaryFolder.newFile("de_DE.toml").toPath();
            String unreadable = "[unterminated";
            Files.writeString(language, unreadable);

            assertThrows(IllegalArgumentException.class,
                    () -> catalog.readOrInstall(LOCALE, language, IrisLanguage::validateDownload));
            assertEquals(unreadable, Files.readString(language));
            assertEquals(0, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void malformedDownloadDoesNotCreateALanguageFile() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server("[unterminated", requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path language = temporaryFolder.newFolder().toPath().resolve("de_DE.toml");
            assertThrows(IllegalArgumentException.class,
                    () -> catalog.readOrInstall(LOCALE, language, IrisLanguage::validateDownload));
            assertFalse(Files.exists(language));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void partialDownloadsInstallTheirValidMessages() throws Exception {
        String partial = """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                "iris.command.permission_denied" = "Missing placeholder"
                "future.message" = "Future value"
                """;
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(partial, requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path language = temporaryFolder.newFolder().toPath().resolve("de_DE.toml");
            assertEquals(partial, catalog.readOrInstall(LOCALE, language, IrisLanguage::validateDownload));
            assertEquals(partial, Files.readString(language));
            LocaleOverlay overlay = IrisLanguage.parseOverlay(language.toString(), LOCALE, partial);
            assertEquals(1, overlay.values().size());
            assertEquals("Unbekannter Iris-Befehl", ((TextValue)
                    overlay.value(IrisMessages.COMMAND_UNKNOWN.id())).template());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void partialLanguageFilesRemainValid() {
        LocaleOverlay overlay = IrisLanguage.parseOverlay("language", LOCALE, """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                """);

        assertEquals(1, overlay.values().size());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(overlay)).errors().isEmpty());
    }

    private String completeCatalog() throws IOException {
        return Files.readString(ProjectPaths.moduleFile("src/main/resources/languages").resolve(LOCALE + ".toml"));
    }

    private HttpServer server(String content, AtomicInteger requests) throws IOException {
        byte[] response = content.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/master/languages/de_DE.toml", exchange -> respond(exchange, response, requests));
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, byte[] response, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        try {
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }

    private URLClassLoader resources() throws IOException {
        Path resources = temporaryFolder.newFolder().toPath();
        Files.writeString(resources.resolve("source.properties"), "revision=master\nlocales=de_DE\n");
        return new URLClassLoader(new URL[]{resources.toUri().toURL()}, null);
    }

    private RemoteLanguageCatalog catalog(HttpServer server, ClassLoader resources) throws IOException {
        return RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                "Iris",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                "languages",
                ".toml",
                "source.properties",
                resources
        ));
    }
}
