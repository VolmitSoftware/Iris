package art.arcane.iris.world.history;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationTransitionTest {
    private static final String BOUNDARY =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TERRAIN =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void completionPreservesTheImmutableTransitionRecipe() {
        GenerationTransition pending = GenerationTransition.pending(256);

        GenerationTransition completed = pending.complete(BOUNDARY, TERRAIN);

        assertEquals(GenerationTransition.CURRENT_ALGORITHM_VERSION, completed.algorithmVersion());
        assertEquals(256, completed.widthBlocks());
        assertEquals(BOUNDARY, completed.boundaryIdentity());
        assertEquals(TERRAIN, completed.terrainSignatureIdentity());
        assertFalse(pending.isComplete());
        assertTrue(completed.isComplete());
        assertSame(completed, completed.complete(BOUNDARY, TERRAIN));
        assertThrows(IllegalStateException.class, () -> completed.complete(TERRAIN, BOUNDARY));
    }

    @Test
    public void strictJsonRoundTripRetainsPendingAndCompleteStates() {
        GenerationTransition pending = GenerationTransition.pending(512);
        GenerationTransition completed = pending.complete(BOUNDARY, TERRAIN);

        assertEquals(pending, GenerationTransition.fromJson(pending.toJson()));
        assertEquals(completed, GenerationTransition.fromJson(completed.toJson()));

        JsonObject unknown = completed.toJson();
        unknown.addProperty("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> GenerationTransition.fromJson(unknown));
    }

    @Test
    public void rejectsInvalidWidthsAndPartiallyCompletedSnapshots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationTransition.pending(GenerationTransition.MINIMUM_WIDTH_BLOCKS - 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationTransition.pending(GenerationTransition.MAXIMUM_WIDTH_BLOCKS + 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationTransition(
                        GenerationTransition.CURRENT_ALGORITHM_VERSION,
                        256,
                        BOUNDARY,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationTransition(0, 256, BOUNDARY, TERRAIN)
        );
    }

    @Test
    public void activationRoundTripRetainsItsTransition() {
        GenerationActivation activation = GenerationActivation.next(2L, BOUNDARY, 1L, 99L, 256)
                .completeTransition(BOUNDARY, TERRAIN);

        GenerationActivation restored = GenerationActivation.fromJson(activation.toJson());

        assertEquals(activation, restored);
        assertEquals(256, restored.transition().widthBlocks());
        assertEquals(BOUNDARY, restored.transition().boundaryIdentity());
    }
}
