package art.arcane.iris.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class BuiltInPackUpdates {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern VERSION = Pattern.compile("[0-9]+");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private BuiltInPackUpdates() {
    }

    public static CompletableFuture<Map<String, Update>> check(List<String> packs) {
        return check(packs, CLIENT, pack -> URI.create(
                "https://api.github.com/repos/IrisDimensions/" + pack + "/releases/latest"));
    }

    static CompletableFuture<Map<String, Update>> check(List<String> packs, HttpClient client,
                                                        Function<String, URI> endpoint) {
        Map<String, CompletableFuture<Update>> requests = new LinkedHashMap<>();
        for (String pack : packs) {
            if (!PackDownloader.isBuiltInPack(pack) || requests.containsKey(pack)) {
                continue;
            }
            HttpRequest request = HttpRequest.newBuilder(endpoint.apply(pack))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Iris-Pack-Updates")
                    .GET()
                    .build();
            CompletableFuture<HttpResponse<String>> response = client.sendAsync(request,
                    HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), 1024L * 1024L));
            requests.put(pack, response.copy().orTimeout(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            response.cancel(true);
                        }
                    })
                    .thenApply(result -> readRelease(pack, result))
                    .exceptionally(error -> Update.unavailable()));
        }
        return CompletableFuture.allOf(requests.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, Update> updates = new LinkedHashMap<>();
                    requests.forEach((pack, request) -> updates.put(pack, request.join()));
                    return Map.copyOf(updates);
                });
    }

    private static Update readRelease(String pack, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return Update.unavailable();
        }
        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
            return Update.unavailable();
        }
        String version = release.get("tag_name").getAsString().replaceFirst("^[vV]\\+?", "");
        if (!VERSION.matcher(version).matches()) {
            return Update.unavailable();
        }
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            if ((pack + ".zip").equals(asset.get("name").getAsString())) {
                return new Update(version);
            }
        }
        return Update.unavailable();
    }

    public record Update(String version) {
        public static Update unavailable() {
            return new Update(null);
        }

        public boolean newerThan(String installedVersion) {
            return version != null && VERSION.matcher(installedVersion).matches()
                    && new BigInteger(version).compareTo(new BigInteger(installedVersion)) > 0;
        }

        public String suffix(String installedVersion) {
            if (version == null) {
                return " (update check unavailable)";
            }
            if (!VERSION.matcher(installedVersion).matches()) {
                return " (latest v" + version + ")";
            }
            return newerThan(installedVersion)
                    ? " -> v" + version + " available"
                    : "";
        }
    }
}
