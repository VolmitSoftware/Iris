package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.Objects;

public record BakedVariant(String id, TemplateVolume volume, int baseY, TreeRoots roots) {
    public BakedVariant {
        VanillaResourceIdentifier.requireIdentifier(id, "template id");
        Objects.requireNonNull(volume, "Template volume");
        Objects.requireNonNull(roots, "Template roots");
        if (baseY > 0) {
            throw new IllegalArgumentException("Template base offset must not be positive, got " + baseY);
        }
    }
}
