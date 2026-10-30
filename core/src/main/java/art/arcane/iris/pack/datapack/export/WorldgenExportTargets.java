package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.DataVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class WorldgenExportTargets {
    private static final DataVersion OLDEST = DataVersion.V26_3;
    public static final List<DataVersion> SUPPORTED = supported();

    private WorldgenExportTargets() {
    }

    public static DataVersion resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUPPORTED.getLast();
        }
        DataVersion version = DataVersion.forMinecraftVersion(raw.trim());
        if (!SUPPORTED.contains(version)) {
            throw unsupported(raw);
        }
        return version;
    }

    public static IllegalArgumentException unsupported(String raw) {
        StringJoiner supported = new StringJoiner(", ");
        for (DataVersion version : SUPPORTED) {
            supported.add(version.getVersion());
        }
        return new IllegalArgumentException("Unsupported datapack export version '" + raw + "'. Supported: " + supported);
    }

    private static List<DataVersion> supported() {
        List<DataVersion> versions = new ArrayList<>();
        for (DataVersion version : DataVersion.values()) {
            if (version.ordinal() >= OLDEST.ordinal()) {
                versions.add(version);
            }
        }
        return List.copyOf(versions);
    }
}
