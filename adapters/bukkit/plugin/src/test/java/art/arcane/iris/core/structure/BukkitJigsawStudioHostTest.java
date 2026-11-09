package art.arcane.iris.core.structure;

import art.arcane.iris.core.pack.BrokenPackException;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.tools.IrisCreator;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BukkitJigsawStudioHostTest {
    private final BukkitJigsawStudioHost host = new BukkitJigsawStudioHost();

    @Test
    public void jigsawStudioUsesItsDedicatedOpenLifecycle() {
        assertEquals(
                StudioOpenCoordinator.StudioOpenKind.TOOL,
                BukkitJigsawStudioHost.STUDIO_OPEN_KIND);
        assertFalse(BukkitJigsawStudioHost.STUDIO_OPEN_KIND.openWorkspace());
        assertFalse(BukkitJigsawStudioHost.STUDIO_OPEN_KIND.teleportThroughStandardEntry());
        assertEquals(
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                BukkitJigsawStudioHost.STUDIO_OPEN_KIND.datapackPreparation());
    }

    @Test
    public void packValidationDenialsAreExpectedOpenFailures() {
        BrokenPackException failure = new BrokenPackException(
                "overworld", List.of("Biome 'broken' has no resolvable regions."));

        assertTrue(host.isExpectedOpenDenial(failure));
        assertTrue(host.isExpectedOpenDenial(new CompletionException(failure)));
        assertFalse(host.isExpectedOpenDenial(new IllegalStateException("Unexpected Studio failure")));
    }
}
