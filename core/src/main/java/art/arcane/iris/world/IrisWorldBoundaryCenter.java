package art.arcane.iris.world;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("The world-border center in block coordinates")
@Data
public class IrisWorldBoundaryCenter {
    @MinNumber(-29_999_984)
    @MaxNumber(29_999_984)
    @Description("The world-border center X coordinate")
    private double x = 0D;

    @MinNumber(-29_999_984)
    @MaxNumber(29_999_984)
    @Description("The world-border center Z coordinate")
    private double z = 0D;

    public IrisWorldBoundaryCenter(double x, double z) {
        this.x = x;
        this.z = z;
    }
}
