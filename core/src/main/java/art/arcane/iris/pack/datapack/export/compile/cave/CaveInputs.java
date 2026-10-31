package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.List;
import java.util.Objects;

public record CaveInputs(
        String resource,
        int minY,
        int maxY,
        int fluidHeight,
        int lavaY,
        Field surface,
        Field aquiferSurface,
        NoiseRegistry noises,
        ExportCapabilities capabilities,
        List<ExportDiagnostic> diagnostics
) {
    public CaveInputs {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(surface);
        Objects.requireNonNull(aquiferSurface);
        Objects.requireNonNull(noises);
        Objects.requireNonNull(capabilities);
        Objects.requireNonNull(diagnostics);
        if (maxY <= minY) {
            throw new IllegalArgumentException("Cave bounds need maxY above minY, got " + minY + ".." + maxY);
        }
        FieldValidator.requireTwoDimensional(surface, "cave surface");
        FieldValidator.requireTwoDimensional(aquiferSurface, "aquifer surface");
    }

    public static CaveInputs of(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        int minY = dimension.getMinHeight();
        return new CaveInputs(
                "dimensions/" + dimension.getLoadKey(),
                minY,
                dimension.getMaxHeight() - 1,
                context.dimensionDef().fluidHeight(),
                minY + dimension.getCaveLavaHeight(),
                context.height(),
                context.surfaceEstimate(),
                context.noises(),
                context.capabilities(),
                context.diagnostics());
    }

    public Field blockY() {
        return new Field.Coord(Axis.Y, Field.Tiling.CLAMP, minY, maxY + 1, minY, maxY + 1);
    }
}
