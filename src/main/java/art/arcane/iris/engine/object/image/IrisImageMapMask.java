package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("A composable reference to a named MASK image map")
@Data
public class IrisImageMapMask {
    @Desc("The key of a named imageMaps entry whose application is MASK")
    private String map = "";

    @Desc("How this mask combines with masks before it")
    private IrisImageMapMaskOperation operation = IrisImageMapMaskOperation.MULTIPLY;

    @Desc("Invert this mask before combining it")
    private boolean inverted = false;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Values below this threshold become zero")
    private double threshold = 0D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Soft transition width above threshold; zero is a hard edge")
    private double falloff = 0D;
}
