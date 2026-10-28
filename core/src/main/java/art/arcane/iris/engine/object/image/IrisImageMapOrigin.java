package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.Desc;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("A two-dimensional image-map coordinate")
@Data
public class IrisImageMapOrigin {
    @Desc("X coordinate in world blocks for origin, or source pixels for sourceOrigin")
    private double x = 0D;

    @Desc("Z coordinate in world blocks for origin, or source image Y pixels for sourceOrigin")
    private double z = 0D;

    public IrisImageMapOrigin(double x, double z) {
        this.x = x;
        this.z = z;
    }
}
