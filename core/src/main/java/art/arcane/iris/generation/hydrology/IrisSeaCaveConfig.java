package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Sea caves: coastal grottos that open from the ocean into the coast without a river. The chamber size, headroom and volume cap are the coastal grotto's.")
@Data
public class IrisSeaCaveConfig {
    @Description("Plan sea caves along the coast. Requires the coastal grotto to be enabled.")
    private boolean enabled = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Sea caves accepted per planning tile; the steepest owned coast is taken first.")
    private int maximumPerTile = 3;

    @MinNumber(16)
    @MaxNumber(8192)
    @Description("Least distance in blocks between two sea caves. Must be at least twice the coastal grotto horizontalRadius.")
    private int minimumSpacing = 160;

    @MinNumber(1)
    @MaxNumber(128)
    @Description("The coast must stand this many blocks above the sea both at the shoreline and at the back of the chamber.")
    private int minimumCoastHeight = 8;

    @MinNumber(0)
    @MaxNumber(128)
    @Description("How far inland from the shoreline the chamber is swept. 0 leaves a single chamber at the shore.")
    private int depth = 12;

    @MinNumber(0)
    @MaxNumber(90)
    @Description("Largest angle in degrees the inland sweep may turn away from straight inland, chosen per cave; 0 sweeps every sea cave straight in from the shore.")
    private double sweepJitterDegrees = 25D;
}
