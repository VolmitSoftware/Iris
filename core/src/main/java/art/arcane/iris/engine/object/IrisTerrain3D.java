package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Snippet;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("terrain-3d")
@Accessors(chain = true)
@NoArgsConstructor
@Data
@Desc("Volumetric surface terrain that adds projecting rock and removes undercuts and fissures around the biome's height generators.")
public class IrisTerrain3D {
    @Desc("Enable this biome's volumetric terrain profile.")
    private boolean enabled = true;

    @Desc("Seed offset for the surface density and fissure fields.")
    private long seed = 0;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Maximum positive or negative surface density displacement in blocks.")
    private double amplitude = 32;

    @MinNumber(8)
    @MaxNumber(4096)
    @Desc("Horizontal feature size in blocks for a density style with its default zoom.")
    private double horizontalScale = 96;

    @MinNumber(8)
    @MaxNumber(4096)
    @Desc("Vertical feature size in blocks. Shorter scales relative to amplitude permit overlapping ledges and overhangs.")
    private double verticalScale = 24;

    @Desc("Signed three-dimensional noise that displaces terrain density. Style zoom multiplies the configured feature sizes.")
    private IrisGeneratorStyle densityStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Maximum additional fissure depth in blocks. Zero disables fissures.")
    private double crackDepth = 0;

    @MinNumber(0.25)
    @MaxNumber(64)
    @Desc("Approximate fissure half-width in blocks around the fissure field's zero crossings.")
    private double crackWidth = 4;

    @MinNumber(8)
    @MaxNumber(4096)
    @Desc("Horizontal fissure feature size in blocks. Fissures use four times this vertical scale.")
    private double crackScale = 96;

    @Desc("Noise whose zero crossings define tall narrow fissures.")
    private IrisGeneratorStyle crackStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Minimum original terrain slope, measured as rise divided by horizontal distance, before shaping starts. Zero disables slope gating.")
    private double minimumSlope = 0.15;

    @MinNumber(0.001)
    @MaxNumber(16)
    @Desc("Slope interval above minimumSlope over which shaping reaches full strength.")
    private double slopeFade = 0.35;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Height above the dimension fluid level below which terrain remains solid and unchanged.")
    private double fluidClearance = 8;

    @MinNumber(1)
    @MaxNumber(128)
    @Desc("Vertical distance over which shaping grows from zero above the fluid clearance.")
    private double fluidFade = 24;

    public void validate() {
        requireRange("amplitude", amplitude, 0D, 128D);
        requireRange("horizontalScale", horizontalScale, 8D, 4096D);
        requireRange("verticalScale", verticalScale, 8D, 4096D);
        requireRange("crackDepth", crackDepth, 0D, 128D);
        requireRange("crackWidth", crackWidth, 0.25D, 64D);
        requireRange("crackScale", crackScale, 8D, 4096D);
        requireRange("minimumSlope", minimumSlope, 0D, 16D);
        requireRange("slopeFade", slopeFade, 0.001D, 16D);
        requireRange("fluidClearance", fluidClearance, 0D, 128D);
        requireRange("fluidFade", fluidFade, 1D, 128D);
        if (densityStyle == null || crackStyle == null) {
            throw new IllegalArgumentException("terrain3D densityStyle and crackStyle must be style objects or snippets");
        }
        validateStyle(densityStyle, "densityStyle", 0);
        validateStyle(crackStyle, "crackStyle", 0);
    }

    private static void validateStyle(IrisGeneratorStyle style, String path, int depth) {
        if (depth > 32 || style.getStyle() == null) {
            throw new IllegalArgumentException("terrain3D." + path + " must use a known style with at most 32 nested fractures");
        }
        requireRange(path + ".zoom", style.getZoom(), 0.00001D, Double.MAX_VALUE);
        requireRange(path + ".cellularFrequency", style.getCellularFrequency(), 0D, Double.MAX_VALUE);
        requireRange(path + ".cellularZoom", style.getCellularZoom(), 0.00001D, Double.MAX_VALUE);
        requireRange(path + ".multiplier", style.getMultiplier(), 0.00001D, Double.MAX_VALUE);
        requireRange(path + ".exponent", style.getExponent(), 0.01562D, 64D);
        requireRange(path + ".cacheSize", style.getCacheSize(), 0D, 8192D);
        if (style.getFracture() != null) {
            validateStyle(style.getFracture(), path + ".fracture", depth + 1);
        }
    }

    private static void requireRange(String name, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("terrain3D." + name + " must be finite and between "
                    + minimum + " and " + maximum);
        }
    }
}
