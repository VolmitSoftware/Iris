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

package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockTurn;

/**
 * How Iris's object model turns an object by fixed angles: positions turn around X, then Z, then Y, and an axis turned by
 * a multiple of a quarter turn swaps coordinates exactly instead of turning by an angle. The same turn as the built-in
 * modules' placement rotation locked to these angles, kept in core so the object model does not read module settings.
 *
 * @param xDegrees degrees around X; 0 for none
 * @param yDegrees degrees around Y; 0 for none
 * @param zDegrees degrees around Z; 0 for none
 */
public record ObjectTurn(double xDegrees, double yDegrees, double zDegrees) {
    public static final ObjectTurn NONE = new ObjectTurn(0, 0, 0);

    private static final double QUARTER_TURN_DEGREES = 90;
    private static final double HALF_TURN_DEGREES = 180;
    private static final double THREE_QUARTER_TURN_DEGREES = 270;
    private static final double FULL_TURN_DEGREES = 360;

    public static ObjectTurn of(double xDegrees, double yDegrees, double zDegrees) {
        return new ObjectTurn(xDegrees, yDegrees, zDegrees);
    }

    public boolean canRotate() {
        return xDegrees != 0 || yDegrees != 0 || zDegrees != 0;
    }

    /** The directions this turn turns, for the facing, axis and connections of block states. */
    public BlockTurn blockTurn() {
        return (x, y, z) -> {
            IrisBlockVector turned = rotate(new IrisBlockVector(x, y, z));
            return new BlockTurn.Direction(turned.getX(), turned.getY(), turned.getZ());
        };
    }

    /** {@code position} turned around X, then Z, then Y; a copy unless nothing turns. */
    public IrisBlockVector rotate(IrisBlockVector position) {
        if (!canRotate()) {
            return position;
        }
        IrisBlockVector turned = position.clone();
        if (xDegrees != 0) {
            turnAroundX(turned);
        }
        if (zDegrees != 0) {
            turnAroundZ(turned);
        }
        if (yDegrees != 0) {
            turnAroundY(turned);
        }
        return turned;
    }

    public IrisPosition rotate(IrisPosition position) {
        IrisBlockVector turned = rotate(new IrisBlockVector(position.getX(), position.getY(), position.getZ()));
        return new IrisPosition(turned.getBlockX(), turned.getBlockY(), turned.getBlockZ());
    }

    /** The direction closest to {@code direction} turned by this turn. */
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

    private void turnAroundX(IrisBlockVector v) {
        if (Math.abs(xDegrees) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setZ(-v.getZ());
            v.setY(-v.getY());
        } else if (isQuarterTurn(xDegrees)) {
            double z = v.getZ();
            v.setZ(v.getY());
            v.setY(-z);
        } else if (isThreeQuarterTurn(xDegrees)) {
            double z = v.getZ();
            v.setZ(-v.getY());
            v.setY(z);
        } else {
            v.rotateAroundX(radians(xDegrees));
        }
    }

    private void turnAroundZ(IrisBlockVector v) {
        if (Math.abs(zDegrees) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setY(-v.getY());
            v.setX(-v.getX());
        } else if (isQuarterTurn(zDegrees)) {
            double y = v.getY();
            v.setY(v.getX());
            v.setX(-y);
        } else if (isThreeQuarterTurn(zDegrees)) {
            double y = v.getY();
            v.setY(-v.getX());
            v.setX(y);
        } else {
            v.rotateAroundZ(radians(zDegrees));
        }
    }

    private void turnAroundY(IrisBlockVector v) {
        if (Math.abs(yDegrees) % FULL_TURN_DEGREES == HALF_TURN_DEGREES) {
            v.setX(-v.getX());
            v.setZ(-v.getZ());
        } else if (isQuarterTurn(yDegrees)) {
            double x = v.getX();
            v.setX(v.getZ());
            v.setZ(-x);
        } else if (isThreeQuarterTurn(yDegrees)) {
            double x = v.getX();
            v.setX(-v.getZ());
            v.setZ(x);
        } else {
            v.rotateAroundY(radians(yDegrees));
        }
    }

    /** The angle in radians a turn by {@code degrees} not on a quarter turn uses: whole degrees, rounded up, without the sign. */
    private static double radians(double degrees) {
        return Math.toRadians(Math.ceil(Math.abs(degrees % FULL_TURN_DEGREES)));
    }

    private static boolean isQuarterTurn(double angle) {
        return angle % FULL_TURN_DEGREES == QUARTER_TURN_DEGREES || angle % FULL_TURN_DEGREES == -THREE_QUARTER_TURN_DEGREES;
    }

    private static boolean isThreeQuarterTurn(double angle) {
        return angle == -QUARTER_TURN_DEGREES || angle % FULL_TURN_DEGREES == THREE_QUARTER_TURN_DEGREES;
    }
}
