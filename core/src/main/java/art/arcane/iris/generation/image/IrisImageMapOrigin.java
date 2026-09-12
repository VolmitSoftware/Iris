package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A two-dimensional image-map coordinate")
@Data
public class IrisImageMapOrigin {
    @Description("X coordinate in world blocks for origin, or source pixels for sourceOrigin")
    private double x = 0D;

    @Description("Z coordinate in world blocks for origin, or source image Y pixels for sourceOrigin")
    private double z = 0D;

    public IrisImageMapOrigin(double x, double z) {
        this.x = x;
        this.z = z;
    }
}
