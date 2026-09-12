package art.arcane.iris.generation.hydrology.runtime;

@FunctionalInterface
public interface IrisHydrologyNaturalSampleProvider {
    IrisHydrologyNaturalSample sample(int blockX, int blockZ, double naturalHeight);
}
