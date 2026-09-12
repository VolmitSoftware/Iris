package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PregenPhaseTrackerTest {
    @Test
    public void theFirstTickAnnouncesTheJobAndStillReportsProgress() {
        PregenPhaseTracker tracker = new PregenPhaseTracker();

        assertEquals(List.of(PregenApiPhase.STARTED, PregenApiPhase.TICK), tracker.onTick(false));
        assertEquals(List.of(PregenApiPhase.TICK), tracker.onTick(false));
    }

    @Test
    public void pauseAndResumeAreEmittedOnTransitionOnly() {
        PregenPhaseTracker tracker = new PregenPhaseTracker();
        tracker.onTick(false);

        assertEquals(List.of(PregenApiPhase.PAUSED, PregenApiPhase.TICK), tracker.onTick(true));
        assertEquals(List.of(PregenApiPhase.TICK), tracker.onTick(true));
        assertEquals(List.of(PregenApiPhase.RESUMED, PregenApiPhase.TICK), tracker.onTick(false));
        assertEquals(List.of(PregenApiPhase.TICK), tracker.onTick(false));
    }

    @Test
    public void aJobThatStartsPausedDoesNotAlsoEmitPaused() {
        PregenPhaseTracker tracker = new PregenPhaseTracker();

        assertEquals(List.of(PregenApiPhase.STARTED, PregenApiPhase.TICK), tracker.onTick(true));
        assertEquals(List.of(PregenApiPhase.TICK), tracker.onTick(true));
        assertEquals(List.of(PregenApiPhase.RESUMED, PregenApiPhase.TICK), tracker.onTick(false));
    }

    @Test
    public void savingIsAStateNotAPulse() {
        PregenPhaseTracker tracker = new PregenPhaseTracker();
        tracker.onTick(false);

        assertEquals(List.of(PregenApiPhase.SAVING), tracker.onSaving());
        assertEquals(List.of(), tracker.onSaving());
        assertEquals(List.of(PregenApiPhase.TICK), tracker.onTick(false));
        assertEquals(List.of(PregenApiPhase.SAVING), tracker.onSaving());
    }

    @Test
    public void closeIsTerminalAndDistinguishesCompletionFromCancellation() {
        PregenPhaseTracker completed = new PregenPhaseTracker();
        completed.onTick(false);
        assertEquals(List.of(PregenApiPhase.COMPLETED), completed.onClose(true));
        assertEquals(List.of(), completed.onClose(true));
        assertEquals(List.of(), completed.onClose(false));
        assertEquals(List.of(), completed.onTick(false));
        assertEquals(List.of(), completed.onSaving());

        PregenPhaseTracker cancelled = new PregenPhaseTracker();
        cancelled.onTick(false);
        assertEquals(List.of(PregenApiPhase.CANCELLED), cancelled.onClose(false));
        assertEquals(List.of(), cancelled.onClose(true));
    }

    @Test
    public void aJobThatNeverTickedStillReportsItsOutcomeExactlyOnce() {
        PregenPhaseTracker tracker = new PregenPhaseTracker();

        assertEquals(List.of(PregenApiPhase.CANCELLED), tracker.onClose(false));
        assertEquals(List.of(), tracker.onClose(false));
    }
}
