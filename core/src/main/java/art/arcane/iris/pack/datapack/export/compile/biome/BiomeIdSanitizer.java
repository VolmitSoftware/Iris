package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.Objects;
import java.util.Set;

public final class BiomeIdSanitizer {
    private static final int FIRST_SUFFIX = 2;

    private BiomeIdSanitizer() {
    }

    public static String sanitize(String namespace, String loadKey, Set<String> taken) {
        VanillaResourceIdentifier.requireNamespace(namespace, "biome namespace");
        Objects.requireNonNull(taken);
        String path = VanillaResourceIdentifier.sanitizePath(loadKey);
        String id = namespace + ":" + path;
        int suffix = FIRST_SUFFIX;
        while (taken.contains(id)) {
            id = namespace + ":" + path + "_" + suffix;
            suffix++;
        }
        taken.add(id);
        return id;
    }
}
