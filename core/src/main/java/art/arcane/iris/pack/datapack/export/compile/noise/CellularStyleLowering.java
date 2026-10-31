package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

public interface CellularStyleLowering {
    Field lower(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities);
}
