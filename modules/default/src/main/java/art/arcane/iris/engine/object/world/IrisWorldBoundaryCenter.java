package art.arcane.iris.engine.object.world;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("The world-border center in block coordinates")
@Data
public class IrisWorldBoundaryCenter {
    @MinNumber(-29_999_984)
    @MaxNumber(29_999_984)
    @Desc("The world-border center X coordinate")
    private double x = 0D;

    @MinNumber(-29_999_984)
    @MaxNumber(29_999_984)
    @Desc("The world-border center Z coordinate")
    private double z = 0D;

    public IrisWorldBoundaryCenter(double x, double z) {
        this.x = x;
        this.z = z;
    }
}
