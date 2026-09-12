package art.arcane.iris.generation.hydrology.runtime;

@FunctionalInterface
public interface IrisHydrologyNaturalOceanClassifier {
    boolean isOcean(int blockX, int blockZ);
}
