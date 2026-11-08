package art.arcane.iris.core.tooling;

import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldListener;
import com.volmit.tooling.world.ToolWorldSpec;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UnsupportedToolWorldsTest {
    @Test
    public void openingFailsAndSaysThatToolWorldsAreNotSupportedYet() {
        ToolWorldSpec spec = new ToolWorldSpec("scratch", (chunkX, chunkZ, canvas) -> { }, new ToolWorldListener() { });

        CompletableFuture<ToolWorld> opened = UnsupportedToolWorlds.INSTANCE.open(spec);

        ExecutionException failure = assertThrows(ExecutionException.class, opened::get);
        assertTrue(failure.getCause() instanceof UnsupportedOperationException);
        assertEquals("Tool worlds are not supported on this platform yet", failure.getCause().getMessage());
    }

    @Test
    public void nothingIsFound() {
        assertTrue(UnsupportedToolWorlds.INSTANCE.find("scratch").isEmpty());
    }
}
