package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls how a surface river meets the sea.")
@Data
public class IrisRiverMouthConfig {
    @MinNumber(1)
    @MaxNumber(4)
    @Description("Width multiplier reached at the coastline over the final stretch of the river.")
    private double flareRatio = 1.6D;

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Maximum number of blocks the recorded mouth footprint may enter the ocean before terminating.")
    private int maximumOceanApron = 8;

    @MinNumber(0)
    @MaxNumber(256)
    @Description("Blocks of river before the coast held at sea level as a drowned inlet, widened toward flareRatio over that reach. Zero ends the river at the shoreline with no inlet.")
    private int inletLength = 64;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Extra bed depth reached at the shoreline over the inlet, so the estuary is deeper than the river above it.")
    private int inletDepth = 3;

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Deepest cut allowed in the inlet and its approach ramp, replacing channel.maximumIncision there, so the coast may be cut down to sea level through a rise of this height.")
    private int maximumIncision = 32;

    @MinNumber(0.05)
    @MaxNumber(1)
    @Description("Largest share of the river's exposed course the drowned inlet may occupy, so a short river keeps most of its length above sea level; the inlet is the shorter of inletLength and this share.")
    private double inletCourseFraction = 0.5D;

    @MinNumber(0.1)
    @MaxNumber(4)
    @Description("Blocks of head gained per station on the approach ramp climbing from the inlet back to the river's natural profile; 1 climbs one block per station, higher values grade the approach faster.")
    private double inletRampSlope = 1D;
}
