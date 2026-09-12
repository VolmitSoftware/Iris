package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls the wet channel of a surface river.")
@Data
public class IrisSurfaceRiverChannelConfig {
    @Description("Wet channel width in blocks.")
    private IrisStyledRange width = range(4D, 8D, 1024D);

    @Description("Wet bed depth in blocks at the channel center.")
    private IrisStyledRange depth = range(2D, 4D, 768D);

    @MinNumber(0)
    @MaxNumber(3)
    @Description("Blocks the water surface sinks below the lowest natural ground beside the channel; 0 keeps the water flush with the bank, and the bank always meets the water at its own height.")
    private int sink = 0;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Maximum cut below natural terrain at the channel center before a course is rejected.")
    private int maximumIncision = 10;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent variation of the wet outline and bed as a fraction of the channel size.")
    private double roughness = 0.25D;

    @MinNumber(4)
    @MaxNumber(64)
    @Description("Wavelength in blocks of the outline and bed variation.")
    private int roughnessWavelength = 16;

    @MinNumber(1)
    @MaxNumber(4)
    @Description("Width of the spring pool at the headwater relative to the channel width; 1 starts the river at its normal width.")
    private double springWidthRatio = 2.5D;

    @MinNumber(4)
    @MaxNumber(96)
    @Description("Blocks over which the spring pool narrows back to the channel width.")
    private int springLength = 24;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Stations along the course the sampled width and depth are averaged over, so the channel changes size gradually; 0 follows the sampled values exactly at every station.")
    private int smoothingRadius = 16;

    @MinNumber(0.2)
    @MaxNumber(1)
    @Description("Narrowest the roughened waterline may pinch, as a fraction of the channel half-width; 1 stops the outline from ever narrowing below the nominal width.")
    private double outlineMinimumRatio = 0.6D;

    @MinNumber(1)
    @MaxNumber(3)
    @Description("Widest the roughened waterline may bulge, as a fraction of the channel half-width; 1 stops the outline from ever widening beyond the nominal width.")
    private double outlineMaximumRatio = 1.4D;

    @MinNumber(0)
    @MaxNumber(8)
    @Description("Extra bed depth in blocks at the headwater spring, fading to nothing over springLength; 0 keeps the spring pool as deep as the channel.")
    private double springExtraDepth = 1D;

    private static IrisStyledRange range(double min, double max, double zoom) {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.IRIS).zoomed(zoom);
        return new IrisStyledRange(min, max, style);
    }
}
