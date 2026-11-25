/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * The Electron build the studio app runs on. The build pins its version and each platform's SHA-256 in the studio app
 * manifest; the archive comes from Electron's own GitHub release and is unpacked once into the studio folder.
 */
final class ElectronRuntime {
    private static final String RELEASE_ROOT = "https://github.com/electron/electron/releases/download/";
    private static final String VERSION_KEY = "electron.version";
    private static final String CHECKSUM_SUFFIX = ".sha256";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(15);

    private final String version;
    private final Platform platform;
    private final String sha256;

    private ElectronRuntime(String version, Platform platform, String sha256) {
        this.version = version;
        this.platform = platform;
        this.sha256 = sha256;
    }

    static ElectronRuntime pinned(Properties manifest, Platform platform) throws IOException {
        String version = manifest.getProperty(VERSION_KEY);
        String sha256 = manifest.getProperty(platform.name() + CHECKSUM_SUFFIX);
        if (version == null || sha256 == null) {
            throw new IOException("no Electron build is pinned for " + platform.name());
        }
        return new ElectronRuntime(version, platform, sha256.toLowerCase(Locale.ROOT));
    }

    String folderName() {
        return "electron-v" + version + "-" + platform.name();
    }

    URI source() {
        return URI.create(RELEASE_ROOT + "v" + version + "/" + folderName() + ".zip");
    }

    /** The Electron executable under {@code root}, downloaded, checked and unpacked first when it is not there yet. */
    Path install(Path root, Downloader downloader) throws IOException {
        Path executable = root.resolve(folderName()).resolve(platform.executable());
        if (Files.isRegularFile(executable)) {
            return executable;
        }
        Path archive = root.resolve(folderName() + ".zip.part");
        try {
            downloader.download(source(), archive);
            requireChecksum(archive);
            if (platform.macOS()) {
                ZipArchives.extractWithDitto(archive, root.resolve(folderName()));
            } else {
                ZipArchives.extract(archive, root.resolve(folderName()));
            }
        } finally {
            Files.deleteIfExists(archive);
        }
        if (!Files.isRegularFile(executable)) {
            throw new IOException(source() + " has no " + platform.executable());
        }
        return executable;
    }

    private void requireChecksum(Path archive) throws IOException {
        String actual = sha256(archive);
        if (!actual.equals(sha256)) {
            throw new IOException(source() + " failed its checksum (expected " + sha256 + ", got " + actual + ")");
        }
    }

    static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    @FunctionalInterface
    interface Downloader {
        void download(URI source, Path target) throws IOException;

        static Downloader http() {
            return (source, target) -> {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(source)
                        .timeout(DOWNLOAD_TIMEOUT)
                        .header("User-Agent", "Iris")
                        .build();
                try {
                    HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
                    if (response.statusCode() != 200) {
                        throw new IOException(source + " answered HTTP " + response.statusCode());
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("download of " + source + " was interrupted", interrupted);
                }
            };
        }
    }

    /** An Electron release platform, named as its archives are, and where the executable sits inside them. */
    record Platform(String name, String executable) {
        static Platform current() {
            return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        }

        /** Null for a system Electron publishes no build for. */
        static Platform of(String operatingSystem, String architecture) {
            String os = operatingSystem.toLowerCase(Locale.ROOT);
            String arch = switch (architecture) {
                case "aarch64", "arm64" -> "arm64";
                case "amd64", "x86_64" -> "x64";
                default -> null;
            };
            if (arch == null) {
                return null;
            }
            if (os.contains("linux")) {
                return new Platform("linux-" + arch, "electron");
            }
            if (os.contains("win")) {
                return new Platform("win32-" + arch, "electron.exe");
            }
            if (os.contains("mac")) {
                return new Platform("darwin-" + arch, "Electron.app/Contents/MacOS/Electron");
            }
            return null;
        }

        boolean macOS() {
            return name.startsWith("darwin");
        }
    }
}
