package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnginePanicTest {
    @Test
    public void savedSnapshotDoesNotReadCurrentValues() {
        EnginePanic.Diagnostics diagnostics = EnginePanic.scoped("test-world");
        diagnostics.add("read-chunk", "Chunk[1]");
        diagnostics.saveLast();
        diagnostics.add("read-chunk", "Chunk[2]");

        assertEquals("Chunk[1]", diagnostics.lastSnapshot().get("read-chunk"));
        assertEquals("Chunk[2]", diagnostics.currentSnapshot().get("read-chunk"));
    }

    @Test
    public void currentDiagnosticsAreIsolatedByThread() throws Exception {
        EnginePanic.Diagnostics diagnostics = EnginePanic.scoped("test-world");
        AtomicReference<Map<String, String>> first = new AtomicReference<>();
        AtomicReference<Map<String, String>> second = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            diagnostics.add("read-chunk", "Chunk[10]");
            first.set(diagnostics.currentSnapshot());
        });
        Thread secondThread = new Thread(() -> {
            diagnostics.add("read-chunk", "Chunk[20]");
            second.set(diagnostics.currentSnapshot());
        });

        firstThread.start();
        firstThread.join();
        secondThread.start();
        secondThread.join();

        assertEquals("Chunk[10]", first.get().get("read-chunk"));
        assertEquals("Chunk[20]", second.get().get("read-chunk"));
        assertTrue(diagnostics.currentSnapshot().isEmpty());
    }
}
