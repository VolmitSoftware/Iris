package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.JsonElement;

public interface DensityFunctionEmitter {
    JsonElement emit(Field field, FunctionTable table);

    static DensityFunctionEmitter forTarget(ExportCapabilities capabilities, String namespace, VanillaBounds bounds) {
        return switch (capabilities.target()) {
            case V26_3 -> new DensityFunctionEmitter26_3(namespace, bounds);
            default -> throw WorldgenExportTargets.unsupported(capabilities.target().getVersion());
        };
    }
}
