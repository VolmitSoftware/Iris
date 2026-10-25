package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Ponds that fill hollows already present in the terrain, up to just below the point where they would spill.")
@Data
public class IrisPondNatural {
    @Description("Fills natural hollows with water.")
    private boolean enabled = true;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Share of qualifying hollows that become ponds, before the area's pond density multiplier.")
    private double keep = 1D;

    @MinNumber(16)
    @MaxNumber(16777216)
    @Description("Smallest pond surface in square blocks.")
    private int minArea = 256;

    @MinNumber(16)
    @MaxNumber(16777216)
    @Description("Largest pond surface in square blocks. Larger hollows become lakes or are drained.")
    private int maxArea = 65536;

    @MinNumber(0.5)
    @MaxNumber(64)
    @Description("Smallest hollow depth in blocks that holds a pond.")
    private double minDepth = 2D;
}
