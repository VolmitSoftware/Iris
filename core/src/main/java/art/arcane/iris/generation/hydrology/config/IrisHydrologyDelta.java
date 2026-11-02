package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Where a channel's estuary crosses low coastal ground it can split into a delta: extra channels at sea level that branch from the estuary and fan out to the sea on both sides of it.")
@Data
public class IrisHydrologyDelta {
    @MinNumber(0)
    @MaxNumber(4)
    @Description("Channels that branch from each estuary of this tier: 0 for no delta, or 2 to 4. Each has the half-width of the channel where its estuary starts divided by the square root of this count. A delta channel is left out where it would cross ground more than deformation.maxFill blocks above sea level, a pond or lake, or another channel, or where it does not reach the sea within length blocks; sea inlets keep clear of delta channels.")
    private int channels = 0;

    @MinNumber(10)
    @MaxNumber(80)
    @Description("Largest angle in degrees between a delta channel and its estuary. The channels share the angles up to this value on both sides of the estuary, each turned a little at random, and bend gently back toward the sea.")
    private double spreadDegrees = 35D;

    @MinNumber(8)
    @MaxNumber(384)
    @Description("Most blocks a delta channel runs from its estuary before it reaches the sea. This length, plus the tier's halfWidth max, plus bankBand, plus 3 must not exceed 512.")
    private int length = 64;
}
