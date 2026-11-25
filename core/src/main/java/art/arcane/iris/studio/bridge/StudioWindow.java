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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.app.StudioAppInstall;
import art.arcane.iris.studio.app.StudioAppInstaller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the Iris Studio window on a bridge, from the studio checkout the settings name or else from the app the
 * server downloaded at startup. The app keeps one window:
 * a second start hands its link to the open window and exits. Windows the server started close when it stops, as
 * their bridges go with it.
 */
public final class StudioWindow {
    private static final Path LOG_FILE = Path.of(System.getProperty("user.home"), ".iris", "studio", "window.log");
    private static final String DEFAULT_ELECTRON = "electron";
    private static final String BUILT_MAIN = "out/main/index.js";
    /** Set by terminals that are Electron apps themselves; left in, Electron would start as plain Node with no window. */
    private static final String RUN_AS_PLAIN_NODE = "ELECTRON_RUN_AS_NODE";
    private static final List<Process> STARTED = new ArrayList<>();

    private StudioWindow() {
    }

    public static synchronized StudioLaunch show(StudioBridge bridge) {
        IrisSettings.IrisSettingsStudio settings = IrisSettings.get().getStudio();
        String checkout = settings.getStudioApp();
        if (checkout != null && !checkout.isBlank()) {
            return showCheckout(Path.of(checkout), electronBinary(settings), bridge.link());
        }
        return showDownloaded(bridge.link());
    }

    private static StudioLaunch showCheckout(Path app, String electron, String link) {
        if (!Files.isRegularFile(app.resolve(BUILT_MAIN))) {
            IrisLogging.warn("Iris Studio is not built in %s; run npm run build there.", app);
            return StudioLaunch.NOT_BUILT;
        }
        return launch(electron, app, link);
    }

    private static StudioLaunch showDownloaded(String link) {
        return switch (StudioAppInstaller.installInBackground()) {
            case READY -> {
                StudioAppInstall install = StudioAppInstaller.installed();
                yield launch(install.electron().toString(), install.app(), link);
            }
            case NOT_STARTED, DOWNLOADING -> StudioLaunch.DOWNLOADING;
            case DOWNLOAD_OFF -> StudioLaunch.DOWNLOAD_OFF;
            case NO_DISPLAY -> StudioLaunch.NO_DISPLAY;
            case UNAVAILABLE -> {
                IrisLogging.warn("Studio app unavailable: %s", StudioAppInstaller.unavailableReason());
                yield StudioLaunch.UNAVAILABLE;
            }
        };
    }

    private static StudioLaunch launch(String electron, Path app, String link) {
        try {
            STARTED.removeIf(process -> !process.isAlive());
            STARTED.add(start(electron, app, link));
            return StudioLaunch.OPENED;
        } catch (IOException error) {
            IrisLogging.warn("Could not open Iris Studio: %s", error.getMessage());
            return StudioLaunch.FAILED_TO_START;
        }
    }

    public static synchronized void closeAll() {
        STARTED.forEach(Process::destroy);
        STARTED.clear();
    }

    private static Process start(String electron, Path app, String link) throws IOException {
        Files.createDirectories(LOG_FILE.getParent());
        ProcessBuilder builder = new ProcessBuilder(electron, app.toString(), link)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(LOG_FILE.toFile()));
        builder.environment().remove(RUN_AS_PLAIN_NODE);
        return builder.start();
    }

    private static String electronBinary(IrisSettings.IrisSettingsStudio settings) {
        String electron = settings.getElectron();
        return electron == null || electron.isBlank() ? DEFAULT_ELECTRON : electron;
    }
}
