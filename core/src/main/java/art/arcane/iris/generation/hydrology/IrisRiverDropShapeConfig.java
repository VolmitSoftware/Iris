package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls low-volume cascades, falling faces, and organic receiving basins.")
@Data
public class IrisRiverDropShapeConfig {
    @MinNumber(1)
    @MaxNumber(16)
    @Description("Preferred horizontal cascade run per block of head loss.")
    private int cascadeRunPerBlock = 2;

    @MinNumber(0.25)
    @MaxNumber(6)
    @Description("Exponent of the graded cascade profile. Values above one accelerate toward the receiver.")
    private double cascadeExponent = 1.4D;

    @MinNumber(1)
    @MaxNumber(4)
    @Description("Maximum head loss between adjacent underground drop faces. Exposed cascades always use one-block steps.")
    private int maximumCascadeStep = 2;

    @MinNumber(0.25)
    @MaxNumber(1)
    @Description("Drop-flow width as a fraction of the connected channel width.")
    private double flowWidthRatio = 0.45D;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Maximum wetted depth along a descending flow path.")
    private int maximumFlowDepth = 2;

    @MinNumber(1)
    @MaxNumber(4)
    @Description("Receiving-basin width as a fraction of the descending flow width.")
    private double basinWidthRatio = 1.8D;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Maximum receiving-basin depth after drop-scaled erosion.")
    private int maximumBasinDepth = 8;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Horizontal run in blocks an underground drop spreads over per block of head loss, so tall drops become long cascades; 0 keeps every underground drop at its shortest run.")
    private int undergroundCascadeRunPerBlock = 0;
}
