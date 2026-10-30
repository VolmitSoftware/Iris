package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;

import java.util.Objects;

public record ExportCapabilities(DataVersion target) {
    public ExportCapabilities {
        Objects.requireNonNull(target);
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
    }

    public static ExportCapabilities forTarget(DataVersion target) {
        return new ExportCapabilities(target);
    }
}
