package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A composable reference to a named MASK image map")
@Data
public class IrisImageMapMask {
    @Description("The key of a named imageMaps entry whose application is MASK")
    private String map = "";

    @Description("How this mask combines with masks before it")
    private IrisImageMapMaskOperation operation = IrisImageMapMaskOperation.MULTIPLY;

    @Description("Invert this mask before combining it")
    private boolean inverted = false;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Values below this threshold become zero")
    private double threshold = 0D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Soft transition width above threshold; zero is a hard edge")
    private double falloff = 0D;
}
