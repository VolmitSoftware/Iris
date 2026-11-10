package art.arcane.iris.builtin.structure;

import com.volmit.resource.ResourceId;

/** The seed purposes of structure placement, for {@link com.volmit.world.PackWorld#seed(ResourceId)}. */
public final class StructureSeeds {
    /**
     * The seed placements start from: where structures start, their anchors and their pieces. Iris keeps the seed it has
     * always used for it, so the structures of existing worlds stay where they are.
     */
    public static final ResourceId PLACEMENT = ResourceId.parse("iris:structure/placement");

    private StructureSeeds() {
    }
}
