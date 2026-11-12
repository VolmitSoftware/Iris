package art.arcane.iris.core.studio.bridge;

import com.google.gson.Gson;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class StudioEventsTest {
    private static final String WORLD_EVENT = "event: world\n";

    private final StudioEvents events = new StudioEvents(new StudioWorld("studio", () -> null, null), new Gson());

    @Test
    public void aStudioThatConnectsGetsTheWorldStateFirst() {
        ByteArrayOutputStream studio = new ByteArrayOutputStream();

        events.listen(studio);
        events.poll();

        assertEquals(1, worldEvents(studio));
    }

    // A studio skips its first world event as the state it connected to, so without one of its own a later window
    // would take the first hotload for that state and not redraw.
    @Test
    public void aStudioThatConnectsLaterGetsItsOwnWorldStateAndTheOthersAreNotRedrawn() {
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream later = new ByteArrayOutputStream();
        events.listen(first);
        events.poll();

        events.listen(later);
        events.poll();

        assertEquals(1, worldEvents(first));
        assertEquals(1, worldEvents(later));
    }

    @Test
    public void everyStudioHearsOfAHotloadAfterItsWorldState() {
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream later = new ByteArrayOutputStream();
        events.listen(first);
        events.poll();
        events.listen(later);
        events.poll();

        events.hotloaded();
        events.poll();

        assertEquals(2, worldEvents(first));
        assertEquals(2, worldEvents(later));
    }

    private static int worldEvents(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8).split(WORLD_EVENT, -1).length - 1;
    }
}
