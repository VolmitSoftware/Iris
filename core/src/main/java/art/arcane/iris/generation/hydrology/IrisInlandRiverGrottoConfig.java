package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls contained inland grotto outlets.")
@Data
public class IrisInlandRiverGrottoConfig {
    @Description("Allow a contained inland grotto to serve as an explicit accepted outlet.")
    private boolean enabled = true;

    @Description("Continue eligible surface rivers through a falling sinkhole into the contained inland grotto.")
    private boolean connectSurfaceRivers = false;

    @MinNumber(1)
    @MaxNumber(128)
    @Description("Maximum horizontal radius in blocks of an accepted inland grotto.")
    private int horizontalRadius = 10;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Maximum vertical radius in blocks of an accepted inland grotto.")
    private int verticalRadius = 6;

    @MinNumber(1)
    @MaxNumber(63)
    @Description("Required dry clearance above an inland grotto pool.")
    private int headroom = 10;

    @MinNumber(1)
    @MaxNumber(1048576)
    @Description("Maximum accepted inland grotto volume in blocks.")
    private int maximumVolume = 8192;
}
