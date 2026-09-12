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

package art.arcane.iris.pack.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModrinthResolver {
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "VolmitSoftware/Iris (datapack-ingest)";
    private static final String DATAPACK_LOADER = "datapack";
    private static final int MAX_API_RESPONSE_CHARS = 8 * 1024 * 1024;

    private ModrinthResolver() {
    }

    public static ResolvedDatapack resolve(String rawUrl, String serverMcVersion) throws IOException {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) {
            throw new IOException("Empty datapack url");
        }

        ModrinthRef ref = parse(url);
        if (ref == null) {
            return directResolve(url);
        }

        JsonArray versions = getJsonArray(API + "/project/" + ref.slug + "/version");
        if (versions == null || versions.isEmpty()) {
            throw new IOException("Modrinth project '" + ref.slug + "' returned no versions");
        }

        JsonObject version = ref.versionToken == null
                ? selectLatestDatapackVersion(versions, serverMcVersion)
                : selectVersionByToken(versions, ref.versionToken);
        if (version == null) {
            String detail = ref.versionToken == null
                    ? (serverMcVersion == null || serverMcVersion.isBlank()
                    ? "no datapack-loader version"
                    : "no datapack-loader version compatible with Minecraft " + serverMcVersion)
                    : "no version matching '" + ref.versionToken + "'";
            throw new IOException("Modrinth project '" + ref.slug + "' has " + detail);
        }
        if (!isDatapack(version)) {
            throw new IOException("Modrinth version for '" + ref.slug + "' is not published for the datapack loader");
        }
        if (serverMcVersion != null && !serverMcVersion.isBlank() && !gameVersionsContains(version, serverMcVersion)) {
            throw new IOException("Modrinth version for '" + ref.slug + "' is not compatible with Minecraft " + serverMcVersion);
        }

        JsonObject file = selectFile(version);
        if (file == null) {
            throw new IOException("Modrinth version for '" + ref.slug + "' has no downloadable file");
        }

        return toResolved(version, file, ref.slug);
    }

    private static ModrinthRef parse(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("modrinth.com") || host.equalsIgnoreCase("www.modrinth.com"))) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        for (String segment : uri.getPath().split("/")) {
            if (!segment.isBlank()) {
                parts.add(segment);
            }
        }
        if (parts.size() < 2) {
            return null;
        }

        String slug = parts.get(1);
        String token = null;
        for (int i = 2; i + 1 < parts.size(); i++) {
            if (parts.get(i).equalsIgnoreCase("version")) {
                token = parts.get(i + 1);
                break;
            }
        }
        return new ModrinthRef(slug, token);
    }

    private static JsonObject selectVersionByToken(JsonArray versions, String token) {
        JsonObject datapackMatch = null;
        JsonObject anyMatch = null;
        String normalizedToken = normalizeVersion(token);

        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject version = element.getAsJsonObject();
            String id = optString(version, "id");
            String number = optString(version, "version_number");
            boolean matches = token.equals(id)
                    || token.equalsIgnoreCase(number)
                    || normalizedToken.equals(normalizeVersion(number));
            if (!matches) {
                continue;
            }
            if (anyMatch == null) {
                anyMatch = version;
            }
            if (isDatapack(version)) {
                datapackMatch = version;
                break;
            }
        }

        return datapackMatch != null ? datapackMatch : anyMatch;
    }

    static JsonObject selectLatestDatapackVersion(JsonArray versions, String serverMcVersion) {
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject version = element.getAsJsonObject();
            if (!isDatapack(version)) {
                continue;
            }
            if (serverMcVersion == null || serverMcVersion.isBlank() || gameVersionsContains(version, serverMcVersion)) {
                return version;
            }
        }
        return null;
    }

    static JsonObject selectFile(JsonObject version) {
        JsonArray files = optArray(version, "files");
        if (files == null || files.isEmpty()) {
            return null;
        }

        JsonObject primaryZip = null;
        JsonObject anyZip = null;
        JsonObject primary = null;
        JsonObject first = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            if (first == null) {
                first = file;
            }
            boolean isPrimary = optBoolean(file, "primary");
            boolean isZip = optString(file, "filename").toLowerCase(Locale.ROOT).endsWith(".zip");
            if (isPrimary && primary == null) {
                primary = file;
            }
            if (isZip && anyZip == null) {
                anyZip = file;
            }
            if (isPrimary && isZip && primaryZip == null) {
                primaryZip = file;
            }
        }

        if (primaryZip != null) {
            return primaryZip;
        }
        if (anyZip != null) {
            return anyZip;
        }
        if (primary != null) {
            return primary;
        }
        return first;
    }

    private static ResolvedDatapack toResolved(JsonObject version, JsonObject file, String slug) throws IOException {
        String downloadUrl = optString(file, "url");
        String filename = optString(file, "filename");
        if (downloadUrl.isBlank() || filename.isBlank()) {
            throw new IOException("Modrinth version for '" + slug + "' has an invalid downloadable file");
        }
        String sha1 = null;
        if (file.has("hashes") && file.get("hashes").isJsonObject()) {
            sha1 = optString(file.getAsJsonObject("hashes"), "sha1");
            if (!sha1.isBlank() && !sha1.matches("(?i)[0-9a-f]{40}")) {
                throw new IOException("Modrinth version for '" + slug + "' has an invalid SHA-1 checksum");
            }
        }
        return new ResolvedDatapack(downloadUrl, filename, sha1, optString(version, "id"), optString(version, "version_number"), slug, false);
    }

    private static ResolvedDatapack directResolve(String url) {
        String filename = directFilename(url);
        String identity = directIdentity(url);
        return new ResolvedDatapack(url, filename, null, "direct-" + identity, "direct", null, true);
    }

    private static String directFilename(String url) {
        try {
            URI uri = new URI(url);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                Path fileName = Path.of(uri).getFileName();
                return fileName == null || fileName.toString().isBlank()
                        ? "datapack.zip"
                        : fileName.toString();
            }
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                int slash = path.lastIndexOf('/');
                String fileName = slash >= 0 ? path.substring(slash + 1) : path;
                if (!fileName.isBlank()) {
                    return fileName;
                }
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }
        return "datapack.zip";
    }

    static String directIdentity(String url) {
        String normalized = url;
        try {
            URI parsed = new URI(url).normalize();
            normalized = new URI(
                    parsed.getScheme() == null ? null : parsed.getScheme().toLowerCase(Locale.ROOT),
                    parsed.getUserInfo(),
                    parsed.getHost() == null ? null : parsed.getHost().toLowerCase(Locale.ROOT),
                    parsed.getPort(),
                    parsed.getPath(),
                    parsed.getQuery(),
                    null
            ).toASCIIString();
        } catch (URISyntaxException ignored) {
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static boolean isDatapack(JsonObject version) {
        JsonArray loaders = optArray(version, "loaders");
        if (loaders == null) {
            return false;
        }
        for (JsonElement loader : loaders) {
            if (isString(loader) && DATAPACK_LOADER.equalsIgnoreCase(loader.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean gameVersionsContains(JsonObject version, String mc) {
        JsonArray gameVersions = optArray(version, "game_versions");
        if (gameVersions == null) {
            return false;
        }
        for (JsonElement gv : gameVersions) {
            if (isString(gv) && mc.equalsIgnoreCase(gv.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeVersion(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String optString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !isString(object.get(key))) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static JsonArray optArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static boolean optBoolean(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        return object.get(key).getAsBoolean();
    }

    private static boolean isString(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static JsonArray getJsonArray(String url) throws IOException {
        String body = httpGet(url);
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) {
                throw new IOException("Unexpected response from " + url);
            }
            return parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON response from " + url, e);
        }
    }

    private static String httpGet(String url) throws IOException {
        URL target = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);

        // Everything past openConnection under one finally: a timeout/DNS/TLS throw inside
        // getResponseCode abandoned the connection undisconnected.
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + " from " + url);
            }

            try (InputStream input = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return readBoundedApiResponse(reader, url, MAX_API_RESPONSE_CHARS);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String readBoundedApiResponse(Reader reader, String source, int maxChars) throws IOException {
        if (maxChars < 1) {
            throw new IllegalArgumentException("Response character limit must be positive");
        }
        int bufferSize = maxChars < 8192 ? maxChars + 1 : 8192;
        char[] buffer = new char[bufferSize];
        StringBuilder builder = new StringBuilder(Math.min(maxChars, 8192));
        while (true) {
            int remaining = maxChars - builder.length();
            int requested = remaining >= buffer.length ? buffer.length : remaining + 1;
            int length = reader.read(buffer, 0, requested);
            if (length == -1) {
                return builder.toString();
            }
            if (length == 0) {
                continue;
            }
            if (length > remaining) {
                throw new IOException("Oversized response from " + source);
            }
            builder.append(buffer, 0, length);
        }
    }

    private static final class ModrinthRef {
        private final String slug;
        private final String versionToken;

        private ModrinthRef(String slug, String versionToken) {
            this.slug = slug;
            this.versionToken = versionToken;
        }
    }

    public static final class ResolvedDatapack {
        private final String downloadUrl;
        private final String fileName;
        private final String sha1;
        private final String versionId;
        private final String versionNumber;
        private final String projectSlug;
        private final boolean direct;

        public ResolvedDatapack(String downloadUrl, String fileName, String sha1, String versionId, String versionNumber, String projectSlug, boolean direct) {
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
            this.sha1 = sha1;
            this.versionId = versionId;
            this.versionNumber = versionNumber;
            this.projectSlug = projectSlug;
            this.direct = direct;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public String getFileName() {
            return fileName;
        }

        public String getSha1() {
            return sha1;
        }

        public String getVersionId() {
            return versionId;
        }

        public String getVersionNumber() {
            return versionNumber;
        }

        public String getProjectSlug() {
            return projectSlug;
        }

        public boolean isDirect() {
            return direct;
        }
    }
}
