package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class LoopbackLanguageRepository implements AutoCloseable {
    static final String REVISION_ONE = "1111111111111111111111111111111111111111";
    static final String REVISION_TWO = "2222222222222222222222222222222222222222";

    private final HttpServer server;
    private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
    private final Map<String, String> files = new ConcurrentHashMap<>();
    private final AtomicInteger rawRequests = new AtomicInteger();

    LoopbackLanguageRepository() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", this::api);
        server.createContext("/raw/", this::raw);
        server.start();
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        IrisLanguage.useLanguageSource(root.resolve("api/"), root.resolve("raw/"));
    }

    void publish(String publishedRevision, String locale, String content) {
        files.put(publishedRevision + "/" + locale, content);
    }

    void advance(String nextRevision) {
        revision.set(nextRevision);
    }

    int rawRequests() {
        return rawRequests.get();
    }

    @Override
    public void close() {
        server.stop(0);
        IrisLanguage.useLanguageSource(GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT);
    }

    private void api(HttpExchange exchange) throws IOException {
        String etag = "\"" + revision.get() + "\"";
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("ETag", etag);
        respond(exchange, ("[{\"sha\":\"" + revision.get() + "\"}]").getBytes(StandardCharsets.UTF_8));
    }

    private void raw(HttpExchange exchange) throws IOException {
        rawRequests.incrementAndGet();
        String[] parts = exchange.getRequestURI().getPath().split("/");
        String content = files.get(parts[4] + "/" + parts[parts.length - 1].replace(".toml", ""));
        if (content == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        respond(exchange, content.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, byte[] response) throws IOException {
        try {
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }
}
