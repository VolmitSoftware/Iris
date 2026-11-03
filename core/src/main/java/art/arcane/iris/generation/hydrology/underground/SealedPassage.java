package art.arcane.iris.generation.hydrology.underground;

public interface SealedPassage {
    long id();

    int waterLevel();

    int roofClearance();

    int[] bounds();

    boolean column(int x, int z, int[] out);
}
