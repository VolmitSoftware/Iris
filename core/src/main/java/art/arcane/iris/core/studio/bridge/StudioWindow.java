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

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.spi.IrisLogging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the Iris Studio window on a bridge, from the app folder the studio settings name. The app keeps one window:
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

    /** False when no app is set or it could not start, so the caller's printed link is the only way in. */
    public static synchronized boolean show(StudioBridge bridge) {
        IrisSettings.IrisSettingsStudio settings = IrisSettings.get().getStudio();
        String appFolder = settings.getStudioApp();
        if (appFolder == null || appFolder.isBlank()) {
            return false;
        }
        Path app = Path.of(appFolder);
        if (!Files.isRegularFile(app.resolve(BUILT_MAIN))) {
            IrisLogging.warn("Iris Studio is not built in %s; run npm run build there.", app);
            return false;
        }
        try {
            STARTED.removeIf(process -> !process.isAlive());
            STARTED.add(start(electronBinary(settings), app, bridge.link()));
            return true;
        } catch (IOException error) {
            IrisLogging.warn("Could not open Iris Studio: %s", error.getMessage());
            return false;
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
