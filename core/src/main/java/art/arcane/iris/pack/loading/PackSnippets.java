package art.arcane.iris.pack.loading;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * One pack's snippet folders, discovered at most once per snippet type. Create one per validation run rather than
 * sharing it, since pack files change between runs during hotload.
 */
public final class PackSnippets {
    private final File packFolder;
    private final Map<String, SnippetFolders> foldersByType = new HashMap<>();

    public PackSnippets(File packFolder) {
        this.packFolder = packFolder;
    }

    public File packFolder() {
        return packFolder;
    }

    public SnippetFolders folders(String snippetType) {
        return foldersByType.computeIfAbsent(snippetType, type -> SnippetFolders.discover(packFolder, type));
    }

    public SnippetFolders.Lookup find(String snippetType, String reference) {
        return folders(snippetType).find(packFolder, reference);
    }
}
