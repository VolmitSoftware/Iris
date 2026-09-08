package art.arcane.iris.util.common.misc;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.io.IO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebCacheTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private IrisPlatform previousPlatform;

    @Before
    public void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        when(platform.dataFolder()).thenReturn(temp.getRoot());
        when(platform.dataFile(any(String[].class))).thenAnswer(invocation -> {
            File file = temp.getRoot();
            for (Object argument : invocation.getArguments()) {
                file = new File(file, String.valueOf(argument));
            }
            return file;
        });
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void declaredOversizeDoesNotReplaceThePreviousCacheEntry() throws Exception {
        byte[] body = "archive-larger-than-limit".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, true);
        try {
            String name = "declared-pack";
            String url = url(server);
            File existing = cachedFile(name, url);
            Files.createDirectories(existing.toPath().getParent());
            Files.writeString(existing.toPath(), "previous", StandardCharsets.UTF_8);

            IOException failure = assertThrows(
                    IOException.class,
                    () -> WebCache.getNonCachedFile(name, url, 8L)
            );

            assertTrue(failure.getMessage().contains("size limit"));
            assertEquals("previous", Files.readString(existing.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void streamedOversizeDoesNotPublishAPartialDownload() throws Exception {
        byte[] body = "chunked-archive-larger-than-limit".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, false);
        try {
            String name = "chunked-pack";
            String url = url(server);
            File existing = cachedFile(name, url);
            Files.createDirectories(existing.toPath().getParent());
            Files.writeString(existing.toPath(), "previous", StandardCharsets.UTF_8);

            IOException failure = assertThrows(
                    IOException.class,
                    () -> WebCache.getNonCachedFile(name, url, 8L)
            );

            assertTrue(failure.getMessage().contains("size limit"));
            assertEquals("previous", Files.readString(existing.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void boundedDownloadPublishesTheCompleteResponse() throws Exception {
        byte[] body = "valid-archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, true);
        try {
            String name = "valid-pack";
            String url = url(server);

            File downloaded = WebCache.getNonCachedFile(name, url, body.length);

            assertTrue(downloaded.isFile());
            assertEquals("valid-archive", Files.readString(downloaded.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void knownLengthReportsMonotonicStartAndFinalProgress() throws Exception {
        byte[] body = "known-length-archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, true);
        try {
            List<WebCache.TransferProgress> progress = new ArrayList<>();

            File downloaded = WebCache.getNonCachedFile(
                    "known-progress",
                    url(server),
                    body.length,
                    progress::add
            );

            assertNotNull(downloaded);
            assertTrue(progress.size() >= 2);
            assertEquals(0L, progress.get(0).transferredBytes());
            assertEquals(body.length, progress.get(0).contentLength());
            assertEquals(0L, progress.get(0).elapsedMillis());
            assertFalse(progress.get(0).complete());
            assertProgressEndsAt(progress, body.length, body.length);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void unknownLengthReportsMonotonicStartAndFinalProgress() throws Exception {
        byte[] body = "unknown-length-archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, false);
        try {
            List<WebCache.TransferProgress> progress = new ArrayList<>();

            File downloaded = WebCache.getNonCachedFile(
                    "unknown-progress",
                    url(server),
                    body.length,
                    progress::add
            );

            assertNotNull(downloaded);
            assertTrue(progress.size() >= 2);
            assertEquals(-1L, progress.get(0).contentLength());
            assertEquals(0L, progress.get(0).elapsedMillis());
            assertProgressEndsAt(progress, body.length, -1L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void progressListenerFailureDoesNotCorruptTheDownload() throws Exception {
        byte[] body = "listener-safe-archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(body, true);
        try {
            AtomicInteger callbacks = new AtomicInteger();

            File downloaded = WebCache.getNonCachedFile(
                    "listener-failure",
                    url(server),
                    body.length,
                    progress -> {
                        callbacks.incrementAndGet();
                        throw new IllegalStateException("listener failure");
                    }
            );

            assertNotNull(downloaded);
            assertTrue(callbacks.get() >= 2);
            assertEquals("listener-safe-archive", Files.readString(downloaded.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void stalledResponseTimesOutAndPreservesThePreviousCacheEntry() throws Exception {
        byte[] body = "stalled-response".getBytes(StandardCharsets.UTF_8);
        CountDownLatch prefixSent = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack", exchange -> {
            try {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body, 0, 4);
                exchange.getResponseBody().flush();
                prefixSent.countDown();
                releaseResponse.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String name = "stalled-pack";
            String url = url(server);
            File existing = cachedFile(name, url);
            Files.createDirectories(existing.toPath().getParent());
            Files.writeString(existing.toPath(), "previous", StandardCharsets.UTF_8);
            WebCache.DownloadPolicy policy = new WebCache.DownloadPolicy(
                    Duration.ofMillis(100L),
                    1,
                    Duration.ZERO
            );

            IOException failure = assertThrows(
                    IOException.class,
                    () -> WebCache.getNonCachedFile(name, url, body.length, policy, ignored -> {
                    })
            );

            assertTrue(prefixSent.await(1L, TimeUnit.SECONDS));
            assertTrue(failure.getMessage().contains("stalled"));
            assertEquals("previous", Files.readString(existing.toPath(), StandardCharsets.UTF_8));
            assertNoIncompleteDownloads(existing.getParentFile());
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    @Test
    public void transientServerFailuresRetryAndPublishTheCompleteResponse() throws Exception {
        byte[] body = "retried-response".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack", exchange -> {
            if (requests.incrementAndGet() < 3) {
                exchange.sendResponseHeaders(503, -1L);
                exchange.close();
                return;
            }
            respond(exchange, body, true);
        });
        server.start();
        try {
            WebCache.DownloadPolicy policy = new WebCache.DownloadPolicy(
                    Duration.ofSeconds(1L),
                    3,
                    Duration.ZERO
            );

            File downloaded = WebCache.getNonCachedFile(
                    "retried-pack",
                    url(server),
                    body.length,
                    policy,
                    ignored -> {
                    }
            );

            assertEquals(3, requests.get());
            assertEquals("retried-response", Files.readString(downloaded.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void permanentClientFailureDoesNotRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1L);
            exchange.close();
        });
        server.start();
        try {
            WebCache.DownloadPolicy policy = new WebCache.DownloadPolicy(
                    Duration.ofSeconds(1L),
                    3,
                    Duration.ZERO
            );

            IOException failure = assertThrows(
                    IOException.class,
                    () -> WebCache.getNonCachedFile(
                            "missing-pack",
                            url(server),
                            64L,
                            policy,
                            ignored -> {
                            }
                    )
            );

            assertTrue(failure.getMessage().contains("HTTP 404"));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(byte[] body, boolean declareLength) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack", exchange -> respond(exchange, body, declareLength));
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, byte[] body, boolean declareLength) throws IOException {
        exchange.sendResponseHeaders(200, declareLength ? body.length : 0L);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/pack";
    }

    private File cachedFile(String name, String url) {
        String hash = IO.hash(name + "*" + url);
        return IrisPlatforms.get().dataFile("cache", hash.substring(0, 2), hash.substring(3, 5), hash);
    }

    private void assertNoIncompleteDownloads(File folder) {
        File[] incomplete = folder.listFiles((File parent, String name) -> name.startsWith(".download-"));
        assertTrue(incomplete == null || incomplete.length == 0);
    }

    private void assertProgressEndsAt(List<WebCache.TransferProgress> progress, long transferredBytes,
                                      long contentLength) {
        long previousBytes = -1L;
        long previousElapsed = -1L;
        for (int index = 0; index < progress.size(); index++) {
            WebCache.TransferProgress update = progress.get(index);
            assertTrue(update.transferredBytes() >= previousBytes);
            assertTrue(update.elapsedMillis() >= previousElapsed);
            assertEquals(contentLength, update.contentLength());
            assertEquals(index == progress.size() - 1, update.complete());
            previousBytes = update.transferredBytes();
            previousElapsed = update.elapsedMillis();
        }
        assertEquals(transferredBytes, progress.get(progress.size() - 1).transferredBytes());
    }
}
