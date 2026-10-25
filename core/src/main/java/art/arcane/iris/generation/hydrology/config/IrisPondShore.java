package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Shape of pond and lake shores and floors.")
@Data
public class IrisPondShore {
    @MinNumber(0)
    @MaxNumber(4)
    @Description("Strength of the noise that rounds and roughens pond outlines. 0 follows the terrain exactly.")
    private double roundness = 0.5D;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Width in blocks of the shallow shelf along the shore before the floor reaches full depth.")
    private int shelf = 6;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Width in blocks of the ground outside the shore that slopes down to the water.")
    private int taper = 14;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Deepest pond floor below the water surface, in blocks.")
    private int maxDepth = 8;
}
