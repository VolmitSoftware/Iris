package art.arcane.iris.pack.loading;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The pack folder "snippet/&lt;type&gt;" one snippet type keeps its files in. This is the one place a
 * "snippet/..." reference turns into a file.
 */
public record SnippetFolder(String snippetType) {
    public static final String PREFIX = "snippet/";
    private static final String FILE_SUFFIX = ".json";
    private static final int WORKSPACE_MATCH_DEPTH = 8;

    public String path() {
        return PREFIX + snippetType;
    }

    public boolean holds(String reference) {
        return reference.startsWith(path() + "/");
    }

    /** The file key, without ".json", a reference means: "snippet/name" is short for "snippet/&lt;type&gt;/name". */
    public String keyOf(String reference) {
        return holds(reference) ? reference : path() + "/" + reference.substring(PREFIX.length());
    }

    public Lookup find(File packFolder, String reference) {
        String key = keyOf(reference);
        return new Lookup(existingFileInsideFolder(packFolder, key), key);
    }

    /** The IDE file patterns the snippet schema applies to. */
    public List<String> workspaceFileMatches() {
        List<String> matches = new ArrayList<>();
        for (int depth = 1; depth < WORKSPACE_MATCH_DEPTH; depth++) {
            matches.add("/" + path() + "/*".repeat(depth) + FILE_SUFFIX);
        }
        return matches;
    }

    private @Nullable File existingFileInsideFolder(File packFolder, String key) {
        try {
            Path root = packFolder.toPath().toAbsolutePath().normalize();
            Path folder = root.resolve(path()).normalize();
            Path file = root.resolve(key + FILE_SUFFIX).normalize();
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                return null;
            }
            return file.toRealPath().startsWith(folder.toRealPath()) ? file.toFile() : null;
        } catch (InvalidPathException | IOException ignored) {
            return null;
        }
    }

    /** The snippet file a reference names, absent when the pack has no such file. */
    public record Lookup(@Nullable File file, String key) {
        public boolean found() {
            return file != null;
        }
    }
}
