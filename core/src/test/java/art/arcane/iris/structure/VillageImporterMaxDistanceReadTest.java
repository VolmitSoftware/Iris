package art.arcane.iris.structure;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * JigsawStructure.maxDistanceFromCenter is a plain int on some server builds and a
 * JigsawStructure$MaxDistance record ({int horizontal, int vertical}) on others (e.g. Leaf
 * 26.2-33). The reflective member reader must handle both — the wrapper shape previously threw
 * IllegalArgumentException from Field.getInt, failing the jigsaw import of every datapack
 * structure ("Failed to read jigsaw structure graph").
 */
public class VillageImporterMaxDistanceReadTest {
    private static final class IntShape {
        private final int maxDistanceFromCenter = 80;
    }

    private record MaxDistance(int horizontal, int vertical) {
    }

    private static final class WrapperShape {
        private final MaxDistance maxDistanceFromCenter = new MaxDistance(96, 48);
    }

    private static final class BoxedShape {
        private final Integer maxDistanceFromCenter = 64;
    }

    @Test
    public void readsPlainIntField() throws Exception {
        assertEquals(80, VillageImporter.readIntMember(new IntShape(), "maxDistanceFromCenter"));
    }

    @Test
    public void readsLargestIntComponentFromWrapperRecord() throws Exception {
        assertEquals(96, VillageImporter.readIntMember(new WrapperShape(), "maxDistanceFromCenter"));
    }

    @Test
    public void readsBoxedNumberField() throws Exception {
        assertEquals(64, VillageImporter.readIntMember(new BoxedShape(), "maxDistanceFromCenter"));
    }
}
