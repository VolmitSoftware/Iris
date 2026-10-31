package art.arcane.iris.pack.datapack.export;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public final class VanillaResourceIdentifier {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");
    private static final Pattern INVALID_PATH_CHARACTER = Pattern.compile("[^a-z0-9._-]");

    private VanillaResourceIdentifier() {
    }

    public static boolean validNamespace(String value) {
        return value != null && NAMESPACE.matcher(value).matches();
    }

    public static boolean validPath(String value) {
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

    public static boolean validIdentifier(String value) {
        if (value == null) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            return false;
        }
        return validNamespace(value.substring(0, separator)) && validPath(value.substring(separator + 1));
    }

    public static String requireNamespace(String value, String role) {
        if (!validNamespace(value)) {
            throw invalid(value, role);
        }
        return value;
    }

    public static String requirePath(String value, String role) {
        if (!validPath(value)) {
            throw invalid(value, role);
        }
        return value;
    }

    public static String requireIdentifier(String value, String role) {
        if (!validIdentifier(value)) {
            throw invalid(value, role);
        }
        return value;
    }

    public static String sanitizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cannot derive a resource path from '" + value + "'");
        }
        String lowered = value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        StringJoiner path = new StringJoiner("/");
        for (String segment : lowered.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String cleaned = INVALID_PATH_CHARACTER.matcher(segment).replaceAll("_");
            path.add(cleaned.equals(".") || cleaned.equals("..") ? cleaned.replace('.', '_') : cleaned);
        }
        String result = path.toString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Cannot derive a resource path from '" + value + "'");
        }
        return result;
    }

    public static String normalizeConnectorIdentifier(String value) {
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

    private static IllegalArgumentException invalid(String value, String role) {
        return new IllegalArgumentException("Invalid " + role + " '" + value + "'");
    }
}
