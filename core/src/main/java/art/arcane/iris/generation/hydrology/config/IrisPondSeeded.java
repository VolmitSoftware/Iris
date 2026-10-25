package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Ponds cut into the terrain as bowls. On a slope the bowl spills downhill, so the pond sits perched above the ground below it.")
@Data
public class IrisPondSeeded {
    @Description("Cuts seeded cups into the terrain.")
    private boolean enabled = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected cups per 512 by 512 block window, before the area's pond density multiplier.")
    private double density = 4D;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Cup radius in blocks, written as [min, max].")
    private KList<Integer> radius = new KList<>(6, 24);

    @ArrayType(type = Integer.class, min = 2)
    @Description("Cup depth in blocks, written as [min, max].")
    private KList<Integer> depth = new KList<>(3, 8);

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Steepest ground slope, as rise over run, that accepts a cup.")
    private double maxSlope = 0.6D;

    @Description("Places chains of cups along streams, each one spilling into the next one downstream at a lower level.")
    private boolean alongChannels = true;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Distance in blocks between chained cups along a stream, written as [min, max].")
    private KList<Integer> channelSpacing = new KList<>(96, 256);
}
