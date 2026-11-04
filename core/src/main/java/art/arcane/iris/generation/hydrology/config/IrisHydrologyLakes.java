package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Large lakes that fill the biggest hollows in the terrain. Each lake has one water level across its whole surface. A hollow becomes a lake only when it is at least ponds.natural.minDepth deep and no more than 2048 blocks across. Lakes take their freeboard, dryDrawdown, shore roundness, materials, decorators and fluid from ponds.")
@Data
public class IrisHydrologyLakes {
    @Description("Turns large hollows into lakes.")
    private boolean enabled = true;

    @MinNumber(1024)
    @MaxNumber(4194304)
    @Description("Smallest lake surface in square blocks.")
    private int minArea = 65536;

    @MinNumber(1024)
    @MaxNumber(4194304)
    @Description("Largest lake surface in square blocks. Larger hollows are drained by a cut through their rim.")
    private int maxArea = 4194304;
}
