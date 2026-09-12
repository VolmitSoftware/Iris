package art.arcane.iris.generation.decoration.tree;

import art.arcane.iris.spi.PlatformBlockState;

import java.util.Objects;

public record TreeBlockMaterial(String materialKey) {
    public TreeBlockMaterial {
        Objects.requireNonNull(materialKey, "materialKey");
        if (materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey must not be blank");
        }
    }

    public static TreeBlockMaterial of(PlatformBlockState state) {
        return of(Objects.requireNonNull(state, "state").key());
    }

    public static TreeBlockMaterial of(String blockStateKey) {
        String key = Objects.requireNonNull(blockStateKey, "blockStateKey");
        int properties = key.indexOf('[');
        return new TreeBlockMaterial(properties < 0 ? key : key.substring(0, properties));
    }

    public boolean matches(String blockStateKey) {
        return equals(of(blockStateKey));
    }
}
