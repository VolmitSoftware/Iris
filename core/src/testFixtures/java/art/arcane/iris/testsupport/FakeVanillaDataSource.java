package art.arcane.iris.testsupport;

import art.arcane.iris.spi.VanillaDataSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class FakeVanillaDataSource implements VanillaDataSource {
    private final TreeMap<String, byte[]> files;

    private FakeVanillaDataSource(TreeMap<String, byte[]> files) {
        this.files = files;
    }

    public static FakeVanillaDataSource of(Map<String, String> files) {
        TreeMap<String, byte[]> encoded = new TreeMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            requireDataPath(file.getKey());
            encoded.put(file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return new FakeVanillaDataSource(encoded);
    }

    @Override
    public Optional<byte[]> read(String dataPath) {
        requireDataPath(dataPath);
        byte[] content = files.get(dataPath);
        return content == null ? Optional.empty() : Optional.of(content.clone());
    }

    @Override
    public List<String> list(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String directory = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        requireDataPath(directory);
        String beneath = directory + "/";
        List<String> paths = new ArrayList<>();
        for (String path : files.tailMap(beneath, true).keySet()) {
            if (!path.startsWith(beneath)) {
                break;
            }
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    private static void requireDataPath(String dataPath) {
        Objects.requireNonNull(dataPath, "dataPath");
        String[] parts = dataPath.split("/", -1);
        if (parts.length < 2 || !"data".equals(parts[0]) || dataPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Vanilla data paths have the form data/<namespace>/<path>: " + dataPath);
        }
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Vanilla data path has an empty or relative segment: " + dataPath);
            }
        }
    }
}
