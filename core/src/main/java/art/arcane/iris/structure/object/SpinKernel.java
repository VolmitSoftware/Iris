package art.arcane.iris.structure.object;

import art.arcane.iris.generation.geometry.IrisBlockVector;

/**
 * Precomputed vector rotation for a single placement.
 * <p>
 * This mirrors {@link IrisObjectRotation#rotate(IrisBlockVector, int, int, int)} branch for branch, with two
 * differences: it mutates the vector handed to it instead of cloning, and the per-axis angle plus its cosine
 * and sine are resolved once instead of once per block. The angle is a pure function of the axis clamp and the
 * spin, both fixed for the placement, and Math.cos/Math.sin are pure, so every produced double is identical to
 * the value the per-call form would have produced.
 */
final class SpinKernel {
    static final int MODE_NONE = 0;
    static final int MODE_FLIP = 1;
    static final int MODE_QUARTER = 2;
    static final int MODE_THREE_QUARTER = 3;
    static final int MODE_ANGLE = 4;

    final boolean rotates;
    final int xMode;
    final int yMode;
    final int zMode;
    final double xCos;
    final double xSin;
    final double yCos;
    final double ySin;
    final double zCos;
    final double zSin;

    SpinKernel(IrisObjectRotation rotation, int spinx, int spiny, int spinz) {
        rotates = rotation.canRotate();
        xMode = rotation.canRotateX() ? modeOf(rotation.getXAxis()) : MODE_NONE;
        zMode = rotation.canRotateZ() ? modeOf(rotation.getZAxis()) : MODE_NONE;
        yMode = rotation.canRotateY() ? modeOf(rotation.getYAxis()) : MODE_NONE;

        double xAngle = xMode == MODE_ANGLE ? rotation.getXRotation(spinx) : 0;
        double zAngle = zMode == MODE_ANGLE ? rotation.getZRotation(spinz) : 0;
        double yAngle = yMode == MODE_ANGLE ? rotation.getYRotation(spiny) : 0;
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

    /**
     * Rotates in place. Axis order (X, then Z, then Y) is load bearing.
     */
    void rotate(IrisBlockVector v) {
        if (!rotates) {
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
