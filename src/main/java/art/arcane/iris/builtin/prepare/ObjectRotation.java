/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.builtin.prepare;

import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.config.Desc;
import com.volmit.config.Snippet;

import java.util.Objects;

/**
 * How an object is turned around each axis, written as a placement's {@code rotation}. Positions turn around X, then Z,
 * then Y; an axis locked to a multiple of a quarter turn swaps coordinates exactly instead of turning by an angle.
 */
@Snippet("iris/prepare/object-rotator")
@Desc("Configures rotation for iris")
public final class ObjectRotation {
    private static final double QUARTER_TURN_DEGREES = 90;
    private static final double HALF_TURN_DEGREES = 180;
    private static final double THREE_QUARTER_TURN_DEGREES = 270;
    private static final double FULL_TURN_DEGREES = 360;

    @Desc("If this rotator is enabled or not")
    private boolean enabled = true;

    @Desc("The x axis rotation")
    private AxisRotationClamp xAxis = new AxisRotationClamp();

    @Desc("The y axis rotation")
    private AxisRotationClamp yAxis = AxisRotationClamp.of(true, false, 0, 0, QUARTER_TURN_DEGREES);

    @Desc("The z axis rotation")
    private AxisRotationClamp zAxis = new AxisRotationClamp();

    public ObjectRotation() {
    }

    /** A rotation with the given axes. */
    public static ObjectRotation of(boolean enabled, AxisRotationClamp xAxis, AxisRotationClamp yAxis, AxisRotationClamp zAxis) {
        ObjectRotation rotation = new ObjectRotation();
        rotation.enabled = enabled;
        rotation.xAxis = xAxis;
        rotation.yAxis = yAxis;
        rotation.zAxis = zAxis;
        return rotation;
    }

    /** A rotation locked to the given angles, in degrees; enabled only when one of them is not 0. */
    public static ObjectRotation of(double x, double y, double z) {
        return of(x != 0 || y != 0 || z != 0,
                AxisRotationClamp.locked(x != 0, x, QUARTER_TURN_DEGREES),
                AxisRotationClamp.locked(y != 0, y, QUARTER_TURN_DEGREES),
                AxisRotationClamp.locked(z != 0, z, QUARTER_TURN_DEGREES));
    }

    /** Upside down: half a turn around X and no turn around Y or Z. */
    public static ObjectRotation xFlip180() {
        return of(true, AxisRotationClamp.locked(true, HALF_TURN_DEGREES, 0), new AxisRotationClamp(), new AxisRotationClamp());
    }

    /** Upside down, then a random quarter turn around Y. */
    public static ObjectRotation xFlip180RandomY() {
        return xFlip180().withYAxis(AxisRotationClamp.of(true, false, 0, 0, QUARTER_TURN_DEGREES));
    }

    /** Upside down, then {@code y} degrees around Y. */
    public static ObjectRotation xFlip180WithY(double y) {
        return xFlip180().withYAxis(AxisRotationClamp.locked(true, y, QUARTER_TURN_DEGREES));
    }

    /** This rotation with {@code axis} around Y. */
    public ObjectRotation withYAxis(AxisRotationClamp axis) {
        return of(enabled, xAxis, axis, zAxis);
    }

    public boolean enabled() {
        return enabled;
    }

    public AxisRotationClamp xAxis() {
        return xAxis;
    }

    public AxisRotationClamp yAxis() {
        return yAxis;
    }

    public AxisRotationClamp zAxis() {
        return zAxis;
    }

    public boolean canRotateX() {
        return enabled && xAxis.enabled();
    }

    public boolean canRotateY() {
        return enabled && yAxis.enabled();
    }

    public boolean canRotateZ() {
        return enabled && zAxis.enabled();
    }

    public boolean canRotate() {
        return canRotateX() || canRotateY() || canRotateZ();
    }

    /** The angle, in radians, the object turns around {@code axis} for the random {@code spin}; 0 when the rotation or the axis is off. */
    public double radians(AxisRotationClamp axis, int spin) {
        if (!enabled || !axis.enabled()) {
            return 0;
        }
        return axis.getRadians(spin);
    }

    public double xRadians(int spin) {
        return radians(xAxis, spin);
    }

    public double yRadians(int spin) {
        return radians(yAxis, spin);
    }

    public double zRadians(int spin) {
        return radians(zAxis, spin);
    }

    public IrisBlockVector rotate(IrisBlockVector position) {
        return rotate(position, 0, 0, 0);
    }

    public IrisPosition rotate(IrisPosition position) {
        return rotate(position, 0, 0, 0);
    }

    public IrisPosition rotate(IrisPosition position, int spinX, int spinY, int spinZ) {
        IrisBlockVector turned = rotate(new IrisBlockVector(position.getX(), position.getY(), position.getZ()), spinX, spinY, spinZ);
        return new IrisPosition(turned.getBlockX(), turned.getBlockY(), turned.getBlockZ());
    }

    /** The direction closest to {@code direction} turned by this rotation. */
    public IrisDirection rotate(IrisDirection direction) {
        IrisBlockVector turned = rotate(new IrisBlockVector(direction.x(), direction.y(), direction.z()));
        double closestDistance = Double.MAX_VALUE;
        IrisDirection closest = null;
        for (IrisDirection candidate : IrisDirection.values()) {
            double distance = new IrisBlockVector(candidate.x(), candidate.y(), candidate.z()).distance(turned);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    /** {@code position} turned around X, then Z, then Y, each by the angle {@code spin} picks for it; a copy unless nothing turns. */
    public IrisBlockVector rotate(IrisBlockVector position, int spinX, int spinY, int spinZ) {
        if (!canRotate()) {
            return position;
        }
        IrisBlockVector turned = position.clone();
        if (canRotateX()) {
            turnAroundX(turned, spinX);
        }
        if (canRotateZ()) {
            turnAroundZ(turned, spinZ);
        }
        if (canRotateY()) {
            turnAroundY(turned, spinY);
        }
        return turned;
    }

    private void turnAroundX(IrisBlockVector v, int spin) {
        if (!xAxis.isLocked()) {
            v.rotateAroundX(xRadians(spin));
            return;
        }
        double angle = xAxis.max();
        if (Math.abs(angle) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setZ(-v.getZ());
            v.setY(-v.getY());
        } else if (isQuarterTurn(angle)) {
            double z = v.getZ();
            v.setZ(v.getY());
            v.setY(-z);
        } else if (isThreeQuarterTurn(angle)) {
            double z = v.getZ();
            v.setZ(-v.getY());
            v.setY(z);
        } else {
            v.rotateAroundX(xRadians(spin));
        }
    }

    private void turnAroundZ(IrisBlockVector v, int spin) {
        if (!zAxis.isLocked()) {
            v.rotateAroundZ(zRadians(spin));
            return;
        }
        double angle = zAxis.max();
        if (Math.abs(angle) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setY(-v.getY());
            v.setX(-v.getX());
        } else if (isQuarterTurn(angle)) {
            double y = v.getY();
            v.setY(v.getX());
            v.setX(-y);
        } else if (isThreeQuarterTurn(angle)) {
            double y = v.getY();
            v.setY(-v.getX());
            v.setX(y);
        } else {
            v.rotateAroundZ(zRadians(spin));
        }
    }

    private void turnAroundY(IrisBlockVector v, int spin) {
        if (!yAxis.isLocked()) {
            v.rotateAroundY(yRadians(spin));
            return;
        }
        double angle = yAxis.max();
        if (Math.abs(angle) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setX(-v.getX());
            v.setZ(-v.getZ());
        } else if (isQuarterTurn(angle)) {
            double x = v.getX();
            v.setX(v.getZ());
            v.setZ(-x);
        } else if (isThreeQuarterTurn(angle)) {
            double x = v.getX();
            v.setX(-v.getZ());
            v.setZ(x);
        } else {
            v.rotateAroundY(yRadians(spin));
        }
    }

    private static boolean isQuarterTurn(double angle) {
        return angle % FULL_TURN_DEGREES == QUARTER_TURN_DEGREES || angle % FULL_TURN_DEGREES == -THREE_QUARTER_TURN_DEGREES;
    }

    private static boolean isThreeQuarterTurn(double angle) {
        return angle == -QUARTER_TURN_DEGREES || angle % FULL_TURN_DEGREES == THREE_QUARTER_TURN_DEGREES;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ObjectRotation rotation
                && enabled == rotation.enabled
                && Objects.equals(xAxis, rotation.xAxis)
                && Objects.equals(yAxis, rotation.yAxis)
                && Objects.equals(zAxis, rotation.zAxis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, xAxis, yAxis, zAxis);
    }
}
