package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;

import java.util.SortedMap;

public interface WorldgenJsonWriter {
    SortedMap<String, byte[]> write(ExportIr ir);

    static WorldgenJsonWriter forTarget(DataVersion target) {
        return switch (target) {
            case V26_3 -> new Format26_3Writer();
            default -> throw WorldgenExportTargets.unsupported(target.getVersion());
        };
    }
}
