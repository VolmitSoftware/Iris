package art.arcane.iris.pack.datapack.export.ir;

public final class FieldValidator {
    private FieldValidator() {
    }

    public static void requireTwoDimensional(Field root, String name) {
        if (root.axes().contains(Axis.Y)) {
            throw new IllegalStateException("2D field '" + name + "' depends on Y");
        }
    }
}
