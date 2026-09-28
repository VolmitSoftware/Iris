package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PregenChunkRetentionTest {
    @Test
    public void retainsEachPositionOfTheDependencySquareOnce() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 2, 10_000, 0, 0, 0, 0);

        retention.retainAround(5, 5);
        assertEquals(25, tickets.retained.size());
        assertEquals(square(3, 7, 3, 7), new HashSet<>(tickets.retained));

        retention.retainAround(6, 5);
        assertEquals(30, tickets.retained.size());
        assertEquals(square(8, 8, 3, 7), new HashSet<>(tickets.retained.subList(25, 30)));
        assertEquals(30, retention.size());
    }

    @Test
    public void drainedRegionKeepsOnlyTheBorderAnUndrainedNeighbourStillReaches() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 2, 10_000, 0, 0, 1, 0);
        retention.retainAround(16, 16);
        retention.retainAround(30, 16);

        retention.drained(0, 0);
        Set<Long> released = new HashSet<>(square(14, 18, 14, 18));
        released.addAll(square(28, 29, 14, 18));
        assertEquals(released, new HashSet<>(tickets.released));
        assertEquals(15, retention.size());

        retention.drained(1, 0);
        assertEquals(new HashSet<>(tickets.retained), new HashSet<>(tickets.released));
        assertEquals(tickets.retained.size(), tickets.released.size());
        assertEquals(0, retention.size());
    }

    @Test
    public void positionsOutsideTheAreaReleaseWithTheInAreaRegionsThatReachThem() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 10, 10_000, 0, 0, 0, 0);
        retention.retainAround(0, 0);
        retention.retainAround(31, 31);

        retention.drained(0, 0);
        assertEquals(new HashSet<>(tickets.retained), new HashSet<>(tickets.released));
        assertEquals(0, retention.size());
    }

    @Test
    public void aLongRunOfRegionsOnlyHoldsTheActiveRegionAndTheStripBehindIt() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 10, 1_000_000, 0, 0, 127, 0);
        int peak = 0;
        for (int region = 0; region < 128; region++) {
            for (int x = 0; x < 32; x += 4) {
                for (int z = 0; z < 32; z += 4) {
                    retention.retainAround(region * 32 + x, z);
                }
            }
            peak = Math.max(peak, retention.size());
            retention.drained(region, 0);
            int strip = region == 127 ? 0 : 17 * 49;
            assertEquals(strip, retention.size());
        }
        assertEquals(49 * 49, peak);
        assertEquals(new HashSet<>(tickets.retained), new HashSet<>(tickets.released));
    }

    @Test
    public void overCapacityReleasesTheDrainedStripsFarthestFromTheActiveRegionFirst() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 1, 20, 0, 0, 4, 0);
        retention.retainAround(31, 5);
        retention.drained(0, 0);
        assertEquals(square(30, 30, 4, 6), new HashSet<>(tickets.released));
        assertEquals(6, retention.size());

        retention.retainAround(127, 5);
        retention.drained(3, 0);
        assertEquals(12, retention.size());

        retention.retainAround(40, 5);
        Set<Long> released = new HashSet<>(square(30, 30, 4, 6));
        released.addAll(square(126, 127, 4, 6));
        assertEquals(released, new HashSet<>(tickets.released));
        assertEquals(18, retention.size());
    }

    @Test
    public void releaseAllDropsEveryTicketAndRetainsAgainAfterwards() {
        RecordingTickets tickets = new RecordingTickets();
        PregenChunkRetention retention = new PregenChunkRetention(tickets, 1, 10_000, 0, 0, 0, 0);
        retention.retainAround(4, 4);
        retention.releaseAll();
        assertEquals(new HashSet<>(tickets.retained), new HashSet<>(tickets.released));
        assertEquals(0, retention.size());

        retention.retainAround(4, 4);
        assertEquals(18, tickets.retained.size());
        assertEquals(9, retention.size());
    }

    private static Set<Long> square(int minX, int maxX, int minZ, int maxZ) {
        Set<Long> positions = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(position(x, z));
            }
        }
        return positions;
    }

    private static long position(int x, int z) {
        return PregenChunkRetention.regionKey(x, z);
    }

    private static final class RecordingTickets implements PregenChunkRetention.Tickets {
        private final List<Long> retained = new ArrayList<>();
        private final List<Long> released = new ArrayList<>();

        @Override
        public void retain(int chunkX, int chunkZ) {
            retained.add(position(chunkX, chunkZ));
        }

        @Override
        public void release(int chunkX, int chunkZ) {
            released.add(position(chunkX, chunkZ));
        }
    }
}
