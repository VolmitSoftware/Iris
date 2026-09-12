package art.arcane.iris.world.lifecycle;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WorldLifecycleDiagnosticsTest {
    @Test
    public void studioCreateSelectionFailurePrintsFullStacktrace() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.PAPER, false, false, false));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            try {
                service.create(request).join();
                fail("Expected lifecycle create to fail when paper_like_runtime is unavailable.");
            } catch (CompletionException | IllegalStateException ignored) {
            }
        } finally {
            System.setErr(originalErr);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("WorldLifecycle create backend selection failed"));
        assertTrue(text.contains("paper_like_runtime"));
        assertTrue(text.contains("IllegalStateException"));
    }
}
