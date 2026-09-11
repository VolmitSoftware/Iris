package art.arcane.iris.modded;

import art.arcane.iris.engine.framework.Engine;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ModdedWorldgenIds {
    private static final String NAMESPACE = "irisworldgen";

    private ModdedWorldgenIds() {
    }

    public static String presetRef(String pack, String dimension) {
        return NAMESPACE + ":" + scopedPath(pack, dimension) + "/preset";
    }

    public static String dimensionTypeRef(String pack, String dimension) {
        return NAMESPACE + ":" + scopedPath(pack, dimension) + "/dimension_type";
    }

    public static String biomePathPrefix(String pack, String dimension) {
        return scopedPath(pack, dimension) + "/biomes";
    }

    public static String biomeRef(String pack, String dimension, String biome) {
        return NAMESPACE + ":" + biomePathPrefix(pack, dimension) + "/"
                + biome.toLowerCase(Locale.ROOT);
    }

    public static String biomeRef(Engine engine, String biome) {
        return engine.getData().customBiomeResourceKey(engine.getDimension(), biome);
    }

    public static List<String> legacyBiomeRefs(String pack, String dimension, String biome) {
        String scoped = biomeRef(pack, dimension, biome);
        String unscoped = dimension.toLowerCase(Locale.ROOT) + ":" + biome.toLowerCase(Locale.ROOT);
        return scoped.equals(unscoped) ? List.of(scoped) : List.of(scoped, unscoped);
    }

    static Optional<ScopedDimension> scopedDimensionType(String dimensionTypeRef) {
        String prefix = NAMESPACE + ":";
        if (dimensionTypeRef == null || !dimensionTypeRef.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = dimensionTypeRef.substring(prefix.length()).split("/");
        if (parts.length != 5
                || !"packs".equals(parts[0])
                || !"dimensions".equals(parts[2])
                || !"dimension_type".equals(parts[4])) {
            return Optional.empty();
        }
        String pack = decode(parts[1]);
        String dimension = decode(parts[3]);
        if (pack == null || dimension == null) {
            return Optional.empty();
        }
        return Optional.of(new ScopedDimension(pack, dimension));
    }

    public static String displayName(String presetPath) {
        String[] parts = presetPath.split("/");
        if (parts.length != 5 || !"packs".equals(parts[0])
                || !"dimensions".equals(parts[2]) || !"preset".equals(parts[4])) {
            return null;
        }
        String pack = decode(parts[1]);
        String dimension = decode(parts[3]);
        if (pack == null || dimension == null) {
            return null;
        }
        String packLabel = title(pack);
        return pack.equalsIgnoreCase(dimension)
                ? "IRIS:" + packLabel
                : "IRIS:" + packLabel + " / " + title(dimension);
    }

    public static String generatorIdentity(String packDimension) {
        int separator = packDimension.indexOf(':');
        String pack = separator >= 0 ? packDimension.substring(0, separator) : packDimension;
        String dimension = separator >= 0 ? packDimension.substring(separator + 1) : packDimension;
        return "iris:" + (pack.equalsIgnoreCase(dimension)
                ? pack.toLowerCase(Locale.ROOT)
                : pack.toLowerCase(Locale.ROOT) + "/" + dimension.toLowerCase(Locale.ROOT));
    }

    private static String scopedPath(String pack, String dimension) {
        return "packs/" + encode(pack) + "/dimensions/" + encode(dimension);
    }

    private static String encode(String value) {
        return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if ((value.length() & 1) != 0) {
            return null;
        }
        byte[] bytes = new byte[value.length() / 2];
        try {
            for (int index = 0; index < bytes.length; index++) {
                int high = Character.digit(value.charAt(index * 2), 16);
                int low = Character.digit(value.charAt(index * 2 + 1), 16);
                if (high < 0 || low < 0) {
                    return null;
                }
                bytes[index] = (byte) ((high << 4) | low);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String title(String value) {
        String normalized = value.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    record ScopedDimension(String pack, String dimension) {
    }
}
