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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisLogging;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Opens pack files in the editor chosen by {@code studio.editor}. Zed is always started on the pack folder,
 * because it only reads the pack's {@code .zed/settings.json} (and with it the schemas) for an opened project.
 */
public final class StudioEditorLauncher {
    private static final List<String> ZED_COMMANDS = List.of("zed", "zeditor", "zedit");
    private static final List<String> VSCODE_COMMANDS = List.of("code", "codium");
    private static final List<String> EXECUTABLE_SUFFIXES = List.of("", ".exe", ".cmd");

    private StudioEditorLauncher() {
    }

    /** The editor to open, with {@link StudioEditor#AUTO} replaced by the one detected on this machine. */
    public static StudioEditor resolvedEditor() {
        StudioEditor configured = IrisSettings.get().getStudio().getEditor();
        return configured == StudioEditor.AUTO ? detectInstalledEditor(System.getenv("PATH")) : configured;
    }

    // Zed has no workspace file the OS could route to it, so it only wins when it is the sole editor found.
    static StudioEditor detectInstalledEditor(String path) {
        boolean onlyZedInstalled = findCommand(ZED_COMMANDS, path).isPresent() && findCommand(VSCODE_COMMANDS, path).isEmpty();
        return onlyZedInstalled ? StudioEditor.ZED : StudioEditor.VSCODE;
    }

    public static boolean openProject(File projectRoot, File codeWorkspace) {
        return switch (resolvedEditor()) {
            case VSCODE -> openWithDesktop(codeWorkspace);
            case ZED -> startZed(projectRoot, null);
            case NONE, AUTO -> false;
        };
    }

    public static boolean openFile(File projectRoot, File file) {
        return switch (resolvedEditor()) {
            case VSCODE -> openWithDesktop(file);
            case ZED -> startZed(projectRoot, file);
            case NONE, AUTO -> false;
        };
    }

    // Zed starts as its own process, but VSCode goes through AWT, which is headless on servers and on Wayland-only sessions.
    public static boolean canLaunch(StudioEditor editor) {
        return switch (editor) {
            case VSCODE -> !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported();
            case ZED -> true;
            case NONE -> false;
            case AUTO -> canLaunch(detectInstalledEditor(System.getenv("PATH")));
        };
    }

    private static boolean openWithDesktop(File target) {
        if (!canLaunch(StudioEditor.VSCODE)) {
            return false;
        }
        IrisLogging.msg("Opening VSCode. You may see the output from VSCode.");
        IrisLogging.msg("VSCode output always starts with: '(node:#####) electron'");
        Thread launcherThread = new Thread(() -> {
            try {
                Desktop.getDesktop().open(target);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }, "Iris-VSCode-Launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
        return true;
    }

    private static boolean startZed(File projectRoot, File file) {
        Optional<Path> zed = findCommand(ZED_COMMANDS, System.getenv("PATH"));
        if (zed.isEmpty()) {
            IrisLogging.warn("Could not open Zed: none of " + String.join(", ", ZED_COMMANDS) + " is on the PATH.");
            return false;
        }

        List<String> command = new ArrayList<>(List.of(zed.get().toString(), projectRoot.getAbsolutePath()));
        if (file != null) {
            command.add(file.getAbsolutePath());
        }
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    private static Optional<Path> findCommand(List<String> commands, String path) {
        if (path == null) {
            return Optional.empty();
        }
        List<String> directories = Arrays.stream(path.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .toList();
        return commands.stream()
                .flatMap(command -> directories.stream()
                        .flatMap(directory -> EXECUTABLE_SUFFIXES.stream().map(suffix -> Path.of(directory, command + suffix))))
                .filter(candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate))
                .findFirst();
    }
}
