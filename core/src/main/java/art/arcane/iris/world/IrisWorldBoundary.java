package art.arcane.iris.world;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;

@Accessors(chain = true)
@NoArgsConstructor
@Description("The native Minecraft world border applied to worlds using this dimension")
@Data
public class IrisWorldBoundary {
    public static final double DEFAULT_DAMAGE_AMOUNT = 0.2D;
    public static final double DEFAULT_DAMAGE_BUFFER = 5D;
    public static final double MAXIMUM_CENTER = 29_999_984D;
    public static final double MAXIMUM_SIZE = 59_999_968D;

    @Description("The center of the boundary")
    private IrisWorldBoundaryCenter center = new IrisWorldBoundaryCenter();

    @MinNumber(1)
    @MaxNumber(59_999_968)
    @Description("The full border diameter in blocks, matching Minecraft world-border terminology")
    private double size = 16_384D;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Description("Distance from the border at which the client warning begins")
    private int warningDistance = 16;

    @MinNumber(0)
    @Description("Safe distance outside the border before damage begins")
    private double damageBuffer = DEFAULT_DAMAGE_BUFFER;

    @MinNumber(0)
    @Description("Damage per block beyond damageBuffer")
    private double damageAmount = DEFAULT_DAMAGE_AMOUNT;

    public static IrisWorldBoundary snapshot(IrisWorldBoundary configured) {
        IrisWorldBoundary source = Objects.requireNonNull(configured, "Configured worldBoundary");
        source.validate();
        return new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(source.getCenter().getX(), source.getCenter().getZ()))
                .setSize(source.getSize())
                .setWarningDistance(source.getWarningDistance())
                .setDamageBuffer(source.getDamageBuffer())
                .setDamageAmount(source.getDamageAmount());
    }

    public void validate() {
        if (center == null) {
            throw new IllegalArgumentException("worldBoundary.center is required");
        }
        if (!Double.isFinite(center.getX()) || Math.abs(center.getX()) > MAXIMUM_CENTER
                || !Double.isFinite(center.getZ()) || Math.abs(center.getZ()) > MAXIMUM_CENTER) {
            throw new IllegalArgumentException("worldBoundary.center must be finite and within +/-" + MAXIMUM_CENTER);
        }
        if (!Double.isFinite(size) || size < 1D || size > MAXIMUM_SIZE) {
            throw new IllegalArgumentException("worldBoundary.size must be between 1 and " + MAXIMUM_SIZE);
        }
        if (warningDistance < 0) {
            throw new IllegalArgumentException("worldBoundary.warningDistance cannot be negative");
        }
        if (!Double.isFinite(damageBuffer) || damageBuffer < 0D) {
            throw new IllegalArgumentException("worldBoundary.damageBuffer must be finite and non-negative");
        }
        if (!Double.isFinite(damageAmount) || damageAmount < 0D) {
            throw new IllegalArgumentException("worldBoundary.damageAmount must be finite and non-negative");
        }
    }

    public double minimumX() {
        return center.getX() - size / 2D;
    }

    public double maximumX() {
        return center.getX() + size / 2D;
    }

    public double minimumZ() {
        return center.getZ() - size / 2D;
    }

    public double maximumZ() {
        return center.getZ() + size / 2D;
    }
}
