package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;

import java.util.Objects;

public record CaveSource(String resource, IrisCaveProfile profile, int minY, int maxY) {
    public CaveSource {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(profile);
        if (maxY < minY) {
            throw new IllegalArgumentException("Cave source '" + resource + "' needs maxY at or above minY, got " + minY + ".." + maxY);
        }
    }
}
