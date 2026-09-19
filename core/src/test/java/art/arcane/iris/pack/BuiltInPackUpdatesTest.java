package art.arcane.iris.pack;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuiltInPackUpdatesTest {
    @Test
    public void comparesNumericVersionsWithoutDowngradeNotices() {
        BuiltInPackUpdates.Update update = new BuiltInPackUpdates.Update("4010");
        assertEquals(" -> v4010 available", update.suffix("999"));
        assertEquals("", update.suffix("4010"));
        assertEquals("", update.suffix("5000"));
        assertEquals(" (latest v4010)", update.suffix("custom"));
        assertFalse(update.newerThan("custom"));
        assertEquals(" (update check unavailable)", BuiltInPackUpdates.Update.unavailable().suffix("4010"));
    }

    @Test
    public void checksOnlyBuiltInPacksAndKeepsSuccessfulResultsOnPartialFailure() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            boolean overworld = exchange.getRequestURI().getPath().equals("/overworld");
            byte[] body = (overworld ? release("v4010", "overworld.zip", false) : "rate limited")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(overworld ? 200 : 429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            Map<String, BuiltInPackUpdates.Update> updates = BuiltInPackUpdates.check(
                    List.of("overworld", "underworld", "custom", "overworld"), client,
                    pack -> URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + pack))
                    .get(10, TimeUnit.SECONDS);
            assertEquals(2, requests.get());
            assertEquals("4010", updates.get("overworld").version());
            assertEquals(BuiltInPackUpdates.Update.unavailable(), updates.get("underworld"));
            assertFalse(updates.containsKey("custom"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsMalformedPrereleaseAndMissingArchiveMetadata() throws Exception {
        for (String body : List.of("invalid", "{}", release("4010", "source.zip", false),
                release("4010", "overworld.zip", true), release("not-a-version", "overworld.zip", false))) {
            assertEquals(BuiltInPackUpdates.Update.unavailable(), checkResponse(body));
        }
    }

    @Test
    public void acceptsStableReleaseWithMatchingArchive() throws Exception {
        assertTrue(checkResponse(release("V+4010", "overworld.zip", false)).newerThan("4009"));
    }

    @Test
    public void boundsReleaseMetadataSize() throws Exception {
        assertEquals(BuiltInPackUpdates.Update.unavailable(), checkResponse(" ".repeat(1024 * 1024 + 1)));
    }

    @Test
    public void timesOutWithoutResponseHeaders() throws Exception {
        assertStalledResponseUnavailable(false);
    }

    @Test
    public void timesOutWhenResponseBodyStalls() throws Exception {
        assertStalledResponseUnavailable(true);
    }

    private void assertStalledResponseUnavailable(boolean sendHeaders) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (sendHeaders) {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
            }
        });
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertEquals(BuiltInPackUpdates.Update.unavailable(), BuiltInPackUpdates.check(
                    List.of("overworld"), client,
                    pack -> URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                    .get(8, TimeUnit.SECONDS).get("overworld"));
        } finally {
            server.stop(0);
        }
    }

    private BuiltInPackUpdates.Update checkResponse(String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return BuiltInPackUpdates.check(List.of("overworld"), client,
                    pack -> URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                    .get(10, TimeUnit.SECONDS).get("overworld");
        } finally {
            server.stop(0);
        }
    }

    private static String release(String version, String asset, boolean prerelease) {
        return "{\"tag_name\":\"" + version + "\",\"draft\":false,\"prerelease\":" + prerelease
                + ",\"assets\":[{\"name\":\"" + asset + "\"}]}";
    }
}
