package art.arcane.iris.core.localization;

import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final String MISSING_KEY = "iris.director.commanddeveloper.director.stage_world_generation_update";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incompleteCacheDownloadsCompleteCatalogAndReusesIt() throws Exception {
        String complete = completeCatalog();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(complete, requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path cache = catalog.cacheFile(LOCALE);
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, incompleteCatalog(complete));

            RemoteLanguageCatalog.CacheResult initial = catalog.read(LOCALE, IrisLanguage::validateDownload);
            assertEquals(RemoteLanguageCatalog.CacheState.INVALID, initial.state());
            assertTrue(initial.failure().getMessage().contains(MISSING_KEY));
            assertEquals(0, requests.get());

            assertEquals(complete, catalog.readOrDownload(LOCALE, IrisLanguage::validateDownload));
            assertEquals(complete, Files.readString(cache));
            assertEquals(RemoteLanguageCatalog.CacheState.VALID,
                    catalog.read(LOCALE, IrisLanguage::validateDownload).state());
            assertEquals(1, requests.get());

            assertEquals(complete, catalog.readOrDownload(LOCALE, IrisLanguage::validateDownload));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void incompleteUpstreamDoesNotReplaceAnInvalidCache() throws Exception {
        String incomplete = incompleteCatalog(completeCatalog());
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(incomplete, requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path cache = catalog.cacheFile(LOCALE);
            Files.createDirectories(cache.getParent());
            String cached = "{\"locale\":\"de_DE\",\"messages\":{}}";
            Files.writeString(cache, cached);

            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> catalog.readOrDownload(LOCALE, IrisLanguage::validateDownload));
            assertTrue(failure.getMessage().contains(MISSING_KEY));
            assertEquals(cached, Files.readString(cache));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void incompleteUpstreamDoesNotCreateACacheFile() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(incompleteCatalog(completeCatalog()), requests);
        try (URLClassLoader resources = resources();
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            assertThrows(IllegalArgumentException.class,
                    () -> catalog.readOrDownload(LOCALE, IrisLanguage::validateDownload));
            assertFalse(Files.exists(catalog.cacheFile(LOCALE)));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void partialOperatorOverridesRemainValid() {
        LocaleOverlay overlay = IrisLanguage.parseOverlay("override", LOCALE, """
                {"locale":"de_DE","messages":{"iris.command.unknown":"Unbekannter Iris-Befehl"}}
                """);

        assertEquals(1, overlay.values().size());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(overlay)).errors().isEmpty());
    }

    private String completeCatalog() throws IOException {
        return Files.readString(ProjectPaths.moduleFile("src/main/resources/languages").resolve(LOCALE + ".json"));
    }

    private String incompleteCatalog(String complete) {
        JsonObject document = JsonParser.parseString(complete).getAsJsonObject();
        assertTrue(document.getAsJsonObject("messages").remove(MISSING_KEY) != null);
        return document.toString();
    }

    private HttpServer server(String content, AtomicInteger requests) throws IOException {
        byte[] response = content.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/master/languages/de_DE.json", exchange -> respond(exchange, response, requests));
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
                ".json",
                "source.properties",
                temporaryFolder.newFolder().toPath(),
                resources
        ));
    }
}
