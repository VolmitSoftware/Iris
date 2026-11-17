package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The size and modification time of every file a pack copy would include, keyed by path relative to the pack root.
 * Two equal states mean no included file was added, removed or rewritten in between, unless a writer preserved
 * both size and modification time.
 */
public final class PackTreeState {
    private final Map<Path, FileStamp> files;

    PackTreeState(Map<Path, FileStamp> files) {
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    public static PackTreeState capture(Path packRoot) throws IOException {
        return GenerationPackRepository.walkPackTree(GenerationPackRepository.requirePackSource(packRoot), directory -> {
        });
    }

    Map<Path, FileStamp> files() {
        return files;
    }

    Optional<FileStamp> stamp(Path relative) {
        return Optional.ofNullable(files.get(relative));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PackTreeState state && files.equals(state.files);
    }

    @Override
    public int hashCode() {
        return files.hashCode();
    }

    record FileStamp(long sizeBytes, FileTime modified) {
    }
}
