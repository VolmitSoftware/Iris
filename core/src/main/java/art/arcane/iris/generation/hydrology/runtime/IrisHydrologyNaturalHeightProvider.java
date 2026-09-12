package art.arcane.iris.generation.hydrology.runtime;

@FunctionalInterface
public interface IrisHydrologyNaturalHeightProvider {
    double sample(int blockX, int blockZ);
}
