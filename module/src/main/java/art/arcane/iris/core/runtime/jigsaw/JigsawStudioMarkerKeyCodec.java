package art.arcane.iris.core.runtime.jigsaw;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class JigsawStudioMarkerKeyCodec {
    private static final String STUDIO_NAMESPACE = "iris:";
    private static final Pattern INTERNAL_PATH = Pattern.compile("[a-z0-9._-]+(?:/[a-z0-9._-]+)*");

    private JigsawStudioMarkerKeyCodec() {
    }

    public static String encodePool(String internalPoolKey) {
        return STUDIO_NAMESPACE + requireInternalPath(internalPoolKey, "pool");
    }

    public static String decodePool(String markerPoolKey) {
        String markerKey = Objects.requireNonNull(markerPoolKey, "Jigsaw Studio marker pool key").trim();
        if (!markerKey.toLowerCase(Locale.ROOT).startsWith(STUDIO_NAMESPACE)) {
            throw new IllegalArgumentException(
                    "Jigsaw Studio marker pools must use iris:<owned-pool-key>, not '" + markerKey + "'");
        }
        return requireInternalPath(markerKey.substring(STUDIO_NAMESPACE.length()), "pool");
    }

    public static String requireInternalPath(String value, String kind) {
        String path = Objects.requireNonNull(value, "Jigsaw Studio " + kind + " key").trim();
        if (!INTERNAL_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Jigsaw Studio " + kind
                    + " keys must use lowercase [a-z0-9._-/] resource-path characters");
        }
        return path;
    }
}
