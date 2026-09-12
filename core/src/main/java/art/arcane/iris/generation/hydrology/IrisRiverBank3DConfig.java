package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Three-dimensional rock shelves retained above the approved riverbank cut.")
@Data
public class IrisRiverBank3DConfig {
    @Description("Retain connected volumetric shelves in eroded riverbanks.")
    private boolean enabled = true;

    @Description("Seed offset for the riverbank density field.")
    private long seed = 0;

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Maximum shelf height above the approved bank surface. Does not deepen the river cut.")
    private double amplitude = 8;

    @MinNumber(4)
    @MaxNumber(4096)
    @Description("Horizontal density feature size in blocks at the style's default zoom.")
    private double horizontalScale = 48;

    @MinNumber(4)
    @MaxNumber(256)
    @Description("Vertical density feature size in blocks. Short scales form separate rock shelves.")
    private double verticalScale = 8;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Maximum horizontal distance in blocks from an overhang to a solid supporting bank.")
    private int maximumOverhang = 8;

    @Description("Signed XYZ density controlling retained bank rock. Supports generator styles and fractures.")
    private IrisGeneratorStyle densityStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

    public void validate() {
        requireRange("amplitude", amplitude, 0D, 32D);
        requireRange("horizontalScale", horizontalScale, 4D, 4096D);
        requireRange("verticalScale", verticalScale, 4D, 256D);
        requireRange("maximumOverhang", maximumOverhang, 1D, 16D);
        if (densityStyle == null) {
            throw new IllegalArgumentException("geometry.banks3D.densityStyle must be a generator style.");
        }
        validateStyle(densityStyle, 0);
    }

    private static void validateStyle(IrisGeneratorStyle style, int depth) {
        if (depth > 32 || style.getStyle() == null) {
            throw new IllegalArgumentException("geometry.banks3D.densityStyle must use known styles with at most 32 nested fractures.");
        }
        requireRange("densityStyle.zoom", style.getZoom(), 0.00001D, Double.MAX_VALUE);
        requireRange("densityStyle.cellularFrequency", style.getCellularFrequency(), 0D, Double.MAX_VALUE);
        requireRange("densityStyle.cellularZoom", style.getCellularZoom(), 0.00001D, Double.MAX_VALUE);
        requireRange("densityStyle.multiplier", style.getMultiplier(), 0.00001D, Double.MAX_VALUE);
        requireRange("densityStyle.exponent", style.getExponent(), 0.01562D, 64D);
        requireRange("densityStyle.cacheSize", style.getCacheSize(), 0D, 8192D);
        if (style.getFracture() != null) {
            validateStyle(style.getFracture(), depth + 1);
        }
    }

    private static void requireRange(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("geometry.banks3D." + field + " must be finite and between "
                    + minimum + " and " + maximum + ".");
        }
    }
}
