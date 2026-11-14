package art.arcane.iris.pack.loading;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The pack folders one snippet type keeps its files in: the flat legacy folder "snippet/&lt;type&gt;", plus every
 * namespaced folder "snippet/iris/&lt;group&gt;/&lt;type&gt;" that exists in the pack. This is the one
 * place a "snippet/..." reference turns into a file.
 */
public record SnippetFolders(String legacy, List<String> namespaced) {
    public static final String PREFIX = "snippet/";
    /** Only the "iris" namespace counts, so a legacy pack's nested folders such as "snippet/procedural/objects/coral" stay legacy. */
    private static final String NAMESPACE = "iris";
    private static final String FILE_SUFFIX = ".json";
    private static final int WORKSPACE_MATCH_DEPTH = 8;

    public static SnippetFolders discover(File packFolder, String legacy) {
        List<String> namespaced = new ArrayList<>();
        for (File group : subfolders(new File(packFolder, PREFIX + NAMESPACE))) {
            if (new File(group, legacy).isDirectory()) {
                namespaced.add(PREFIX + NAMESPACE + "/" + group.getName() + "/" + legacy);
            }
        }
        return new SnippetFolders(legacy, List.copyOf(namespaced));
    }

    /** The folders as "snippet/&lt;folder&gt;", legacy first, then the namespaced ones in sorted order. */
    public List<String> folders() {
        List<String> folders = new ArrayList<>(namespaced.size() + 1);
        folders.add(PREFIX + legacy);
        folders.addAll(namespaced);
        return folders;
    }

    /**
     * The file keys, without ".json", a reference can mean. A reference already inside one of the folders names that
     * file; a short one is tried in the legacy folder first, then each namespaced one.
     */
    public List<String> candidateKeys(String reference) {
        List<String> folders = folders();
        for (String folder : folders) {
            if (reference.startsWith(folder + "/")) {
                return List.of(reference);
            }
        }

        String shortName = reference.substring(PREFIX.length());
        return folders.stream().map(folder -> folder + "/" + shortName).toList();
    }

    public Lookup find(File packFolder, String reference) {
        List<String> candidates = candidateKeys(reference);
        Path root = packFolder.toPath().toAbsolutePath().normalize();
        for (String key : candidates) {
            Path file = fileInsideFolder(root, key);
            if (file != null && Files.isRegularFile(file)) {
                return new Lookup(file.toFile(), key, candidates);
            }
        }
        return new Lookup(null, null, candidates);
    }

    /** The IDE file patterns the snippet schema applies to, covering every folder of this type. */
    public List<String> workspaceFileMatches() {
        List<String> matches = new ArrayList<>();
        for (String folder : folders()) {
            for (int depth = 1; depth < WORKSPACE_MATCH_DEPTH; depth++) {
                matches.add("/" + folder + "/*".repeat(depth) + FILE_SUFFIX);
            }
        }
        return matches;
    }

    private @Nullable Path fileInsideFolder(Path root, String key) {
        String folder = folders().stream().filter(candidate -> key.startsWith(candidate + "/")).findFirst().orElse(null);
        if (folder == null) {
            return null;
        }

        try {
            Path file = root.resolve(key + FILE_SUFFIX).normalize();
            return file.startsWith(root.resolve(folder).normalize()) ? file : null;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static File[] subfolders(File folder) {
        File[] children = folder.listFiles(File::isDirectory);
        if (children == null) {
            return new File[0];
        }
        Arrays.sort(children);
        return children;
    }

    /** The existing snippet file a reference names, or the keys that were tried when there is none. */
    public record Lookup(@Nullable File file, @Nullable String key, List<String> triedKeys) {
        public boolean found() {
            return file != null;
        }

        public String triedDescription() {
            return String.join(", ", triedKeys);
        }
    }
}
