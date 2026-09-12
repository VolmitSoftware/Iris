package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Independent deep-fluid configuration outside the surface and underground river source budgets.")
@Data
public class IrisDeepFluidConfig {
    @Required
    @Description("Deep-fluid system identifier.")
    private String id = "deep_lava";

    @Required
    @Description("Fluid palette used by accepted deep pools and short channels.")
    private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("lava");

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected independent deep-fluid sources per hydrology tile.")
    private double density = 0.125D;

    @MinNumber(16)
    @MaxNumber(8192)
    @Description("Nominal spacing in blocks between independent deep-fluid source sites.")
    private int spacing = 768;

    @Description("Deep-fluid elevation in world Y.")
    private IrisStyledRange height = new IrisStyledRange(
            -192D,
            32D,
            new IrisGeneratorStyle(NoiseStyle.IRIS).zoomed(1024D)
    );

    @MinNumber(2)
    @MaxNumber(128)
    @Description("Maximum horizontal radius in blocks of an accepted deep-fluid body.")
    private int horizontalRadius = 14;

    @MinNumber(2)
    @MaxNumber(64)
    @Description("Maximum vertical radius in blocks of an accepted deep-fluid body.")
    private int verticalRadius = 6;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Wet width in blocks of an accepted short deep-fluid channel.")
    private int channelWidth = 3;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Wet depth in blocks of an accepted deep-fluid body.")
    private int depth = 1;

    @MinNumber(1)
    @MaxNumber(63)
    @Description("Required dry clearance above an accepted deep-fluid body.")
    private int headroom = 6;

    @Description("Allow accepted sources to form fully contained pools.")
    private boolean containedPools = true;

    @Description("Allow accepted sources to form short contained channels.")
    private boolean shortChannels = false;
}
