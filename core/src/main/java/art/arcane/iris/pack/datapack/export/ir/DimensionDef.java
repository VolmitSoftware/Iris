package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import com.google.gson.JsonObject;

import java.util.List;

public record DimensionDef(
        String name,
        int minY,
        int height,
        int logicalHeight,
        int fluidHeight,
        String defaultBlock,
        String defaultFluid,
        JsonObject dimensionType
) {
    private static final int SECTION = 16;
    private static final int LOWEST_Y = -2032;
    private static final int HIGHEST_TOP = 2032;
    private static final int MAX_HEIGHT = 4064;

    public DimensionDef {
        VanillaResourceIdentifier.requirePath(name, "dimension name");
        if (minY % SECTION != 0 || minY < LOWEST_Y) {
            throw new IllegalArgumentException("Dimension '" + name + "' min_y must be a multiple of 16 no lower than -2032, got " + minY);
        }
        if (height % SECTION != 0 || height < SECTION || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Dimension '" + name + "' height must be a multiple of 16 within [16, 4064], got " + height);
        }
        if (minY + height > HIGHEST_TOP) {
            throw new IllegalArgumentException("Dimension '" + name + "' min_y + height must be at most 2032, got " + (minY + height));
        }
        if (logicalHeight < 0 || logicalHeight > height) {
            throw new IllegalArgumentException("Dimension '" + name + "' logical height must be within [0, " + height + "], got " + logicalHeight);
        }
        VanillaResourceIdentifier.requireIdentifier(defaultBlock, "default block");
        VanillaResourceIdentifier.requireIdentifier(defaultFluid, "default fluid");
        dimensionType = dimensionType.deepCopy();
    }

    public static DimensionDef aligned(
            String name,
            int minY,
            int height,
            int logicalHeight,
            int fluidHeight,
            String defaultBlock,
            String defaultFluid,
            JsonObject dimensionType,
            List<ExportDiagnostic> diagnostics
    ) {
        int alignedMinY = Math.clamp((long) Math.floorDiv(minY, SECTION) * SECTION, LOWEST_Y, HIGHEST_TOP - SECTION);
        long requestedTop = (long) minY + Math.max(height, 0);
        long alignedTop = Math.min(Math.ceilDiv(requestedTop, SECTION) * SECTION, HIGHEST_TOP);
        int alignedHeight = Math.clamp(alignedTop - alignedMinY, SECTION, Math.min(MAX_HEIGHT, HIGHEST_TOP - alignedMinY));
        int alignedLogicalHeight = logicalHeight >= height ? alignedHeight : Math.clamp(logicalHeight, 0, alignedHeight);
        if (alignedMinY != minY || alignedHeight != height || alignedLogicalHeight != logicalHeight) {
            diagnostics.add(new ExportDiagnostic(
                    ExportDiagnostic.Severity.WARNING,
                    ExportLimit.DIMENSION_HEIGHT,
                    name,
                    "Dimension min_y " + minY + " height " + height + " logical height " + logicalHeight
                            + " exports as min_y " + alignedMinY + " height " + alignedHeight + " logical height " + alignedLogicalHeight
                            + "; min_y and height are multiples of 16, height is at most 4064 and min_y + height is at most 2032"
            ));
        }
        return new DimensionDef(name, alignedMinY, alignedHeight, alignedLogicalHeight, fluidHeight, defaultBlock, defaultFluid, dimensionType);
    }

    public static int seaLevel(int fluidHeight) {
        return fluidHeight + 1;
    }

    public int seaLevel() {
        return seaLevel(fluidHeight);
    }
}
