package art.arcane.iris.structure.object;


import java.util.List;

/**
 * Whole-placement phases surround a nested block pass. Block transforms are baked per placement,
 * so the pass exposes only BLOCK_WRITTEN, which runs for successful object writes. AFTER_BLOCKS
 * waits for the entire block pass, including markers. All whole-placement validation precedes
 * world writes.
 */
public final class PlacementStage<C> {
    public static final PlacementStage<Void> PREPARE = new PlacementStage<>("prepare", 0);
    public static final PlacementStage<Void> HEIGHT = new PlacementStage<>("height", 1);
    public static final PlacementStage<Void> VALIDATE = new PlacementStage<>("validate", 2);
    public static final PlacementStage<Void> BEFORE_BLOCKS = new PlacementStage<>("before-blocks", 3);
    public static final PlacementStage<PlacementBlockWrite> BLOCK_WRITTEN = new PlacementStage<>("block-written", 4);
    // BLOCKS denotes completion of the whole pass, so later steps can depend on all block work.
    public static final PlacementStage<PlacementLifecycle> BLOCKS = new PlacementStage<>("blocks", 5);
    public static final PlacementStage<Void> AFTER_BLOCKS = new PlacementStage<>("after-blocks", 6);
    static final List<PlacementStage<?>> ALL = List.of(PREPARE, HEIGHT, VALIDATE, BEFORE_BLOCKS,
            BLOCK_WRITTEN, BLOCKS, AFTER_BLOCKS);
    static final List<PlacementStage<Void>> BEFORE_PASS = List.of(PREPARE, HEIGHT, VALIDATE, BEFORE_BLOCKS);

    private final String name;
    final int order;

    private PlacementStage(String name, int order) {
        this.name = name;
        this.order = order;
    }

    @Override
    public String toString() {
        return name;
    }
}
