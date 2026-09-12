package art.arcane.iris.world.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TransientWorldCleanupSupport {
    private static final Pattern TRANSIENT_STUDIO_WORLD_PATTERN = Pattern.compile("^iris-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    private TransientWorldCleanupSupport() {
    }

    public static boolean isTransientStudioWorldName(String worldName) {
        return transientStudioBaseWorldName(worldName) != null;
    }

    public static String transientStudioBaseWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        String candidate = worldName.trim();
        if (candidate.endsWith("_nether")) {
            candidate = candidate.substring(0, candidate.length() - "_nether".length());
        } else if (candidate.endsWith("_the_end")) {
            candidate = candidate.substring(0, candidate.length() - "_the_end".length());
        }

        if (!TRANSIENT_STUDIO_WORLD_PATTERN.matcher(candidate).matches()) {
            return null;
        }

        return candidate;
    }

    public static List<String> worldFamilyNames(String worldName) {
        ArrayList<String> names = new ArrayList<>();
        String normalized = normalizeWorldName(worldName);
        if (normalized == null) {
            return names;
        }

        names.add(normalized);
        names.add(normalized + "_nether");
        names.add(normalized + "_the_end");
        return names;
    }

    public static LinkedHashSet<String> collectTransientStudioWorldNames(File levelRoot) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (levelRoot == null) {
            return names;
        }

        File namespacesRoot = new File(levelRoot, "dimensions");
        File[] namespaces = namespacesRoot.listFiles(File::isDirectory);
        if (namespaces == null) {
            return names;
        }

        for (File namespace : namespaces) {
            collectTransientStudioWorldNames(namespace, names);
        }
        return names;
    }

    private static void collectTransientStudioWorldNames(File folder, LinkedHashSet<String> names) {
        File[] children = folder.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }

        for (File child : children) {
            String baseName = transientStudioBaseWorldName(child.getName());
            if (baseName != null) {
                names.add(baseName);
                continue;
            }
            collectTransientStudioWorldNames(child, names);
        }
    }

    private static String normalizeWorldName(String worldName) {
        if (worldName == null) {
            return null;
        }

        String normalized = worldName.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.toLowerCase(Locale.ROOT).equals(normalized) ? normalized : normalized;
    }
}
