package art.arcane.iris.modded;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class ModdedTeleportDeadlineTest {
    @Test
    public void deadlineOutlastsAColdFirstChunkAndStaysBounded() {
        long before = System.nanoTime();
        long deadline = ModdedTeleportDeadline.fromNow();
        long after = System.nanoTime();

        assertTrue("deadline must outlast a cold first chunk", deadline - before >= TimeUnit.SECONDS.toNanos(60L));
        assertTrue("deadline must be bounded", deadline - after <= TimeUnit.SECONDS.toNanos(120L));
    }
}
