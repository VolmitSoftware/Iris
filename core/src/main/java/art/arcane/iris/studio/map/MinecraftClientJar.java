/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

final class MinecraftClientJar {
    private static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MinecraftClientJar() {
    }

    static boolean isCached() {
        return cachedJar().exists();
    }

    static CompletableFuture<ZipFile> open() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File jar = cachedJar();
                if (!jar.exists()) {
                    download(IrisPlatforms.get().minecraftVersion(), jar.toPath());
                }
                return new ZipFile(jar);
            } catch (IOException | InterruptedException error) {
                throw new IllegalStateException(readableReason(error), error);
            }
        }, MinecraftClientJar::runInBackground);
    }

    private static void runInBackground(Runnable task) {
        Thread thread = new Thread(task, "Iris Minecraft Client Download");
        thread.setDaemon(true);
        thread.start();
    }

    private static File cachedJar() {
        return IrisPlatforms.get().dataFile("cache", "minecraft-client", IrisPlatforms.get().minecraftVersion() + ".jar");
    }

    private static void download(String version, Path destination) throws IOException, InterruptedException {
        JsonObject versionInfo = fetchJson(findVersionUrl(version));
        JsonObject client = versionInfo.getAsJsonObject("downloads").getAsJsonObject("client");
        URI clientUrl = URI.create(client.get("url").getAsString());
        String expectedSha1 = client.get("sha1").getAsString();

        Path partial = destination.resolveSibling(destination.getFileName() + ".part");
        HttpResponse<InputStream> response = HTTP.send(
                HttpRequest.newBuilder(clientUrl).timeout(DOWNLOAD_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );
        requireSuccess(response.statusCode(), clientUrl);
        String actualSha1;
        try (DigestInputStream body = new DigestInputStream(response.body(), sha1())) {
            Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
            actualSha1 = HexFormat.of().formatHex(body.getMessageDigest().digest());
        }
        if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
            Files.deleteIfExists(partial);
            throw new IOException("the downloaded client jar was damaged (checksum mismatch)");
        }
        Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static URI findVersionUrl(String version) throws IOException, InterruptedException {
        for (JsonElement entry : fetchJson(VERSION_MANIFEST).getAsJsonArray("versions")) {
            JsonObject candidate = entry.getAsJsonObject();
            if (candidate.get("id").getAsString().equals(version)) {
                return URI.create(candidate.get("url").getAsString());
            }
        }
        throw new IOException("Mojang doesn't list Minecraft " + version);
    }

    private static JsonObject fetchJson(URI url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(url).timeout(CONNECT_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        requireSuccess(response.statusCode(), url);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void requireSuccess(int statusCode, URI url) throws IOException {
        if (statusCode / 100 != 2) {
            throw new IOException(url.getHost() + " answered HTTP " + statusCode);
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String readableReason(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
