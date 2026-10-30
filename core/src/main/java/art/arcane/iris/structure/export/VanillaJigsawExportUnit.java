package art.arcane.iris.structure.export;

import java.util.Objects;

public record VanillaJigsawExportUnit(
        VanillaJigsawExportSource source,
        String namespace,
        String resourcePath,
        VanillaJigsawExportSettings settings
) {
    public VanillaJigsawExportUnit {
        Objects.requireNonNull(source);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(resourcePath);
        Objects.requireNonNull(settings);
    }
}
