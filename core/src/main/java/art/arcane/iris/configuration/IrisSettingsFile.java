package art.arcane.iris.configuration;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.config.TomlCodec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads and writes the Iris settings file. Settings live in iris.toml so each key can carry a comment.
 */
public final class IrisSettingsFile {
    public static final String FILE_NAME = "iris.toml";
    private static final String SOURCE_TAG = "iris";

    private IrisSettingsFile() {
    }

    public static File location() {
        return IrisPlatforms.get().dataFile(FILE_NAME);
    }

    public static void write(IrisSettings settings, File file) throws IOException {
        Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
        Files.writeString(file.toPath(), render(settings), StandardCharsets.UTF_8);
    }

    static IrisSettings load() {
        File file = location();
        if (file.exists()) {
            return loadExisting(file);
        }

        IrisSettings defaults = new IrisSettings();
        rewrite(file, defaults);
        return defaults;
    }

    static IrisSettings parse(String toml) throws IOException {
        IrisSettings parsed = TomlCodec.fromToml(toml, IrisSettings.class);
        if (parsed == null) {
            throw new IOException("Iris settings did not contain a table");
        }
        parsed.fillMissingSections();
        return parsed;
    }

    static String render(IrisSettings settings) {
        return TomlCodec.toToml(settings, SOURCE_TAG);
    }

    private static IrisSettings loadExisting(File file) {
        String raw;
        IrisSettings loaded;
        try {
            raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            loaded = parse(raw);
        } catch (Throwable failure) {
            // reportError reads the settings again while they are still loading, so only log the line.
            IrisLogging.error("Configuration Error in " + FILE_NAME + "! " + rootCause(failure)
                    + ". Using defaults until the file is fixed.");
            return new IrisSettings();
        }

        if (!sameContent(raw, render(loaded))) {
            rewrite(file, loaded);
        }
        return loaded;
    }

    private static boolean rewrite(File file, IrisSettings settings) {
        try {
            write(settings, file);
            return true;
        } catch (IOException failure) {
            IrisLogging.reportError("Could not rewrite " + file.getAbsolutePath()
                    + "; settings changes made in game will not survive a restart.", failure);
            return false;
        }
    }

    private static boolean sameContent(String raw, String canonical) {
        return normalizeLineEndings(raw).strip().equals(normalizeLineEndings(canonical).strip());
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
