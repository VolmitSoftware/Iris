package art.arcane.iris.pack.datapack.export;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DatapackExportDestinations {
    private static final Pattern OUTPUT_NAME = Pattern.compile("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,127}");

    private static final Set<Path> CLAIMED = ConcurrentHashMap.newKeySet();

    private DatapackExportDestinations() {
    }

    public static DatapackExportFormat parseFormat(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "zip" -> DatapackExportFormat.ZIP;
            case "directory", "dir", "folder" -> DatapackExportFormat.DIRECTORY;
            default -> throw new IllegalArgumentException("Export format must be directory or zip.");
        };
    }

    public static Path resolve(Path exportRoot, String output, DatapackExportFormat format) {
        Path root = Objects.requireNonNull(exportRoot, "Datapack export root").toAbsolutePath().normalize();
        String requested = Objects.requireNonNull(output, "Datapack export output name");
        String name = requested.trim();
        if (!name.equals(requested) || !OUTPUT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Export output must be one file name using letters, numbers, '.', '_', or '-'.");
        }
        String fileName = format == DatapackExportFormat.ZIP && !name.endsWith(".zip") ? name + ".zip" : name;
        Path destination = root.resolve(fileName).normalize();
        if (destination.equals(root) || !root.equals(destination.getParent())) {
            throw new IllegalArgumentException("Export output must be one artifact inside the exports folder.");
        }
        return destination;
    }

    public static boolean claim(Path destination) {
        return CLAIMED.add(normalized(destination));
    }

    public static void release(Path destination) {
        CLAIMED.remove(normalized(destination));
    }

    private static Path normalized(Path destination) {
        return Objects.requireNonNull(destination, "Datapack export destination").toAbsolutePath().normalize();
    }
}
