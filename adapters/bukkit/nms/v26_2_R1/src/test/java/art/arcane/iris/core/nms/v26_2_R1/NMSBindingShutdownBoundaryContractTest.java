package art.arcane.iris.core.nms.v26_2_R1;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class NMSBindingShutdownBoundaryContractTest {
    @Test
    public void shutdownBoundaryUsesPaperFullyShutdownStateAndServerThread() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsBindingSource"))).replace("\r\n", "\n");
        String boundary = section(source, "public boolean awaitServerShutdownBoundary", "public KMap<Material, List<BlockProperty>> getBlockProperties");

        assertTrue(boundary.contains("ServerShutdownBoundary.await("));
        assertTrue(boundary.contains("server.hasFullyShutdown"));
        assertTrue(boundary.contains("server.getRunningThread()"));
    }

    @Test
    public void chunkTaskPollingIsServerThreadOnlyAndSkipsFolia() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsBindingSource"))).replace("\r\n", "\n");
        String polling = section(source, "public boolean pollChunkTask", "public void flushChunkIO");

        int foliaGuard = polling.indexOf("J.isFolia()");
        int ownerGuard = polling.indexOf("level.getServer().getRunningThread()");
        int poll = polling.indexOf("level.getChunkSource().pollTask()");
        assertTrue(foliaGuard >= 0 && ownerGuard > foliaGuard && poll > ownerGuard);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing source section starting with " + startMarker, start >= 0);
        assertTrue("Missing source section ending with " + endMarker, end > start);
        return source.substring(start, end);
    }
}
