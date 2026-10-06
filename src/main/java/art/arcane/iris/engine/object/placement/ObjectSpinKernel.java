package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.prefab.IrisAxisRotationClamp;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;

/**
 * Precomputed vector rotation for a single placement.
 * <p>
 * This mirrors {@link IrisObjectRotation#rotate(IrisBlockVector, int, int, int)} branch for branch, with two
 * differences: it mutates the vector handed to it instead of cloning, and the per-axis angle plus its cosine
 * and sine are resolved once instead of once per block. The angle is a pure function of the axis clamp and the
 * spin, both fixed for the placement, and Math.cos/Math.sin are pure, so every produced double is identical to
 * the value the per-call form would have produced.
 */
 final class ObjectSpinKernel {
    private static final int MODE_NONE = 0;
    private static final int MODE_FLIP = 1;
    private static final int MODE_QUARTER = 2;
    private static final int MODE_THREE_QUARTER = 3;
    private static final int MODE_ANGLE = 4;

    IrisObjectRotation rotation;

    private final int spinX ;
    private final int spinY;
    private final int spinZ;
    private final int xMode;
    private final int yMode;
    private final int zMode;
    private final double xCos;
    private final double xSin;
    private final double yCos;
    private final double ySin;
    private final double zCos;
    private final double zSin;

    ObjectSpinKernel(IrisObjectRotation rotation, int spinX, int spinY, int spinZ) {
        this.rotation = rotation;
        xMode = rotation.canRotateX() ? modeOf(rotation.getXAxis()) : MODE_NONE;
        zMode = rotation.canRotateZ() ? modeOf(rotation.getZAxis()) : MODE_NONE;
        yMode = rotation.canRotateY() ? modeOf(rotation.getYAxis()) : MODE_NONE;
        this.spinX = spinX;
        this.spinY = spinY;
        this.spinZ = spinZ;
        double xAngle = xMode == MODE_ANGLE ? rotation.getXRotation(spinX) : 0;
        double zAngle = zMode == MODE_ANGLE ? rotation.getZRotation(spinZ) : 0;
        double yAngle = yMode == MODE_ANGLE ? rotation.getYRotation(spinY) : 0;
        xCos = Math.cos(xAngle);
        xSin = Math.sin(xAngle);
        zCos = Math.cos(zAngle);
        zSin = Math.sin(zAngle);
        yCos = Math.cos(yAngle);
        ySin = Math.sin(yAngle);
    }

    private static int modeOf(IrisAxisRotationClamp clamp) {
        if (!clamp.isLocked()) {
            return MODE_ANGLE;
        }

        if (Math.abs(clamp.getMax()) % 360D == 180D) {
            return MODE_FLIP;
        }

        if (clamp.getMax() % 360D == 90D || clamp.getMax() % 360D == -270D) {
            return MODE_QUARTER;
        }

        if (clamp.getMax() == -90D || clamp.getMax() % 360D == 270D) {
            return MODE_THREE_QUARTER;
        }

        return MODE_ANGLE;
    }

    PlatformBlockState rotate(PlatformBlockState state) {
        return rotation.rotate(state, spinX, spinY, spinZ);
    }

    /**
     * Rotates in place. Axis order (X, then Z, then Y) is load bearing.
     */
    void rotate(IrisBlockVector v) {
        if (!rotation.canRotate()) {
            return;
        }

        switch (xMode) {
            case MODE_FLIP -> {
                v.setZ(-v.getZ());
                v.setY(-v.getY());
            }
            case MODE_QUARTER -> {
                double z = v.getZ();
                v.setZ(v.getY());
                v.setY(-z);
            }
            case MODE_THREE_QUARTER -> {
                double z = v.getZ();
                v.setZ(-v.getY());
                v.setY(z);
            }
            case MODE_ANGLE -> {
                double y = xCos * v.getY() - xSin * v.getZ();
                double z = xSin * v.getY() + xCos * v.getZ();
                v.setY(y);
                v.setZ(z);
            }
            default -> {
            }
        }

        switch (zMode) {
            case MODE_FLIP -> {
                v.setY(-v.getY());
                v.setX(-v.getX());
            }
            case MODE_QUARTER -> {
                double y = v.getY();
                v.setY(v.getX());
                v.setX(-y);
            }
            case MODE_THREE_QUARTER -> {
                double y = v.getY();
                v.setY(-v.getX());
                v.setX(y);
            }
            case MODE_ANGLE -> {
                double x = zCos * v.getX() - zSin * v.getY();
                double y = zSin * v.getX() + zCos * v.getY();
                v.setX(x);
                v.setY(y);
            }
            default -> {
            }
        }

        switch (yMode) {
            case MODE_FLIP -> {
                v.setX(-v.getX());
                v.setZ(-v.getZ());
            }
            case MODE_QUARTER -> {
                double x = v.getX();
                v.setX(v.getZ());
                v.setZ(-x);
            }
            case MODE_THREE_QUARTER -> {
                double x = v.getX();
                v.setX(-v.getZ());
                v.setZ(x);
            }
            case MODE_ANGLE -> {
                double x = yCos * v.getX() + ySin * v.getZ();
                double z = -ySin * v.getX() + yCos * v.getZ();
                v.setX(x);
                v.setZ(z);
            }
            default -> {
            }
        }
    }
}
