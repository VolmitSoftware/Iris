package art.arcane.iris.engine.framework;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class StructurePlacementMarker {
    private static final String FAMILY_PREFIX = "@iris-structure:";
    private static final String VERSION_PREFIX = FAMILY_PREFIX + "v1:";

    private StructurePlacementMarker() {
    }

    public static String encodeStructure(String objectKey, int placementId, String structureKey) {
        String normalizedObjectKey = requireKey(objectKey, "objectKey");
        String normalizedStructureKey = requireKey(structureKey, "structureKey");
        return VERSION_PREFIX
                + encodeKey(normalizedObjectKey)
                + ":" + placementId
                + ":" + encodeKey(normalizedStructureKey);
    }

    public static Decoded decode(String marker) {
        if (marker == null || marker.isBlank()) {
            return null;
        }
        if (marker.startsWith(FAMILY_PREFIX)) {
            return decodeStructure(marker);
        }
        return decodeLegacy(marker);
    }

    private static Decoded decodeStructure(String marker) {
        if (!marker.startsWith(VERSION_PREFIX)) {
            return null;
        }
        String[] fields = marker.split(":", -1);
        if (fields.length != 5 || !fields[0].equals("@iris-structure") || !fields[1].equals("v1")) {
            return null;
        }
        try {
            String objectKey = decodeCanonicalKey(fields[2]);
            int placementId = Integer.parseInt(fields[3]);
            String structureKey = decodeCanonicalKey(fields[4]);
            if (objectKey.isBlank() || structureKey.isBlank()) {
                return null;
            }
            return new Decoded(objectKey, placementId, structureKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Decoded decodeLegacy(String marker) {
        int separator = marker.indexOf('@');
        if (separator <= 0 || separator != marker.lastIndexOf('@') || separator == marker.length() - 1) {
            return null;
        }
        String objectKey = marker.substring(0, separator);
        if (objectKey.isEmpty() || objectKey.equals("null")) {
            return null;
        }
        try {
            return new Decoded(objectKey, Integer.parseInt(marker.substring(separator + 1)), null);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String requireKey(String key, String name) {
        String required = Objects.requireNonNull(key, name);
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }

    private static String encodeKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCanonicalKey(String key) {
        String decoded = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
        if (!encodeKey(decoded).equals(key)) {
            throw new IllegalArgumentException("Non-canonical marker key");
        }
        return decoded;
    }

    public record Decoded(String objectKey, int placementId, String structureKey) {
        public boolean structureAware() {
            return structureKey != null;
        }
    }
}
