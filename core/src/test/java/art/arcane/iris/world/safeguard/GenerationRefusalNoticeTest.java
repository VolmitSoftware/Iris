package art.arcane.iris.world.safeguard;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerationRefusalNoticeTest {
    @Test
    public void causesListEveryDistinctMessageOutermostFirst() {
        IllegalStateException failure = new IllegalStateException("Iris generation history is unusable at /srv/moon.",
                new IOException("Historical generated registry definition changed",
                        new IllegalArgumentException()));

        assertEquals(List.of(
                "Iris generation history is unusable at /srv/moon.",
                "Historical generated registry definition changed",
                "IllegalArgumentException"
        ), GenerationRefusalNotice.causes(failure));
    }

    @Test
    public void wrappersThatOnlyRepeatTheirCauseAreSkipped() {
        IllegalStateException root = new IllegalStateException("history drifted");
        CompletionException wrapper = new CompletionException(root);

        assertEquals(List.of("history drifted"), GenerationRefusalNotice.causes(wrapper));
        assertEquals(List.of("history drifted"), GenerationRefusalNotice.causes(
                new IllegalStateException("history drifted", root)));
    }

    @Test
    public void aCyclicCauseChainTerminates() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);

        assertEquals(List.of("first", "second"), GenerationRefusalNotice.causes(first));
    }

    @Test
    public void summaryJoinsTheCausesOnOneLine() {
        assertEquals("outer; inner", GenerationRefusalNotice.summary(
                new IllegalStateException("outer", new IOException("inner"))));
    }

    @Test
    public void noticeIsFramedAndNamesCauseChainAndConsequences() {
        List<String> lines = GenerationRefusalNotice.compose(
                "Iris refused to generate world 'moon' (iris:moon)",
                List.of("outer", "inner"),
                List.of("No chunks are written to it.", "Server startup stops before this world loads."));

        assertEquals(lines.getFirst(), lines.getLast());
        assertTrue(lines.getFirst(), lines.getFirst().chars().allMatch(character -> character == '='));
        assertEquals(List.of(
                "Iris refused to generate world 'moon' (iris:moon)",
                "Cause: outer",
                "Caused by: inner",
                "No chunks are written to it.",
                "Server startup stops before this world loads."
        ), lines.subList(1, lines.size() - 1));
    }
}
