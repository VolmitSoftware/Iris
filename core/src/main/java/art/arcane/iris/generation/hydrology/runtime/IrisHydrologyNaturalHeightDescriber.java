package art.arcane.iris.generation.hydrology.runtime;

/**
 * Explains how the natural terrain height at a column was produced, for the diagnostic that fires
 * when that height is not a finite number.
 */
@FunctionalInterface
public interface IrisHydrologyNaturalHeightDescriber {
    String describe(int x, int z);
}
