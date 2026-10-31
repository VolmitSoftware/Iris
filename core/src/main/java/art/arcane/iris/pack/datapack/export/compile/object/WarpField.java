package art.arcane.iris.pack.datapack.export.compile.object;

@FunctionalInterface
public interface WarpField {
    double offset(double x, double y, double z);
}
