package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PregenListenerDefaultsTest {
    @Test
    public void anUnqualifiedChunkIsReportedAsFreshlyGenerated() {
        RecordingListener listener = new RecordingListener();

        listener.onChunkGenerated(4, -9);

        assertEquals(List.of("4/-9/false"), listener.generated);
    }

    @Test
    public void anExplicitCacheFlagIsPassedThroughUnchanged() {
        RecordingListener listener = new RecordingListener();

        listener.onChunkGenerated(1, 2, true);
        listener.onChunkGenerated(3, 4, false);

        assertEquals(List.of("1/2/true", "3/4/false"), listener.generated);
    }

    @Test
    public void aFailedChunkIsIgnoredByListenersThatDoNotCare() {
        RecordingListener listener = new RecordingListener();

        listener.onChunkFailed(7, 7);

        assertTrue(listener.generated.isEmpty());
    }

    private static final class RecordingListener implements PregenListener {
        private final List<String> generated = new ArrayList<>();

        @Override
        public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generatedChunks, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
        }

        @Override
        public void onChunkGenerating(int x, int z) {
        }

        @Override
        public void onChunkGenerated(int x, int z, boolean cached) {
            generated.add(x + "/" + z + "/" + cached);
        }

        @Override
        public void onRegionGenerated(int x, int z) {
        }

        @Override
        public void onRegionGenerating(int x, int z) {
        }

        @Override
        public void onChunkCleaned(int x, int z) {
        }

        @Override
        public void onRegionSkipped(int x, int z) {
        }

        @Override
        public void onNetworkStarted(int x, int z) {
        }

        @Override
        public void onNetworkFailed(int x, int z) {
        }

        @Override
        public void onNetworkReclaim(int revert) {
        }

        @Override
        public void onNetworkGeneratedChunk(int x, int z) {
        }

        @Override
        public void onNetworkDownloaded(int x, int z) {
        }

        @Override
        public void onClose() {
        }

        @Override
        public void onSaving() {
        }

        @Override
        public void onChunkExistsInRegionGen(int x, int z) {
        }
    }
}
