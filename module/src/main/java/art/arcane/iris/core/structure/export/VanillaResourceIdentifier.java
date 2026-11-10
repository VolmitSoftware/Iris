package art.arcane.iris.core.structure.export;

import java.util.regex.Pattern;

final class VanillaResourceIdentifier {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    private VanillaResourceIdentifier() {
    }

    static boolean validNamespace(String value) {
        return value != null && NAMESPACE.matcher(value).matches();
    }

    static boolean validPath(String value) {
        if (value == null
                || !PATH.matcher(value).matches()
                || value.startsWith("/")
                || value.endsWith("/")
                || value.contains("//")) {
            return false;
        }
        for (String segment : value.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    static boolean validIdentifier(String value) {
        if (value == null) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            return false;
        }
        return validNamespace(value.substring(0, separator)) && validPath(value.substring(separator + 1));
    }

    static String normalizeConnectorIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:empty";
        }
        String normalized = value.trim();
        if (normalized.indexOf(':') < 0) {
            normalized = "minecraft:" + normalized;
        }
        if (!validIdentifier(normalized)) {
            throw new IllegalArgumentException("Invalid resource identifier '" + value + "'");
        }
        return normalized;
    }
}
