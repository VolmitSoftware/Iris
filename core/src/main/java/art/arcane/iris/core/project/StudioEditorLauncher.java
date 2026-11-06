package art.arcane.iris.core.project;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.GuiHost;
import art.arcane.iris.spi.IrisLogging;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Opens packs and pack files in the editor chosen by {@code studio.editor}. Zed is always started on the pack folder,
 * because it only reads the pack's {@code .zed/settings.json} (and with it the schemas) for an opened project.
 */
public final class StudioEditorLauncher {
    private static final List<String> ZED_COMMANDS = List.of("zed", "zeditor", "zedit");
    private static final String WINDOWS_EXECUTABLE_SUFFIX = ".exe";

    private StudioEditorLauncher() {
    }

    public static StudioEditor configuredEditor() {
        return IrisSettings.get().getStudio().getEditor();
    }

    /** Opens the pack folder, or for VSCode its code workspace. False when nothing was started. */
    public static boolean openProject(File projectRoot, File codeWorkspace) {
        return switch (configuredEditor()) {
            case VSCODE -> openWithDesktop(codeWorkspace);
            case ZED -> startZed(projectRoot, null);
            case NONE -> false;
        };
    }

    /** Opens one file of the pack. False when nothing was started. */
    public static boolean openFile(File projectRoot, File file) {
        return switch (configuredEditor()) {
            case VSCODE -> openWithDesktop(file);
            case ZED -> startZed(projectRoot, file);
            case NONE -> false;
        };
    }

    // Zed starts as its own process, but VSCode goes through AWT, which is headless on servers and on Wayland-only sessions,
    // and suppressed inside a game client.
    public static boolean canLaunch(StudioEditor editor) {
        return switch (editor) {
            case VSCODE -> GuiHost.isAvailable() && Desktop.isDesktopSupported();
            case ZED -> true;
            case NONE -> false;
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
        Optional<Path> zed = findZedCommand();
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

    private static Optional<Path> findZedCommand() {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        List<String> directories = Arrays.stream(path.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .toList();
        return ZED_COMMANDS.stream()
                .flatMap(command -> directories.stream()
                        .flatMap(directory -> Stream.of(Path.of(directory, command), Path.of(directory, command + WINDOWS_EXECUTABLE_SUFFIX))))
                .filter(candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate))
                .findFirst();
    }
}
