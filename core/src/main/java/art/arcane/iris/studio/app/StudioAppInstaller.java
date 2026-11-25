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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.studio.view.GuiHost;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Installs the studio app into the Iris data folder in the background at startup: the app from the jar, and the
 * Electron build it runs on from Electron's release. Servers without a display skip it, since the window could not
 * open there anyway.
 */
public final class StudioAppInstaller {
    static final String MANIFEST = "META-INF/iris/studio-app.properties";
    private static final String DATA_FOLDER = "studio";
    private static final String STAGING_SUFFIX = ".part";

    private static StudioAppState state = StudioAppState.NOT_STARTED;
    private static StudioAppInstall installed;
    private static String unavailableReason = "";

    private StudioAppInstaller() {
    }

    /** Starts the install unless it is done, running, or off; the state it leaves, for a caller about to open the app. */
    public static synchronized StudioAppState installInBackground() {
        if (state == StudioAppState.DOWNLOADING || state == StudioAppState.READY || state == StudioAppState.UNAVAILABLE) {
            return state;
        }
        state = blockedState();
        if (state != StudioAppState.DOWNLOADING) {
            return state;
        }
        Thread.ofPlatform().daemon().name("Iris Studio install").start(StudioAppInstaller::install);
        return state;
    }

    public static synchronized StudioAppInstall installed() {
        return installed;
    }

    public static synchronized String unavailableReason() {
        return unavailableReason;
    }

    static boolean hasDisplay(String operatingSystem, Map<String, String> environment) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        if (os.contains("win") || os.contains("mac")) {
            return true;
        }
        return isSet(environment.get("DISPLAY")) || isSet(environment.get("WAYLAND_DISPLAY"));
    }

    private static StudioAppState blockedState() {
        if (!IrisSettings.get().getStudio().isDownloadApp()) {
            return StudioAppState.DOWNLOAD_OFF;
        }
        if (GuiHost.isDesktopSuppressed() || !hasDisplay(System.getProperty("os.name", ""), System.getenv())) {
            return StudioAppState.NO_DISPLAY;
        }
        return StudioAppState.DOWNLOADING;
    }

    private static void install() {
        long startedNanos = System.nanoTime();
        try {
            ElectronRuntime.Platform platform = ElectronRuntime.Platform.current();
            if (platform == null) {
                throw new IOException("Electron has no build for " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            }
            ElectronRuntime electron = ElectronRuntime.pinned(manifest(), platform);
            Path root = IrisPlatforms.get().dataFolder(DATA_FOLDER).toPath();
            Path executable = installElectron(electron, root);
            Path app = BundledStudioApp.install(root, StudioAppInstaller.class.getClassLoader());
            removeStaleInstalls(root, List.of(app, root.resolve(electron.folderName())));
            ready(new StudioAppInstall(executable, app), (System.nanoTime() - startedNanos) / 1_000_000L);
        } catch (IOException | RuntimeException error) {
            unavailable(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private static Path installElectron(ElectronRuntime electron, Path root) throws IOException {
        if (!Files.isDirectory(root.resolve(electron.folderName()))) {
            IrisLogging.info("Downloading the studio app runtime from %s", electron.source());
        }
        return electron.install(root, ElectronRuntime.Downloader.http());
    }

    private static Properties manifest() throws IOException {
        Properties manifest = new Properties();
        try (InputStream input = StudioAppInstaller.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            if (input == null) {
                throw new IOException("this build does not bundle the studio app; point studio.studioApp at a studio checkout");
            }
            manifest.load(input);
        }
        return manifest;
    }

    /** Drops the Electron builds and app copies earlier versions left, and any half-finished download or unpack. */
    private static void removeStaleInstalls(Path root, List<Path> current) {
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (current.contains(child) || !isInstallArtifact(child)) {
                    continue;
                }
                try {
                    ZipArchives.deleteRecursively(child);
                } catch (IOException error) {
                    IrisLogging.debug("Could not remove the old studio install %s: %s", child, error.getMessage());
                }
            }
        } catch (IOException error) {
            IrisLogging.debug("Could not list the studio folder %s: %s", root, error.getMessage());
        }
    }

    private static boolean isInstallArtifact(Path child) {
        String name = child.getFileName().toString();
        return name.startsWith("electron-v") || BundledStudioApp.isAppFolder(child) || name.endsWith(STAGING_SUFFIX);
    }

    private static synchronized void ready(StudioAppInstall install, long elapsedMillis) {
        installed = install;
        unavailableReason = "";
        state = StudioAppState.READY;
        IrisLogging.info("Studio app ready in %d ms", elapsedMillis);
    }

    private static synchronized void unavailable(String reason) {
        installed = null;
        unavailableReason = reason;
        state = StudioAppState.UNAVAILABLE;
        IrisLogging.warn("Studio app unavailable: %s", reason);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
