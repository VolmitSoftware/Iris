package art.arcane.iris.core.safeguard;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A boot that ends in Danger Mode used to say so only in the banner. The server then ran on with every
 * configured Iris world bound to a generator that throws on the first chunk, and nothing after the banner
 * said that out loud. {@code refuseVanillaFallback} never covers this case because it only runs when enable
 * itself fails.
 */
public class RuntimeLockNoticeTest {
    @Test
    public void aReadyRuntimeSaysNothing() {
        assertTrue(RuntimeLockNotice.compose(null, true).isEmpty());
        assertTrue(RuntimeLockNotice.compose("   ", true).isEmpty());
    }

    @Test
    public void aLockedRuntimeWithIrisWorldsNamesTheReasonAndWhatItBlocks() {
        List<String> lines = RuntimeLockNotice.compose("Iris runtime injection failed.", true);

        assertEquals(2, lines.size());
        assertEquals("Iris enabled with a locked runtime: Iris runtime injection failed.", lines.getFirst());
        assertEquals("Every configured Iris world is generation-locked and refuses to generate terrain"
                + " until this is resolved and the server restarts.", lines.get(1));
    }

    @Test
    public void aLockedRuntimeWithoutIrisWorldsSaysOnlyWhatIsBlocked() {
        List<String> lines = RuntimeLockNotice.compose("Iris runtime injection failed.", false);

        assertEquals(2, lines.size());
        assertEquals("Iris enabled with a locked runtime: Iris runtime injection failed.", lines.getFirst());
        assertEquals("This server has no Iris world storage, so nothing is generation-locked;"
                + " world creation and player login stay refused until this is resolved.", lines.get(1));
    }
}
