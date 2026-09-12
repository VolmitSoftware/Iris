package art.arcane.iris.world;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisToolbeltEvacuationTest {
    @Test
    public void evacuationCompletionWaitsForEveryPlayerTeleport() {
        CompletableFuture<Boolean> first = new CompletableFuture<>();
        CompletableFuture<Boolean> second = new CompletableFuture<>();

        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(first, second));

        first.complete(true);
        assertFalse(evacuation.isDone());
        second.complete(true);
        assertTrue(evacuation.join());
    }

    @Test
    public void failedPlayerTeleportFailsEvacuation() {
        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(
                CompletableFuture.completedFuture(true),
                CompletableFuture.completedFuture(false)));

        assertFalse(evacuation.join());
    }

    @Test
    public void exceptionalPlayerTeleportFailsEvacuation() {
        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(
                CompletableFuture.completedFuture(true),
                CompletableFuture.failedFuture(new IllegalStateException("teleport failed"))));

        assertFalse(evacuation.join());
    }
}
