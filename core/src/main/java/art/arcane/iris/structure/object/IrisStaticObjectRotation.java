package art.arcane.iris.structure.object;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Description("Fixed object rotation in degrees around its saved origin")
public class IrisStaticObjectRotation {
    @MinNumber(-360)
    @MaxNumber(360)
    @Description("Fixed X-axis rotation in degrees, including negative and fractional angles")
    private double x = 0;
    @MinNumber(-360)
    @MaxNumber(360)
    @Description("Fixed Y-axis rotation in degrees, including negative and fractional angles")
    private double y = 0;
    @MinNumber(-360)
    @MaxNumber(360)
    @Description("Fixed Z-axis rotation in degrees, including negative and fractional angles")
    private double z = 0;

    public void validate() {
        validateAngle("rotation.x", x);
        validateAngle("rotation.y", y);
        validateAngle("rotation.z", z);
    }

    public IrisObjectRotation toRotation() {
        validate();
        return new IrisObjectRotation()
                .setEnabled(x != 0 || y != 0 || z != 0)
                .setXAxis(fixedAxis(x))
                .setYAxis(fixedAxis(y))
                .setZAxis(fixedAxis(z));
    }

    private static IrisAxisRotationClamp fixedAxis(double angle) {
        return new IrisAxisRotationClamp(angle != 0, false, angle, angle, 0);
    }

    private static void validateAngle(String field, double angle) {
        if (!Double.isFinite(angle) || angle < -360 || angle > 360) {
            throw new IllegalArgumentException(field + " must be finite and between -360 and 360 degrees.");
        }
    }
}
