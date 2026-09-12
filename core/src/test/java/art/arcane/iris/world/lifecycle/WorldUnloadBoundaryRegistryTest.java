package art.arcane.iris.world.lifecycle;

import org.junit.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldUnloadBoundaryRegistryTest {
    @Test
    public void claimedBoundaryCompletesOnlyWhenRawUnloadCompletesTrue() {
        String worldIdentity = "iris:boundary_true";
        WorldUnloadBoundaryRegistry.Boundary boundary = WorldUnloadBoundaryRegistry.begin(worldIdentity);
        CompletionStage<Boolean> claimed = WorldUnloadBoundaryRegistry.claim(worldIdentity);

        assertFalse(claimed.toCompletableFuture().isDone());
        WorldUnloadBoundaryRegistry.complete(boundary, true, null);
        assertTrue(claimed.toCompletableFuture().join());
        assertNull(WorldUnloadBoundaryRegistry.claim(worldIdentity));
    }

    @Test
    public void rawFalseCompletionRemainsFalse() {
        String worldIdentity = "iris:boundary_false";
        WorldUnloadBoundaryRegistry.Boundary boundary = WorldUnloadBoundaryRegistry.begin(worldIdentity);
        CompletionStage<Boolean> claimed = WorldUnloadBoundaryRegistry.claim(worldIdentity);

        WorldUnloadBoundaryRegistry.complete(boundary, false, null);

        assertFalse(claimed.toCompletableFuture().join());
    }

    @Test
    public void rawFailureCompletesTheClaimedBoundaryExceptionally() {
        String worldIdentity = "iris:boundary_failure";
        WorldUnloadBoundaryRegistry.Boundary boundary = WorldUnloadBoundaryRegistry.begin(worldIdentity);
        CompletionStage<Boolean> claimed = WorldUnloadBoundaryRegistry.claim(worldIdentity);

        WorldUnloadBoundaryRegistry.complete(
                boundary,
                null,
                new CompletionException(new IllegalStateException("raw unload failed")));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> claimed.toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertTrue("raw unload failed".equals(failure.getCause().getMessage()));
    }

    @Test
    public void duplicateActiveBoundaryIsRejected() {
        String worldIdentity = "iris:boundary_duplicate";
        WorldUnloadBoundaryRegistry.Boundary boundary = WorldUnloadBoundaryRegistry.begin(worldIdentity);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> WorldUnloadBoundaryRegistry.begin(worldIdentity));
        } finally {
            WorldUnloadBoundaryRegistry.complete(boundary, false, null);
        }
    }
}
