package art.arcane.iris.generation.hydrology.raster;

@FunctionalInterface
public interface NaturalHeights {
    double height(int x, int z);
}
