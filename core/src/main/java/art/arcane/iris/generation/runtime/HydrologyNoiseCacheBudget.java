package art.arcane.iris.generation.runtime;

final class HydrologyNoiseCacheBudget {
    static final int MAXIMUM_CHUNKS = 32_768;
    static final int BYTES_PER_CHUNK = 4_096;
    static final HydrologyNoiseCacheBudget SHARED = new HydrologyNoiseCacheBudget(Runtime.getRuntime().maxMemory() / 8L);

    private final long maximumBytes;
    private long reservedBytes;

    HydrologyNoiseCacheBudget(long maximumBytes) {
        if (maximumBytes < 0L) {
            throw new IllegalArgumentException("Cache budget must not be negative.");
        }
        this.maximumBytes = maximumBytes;
    }

    synchronized Reservation reserve(int cacheCount, int requestedAdditionalChunks) {
        if (cacheCount <= 0 || requestedAdditionalChunks < 0 || requestedAdditionalChunks > MAXIMUM_CHUNKS) {
            throw new IllegalArgumentException("Invalid hydrology cache reservation.");
        }
        long bytesPerIncrement = (long) BYTES_PER_CHUNK * cacheCount;
        int additionalChunks = (int) Math.min(requestedAdditionalChunks, (maximumBytes - reservedBytes) / bytesPerIncrement);
        long bytes = additionalChunks * bytesPerIncrement;
        reservedBytes += bytes;
        return new Reservation(this, additionalChunks, bytes);
    }

    synchronized long reservedBytes() {
        return reservedBytes;
    }

    private synchronized void release(Reservation reservation) {
        if (!reservation.released) {
            reservedBytes -= reservation.bytes;
            reservation.released = true;
        }
    }

    static final class Reservation implements AutoCloseable {
        private final HydrologyNoiseCacheBudget budget;
        private final int additionalChunks;
        private final long bytes;
        private boolean released;

        private Reservation(HydrologyNoiseCacheBudget budget, int additionalChunks, long bytes) {
            this.budget = budget;
            this.additionalChunks = additionalChunks;
            this.bytes = bytes;
        }

        int additionalChunks() {
            return additionalChunks;
        }

        @Override
        public void close() {
            budget.release(this);
        }
    }
}
