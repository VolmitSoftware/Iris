package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls coastal grottos: sea-level chambers whose only opening is their ocean face, admitted either as a river outlet or as a standalone sea cave.")
@Data
public class IrisCoastalRiverGrottoConfig {
    @Description("Allow a coastal grotto to serve as an explicit accepted outlet.")
    private boolean enabled = true;

    @Description("Hydraulic level assigned to the contained coastal grotto pool.")
    private IrisGrottoPoolLevel poolLevel = IrisGrottoPoolLevel.SEA_LEVEL;

    @MinNumber(1)
    @MaxNumber(128)
    @Description("Maximum horizontal radius in blocks of an accepted coastal grotto.")
    private int horizontalRadius = 12;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Maximum vertical radius in blocks of an accepted coastal grotto.")
    private int verticalRadius = 7;

    @MinNumber(1)
    @MaxNumber(63)
    @Description("Required dry clearance above a coastal grotto pool.")
    private int headroom = 10;

    @MinNumber(1)
    @MaxNumber(1048576)
    @Description("Maximum accepted coastal grotto volume in blocks.")
    private int maximumVolume = 8192;

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Blocks the coast must stand above the sea at the outlet for a river to end in a coastal grotto instead of an open mouth. Null uses the larger of 4 and verticalRadius.")
    private Integer cliffMinimumHeight = null;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Share of cliffMinimumHeight the coast's slope must reach at the outlet before a grotto is chosen over an open mouth; 0 ignores the slope and decides on height alone.")
    private double cliffSlopeFactor = 0.5D;

    @Description("Standalone sea caves: coastal grottos opening from the ocean into the coast without a river.")
    private IrisSeaCaveConfig seaCaves = new IrisSeaCaveConfig();
}
