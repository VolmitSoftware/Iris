package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("One size class of flowing water. Its width grows with the square root of the catchment, and it chooses the channel's banks, steps and materials.")
@Data
public class IrisHydrologyTier {
    @Required
    @Description("Label for this tier in configuration error messages. Locate, maps and history do not use it: they rank tiers by minCatchment, so the lowest tier is a stream, the highest a major river and any between are rivers.")
    private String name = "stream";

    @MinNumber(0)
    @MaxNumber(1073741824)
    @Description("Upstream catchment in square blocks a channel needs to belong to this tier.")
    private double minCatchment = 1500D;

    @ArrayType(type = Double.class, min = 2)
    @Description("Smallest and largest half-width of the water surface in blocks, written as [min, max]. The largest is at most 64.")
    private KList<Double> halfWidth = new KList<>(0.5D, 2D);

    @MinNumber(0)
    @MaxNumber(16)
    @Description("How fast the half-width grows from its smallest value, per unit of the square root of catchment divided by 2500.")
    private double widthFactor = 1D;

    @MinNumber(0.05)
    @MaxNumber(2)
    @Description("Water depth at the middle of the channel as a fraction of the half-width. Every channel is at least one block deep.")
    private double depthRatio = 0.4D;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Blocks the water surface sits below the ground it flows through.")
    private int inset = 1;

    @Description("Keeps drops shorter than a waterfall as stepped cascades. When false, drops are spread into a gentler slope where the terrain allows; drops that cannot be spread stay as small cascades below the waterfall minimum.")
    private boolean cascades = true;

    @MinNumber(2)
    @MaxNumber(64)
    @Description("Smallest drop in blocks that becomes a waterfall.")
    private int waterfallMinimumDrop = 6;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Width in blocks of the band beside the water where the ground is reshaped into banks. 0 leaves the ground beside the channel as it is.")
    private int bankBand = 0;

    @Description("Shape of the banks inside the bank band.")
    private IrisBankStyle bankStyle = IrisBankStyle.SMOOTH;

    @Description("Spreads the channel into a delta where it reaches the sea.")
    private boolean deltas = false;

    @Description("Blocks painted on the bed, shore and banks of this tier.")
    private IrisHydrologyMaterials materials = new IrisHydrologyMaterials();

    @ArrayType(type = IrisDecorator.class)
    @Description("Plants for this tier's channels, written like biome decorators. partOf chooses where each one grows: SEA_FLOOR on the bed under water (seagrass, kelp, coral; never taller than the water depth minus one, and only where the water is at least two blocks deep), SEA_SURFACE on still water (lily pads; never on falls, cascades or flowing water) and SHORE_LINE on the dry banks and shore beside the water (reeds, sugar cane, ferns). Nothing grows in lava. Empty by default.")
    private KList<IrisDecorator> decorators = new KList<>();

    @Required
    @Description("Name of the fluid profile that fills this tier's channels.")
    private String fluid = "water";
}
