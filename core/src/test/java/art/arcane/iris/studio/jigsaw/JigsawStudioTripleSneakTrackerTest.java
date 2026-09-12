package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JigsawStudioTripleSneakTrackerTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_WORLD = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID REQUEST = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_REQUEST = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Test
    public void triggersOnThirdSneakAndResetsThePlayerSequence() {
        JigsawStudioTripleSneakTracker tracker = new JigsawStudioTripleSneakTracker(100L);

        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 10L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.SECOND,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 20L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.TRIGGERED,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 30L));
        assertEquals(0, tracker.trackedPlayers());
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 40L));
    }

    @Test
    public void acceptsTheExactWindowBoundaryAndResetsAfterIt() {
        JigsawStudioTripleSneakTracker tracker = new JigsawStudioTripleSneakTracker(100L);

        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 1_000L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.SECOND,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 1_050L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.TRIGGERED,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 1_100L));

        tracker.recordSneak(PLAYER, WORLD, REQUEST, 2_000L);
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 2_101L));
    }

    @Test
    public void changingWorldOrRequestStartsANewSequence() {
        JigsawStudioTripleSneakTracker tracker = new JigsawStudioTripleSneakTracker(100L);

        tracker.recordSneak(PLAYER, WORLD, REQUEST, 10L);
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, OTHER_WORLD, REQUEST, 20L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, OTHER_WORLD, OTHER_REQUEST, 30L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.SECOND,
                tracker.recordSneak(PLAYER, OTHER_WORLD, OTHER_REQUEST, 40L));
    }

    @Test
    public void movingBackwardInTimeStartsANewSequence() {
        JigsawStudioTripleSneakTracker tracker = new JigsawStudioTripleSneakTracker(100L);

        tracker.recordSneak(PLAYER, WORLD, REQUEST, 50L);
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.FIRST,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 49L));
        assertEquals(
                JigsawStudioTripleSneakTracker.Progress.SECOND,
                tracker.recordSneak(PLAYER, WORLD, REQUEST, 50L));
    }

    @Test
    public void clearOperationsRemoveOnlyTheirIntendedSequences() {
        JigsawStudioTripleSneakTracker tracker = new JigsawStudioTripleSneakTracker(100L);
        tracker.recordSneak(PLAYER, WORLD, REQUEST, 10L);
        tracker.recordSneak(OTHER_PLAYER, WORLD, OTHER_REQUEST, 10L);

        tracker.clearPlayer(PLAYER);
        assertEquals(1, tracker.trackedPlayers());
        assertEquals(1, tracker.clearRequest(OTHER_REQUEST));
        assertEquals(0, tracker.trackedPlayers());

        tracker.recordSneak(PLAYER, WORLD, REQUEST, 20L);
        tracker.clearAll();
        assertEquals(0, tracker.trackedPlayers());
    }

    @Test
    public void rejectsNonPositiveGestureWindows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JigsawStudioTripleSneakTracker(0L));
    }
}
