package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Objects;

public record TreeBlockMaterial(String materialKey) {
    public TreeBlockMaterial {
        Objects.requireNonNull(materialKey, "materialKey");
        if (materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey must not be blank");
        }
    }

    public static TreeBlockMaterial of(NativeBlockState state) {
        NativeBlockState resolved = Objects.requireNonNull(state, "state");
        String materialKey = resolved.materialKey();
        return materialKey == null ? of(resolved.key()) : new TreeBlockMaterial(materialKey);
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
