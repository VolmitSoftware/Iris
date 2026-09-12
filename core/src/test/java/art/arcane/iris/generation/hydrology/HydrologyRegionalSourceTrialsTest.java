package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalSourceTrialsTest {
    @Test
    public void longClusterCannotConsumeEveryOutletTrial() {
        ArrayList<Source> sources = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            sources.add(new Source(0, 10000D - index, index));
        }
        sources.add(new Source(1, 6000D, 4096));
        sources.add(new Source(2, 3000D, 8192));
        Fixture fixture = fixture(sources);
        HydrologyRegionalSourceTrials trials = fixture.trials();

        assertEquals(0, trials.next(Set.of()).orElseThrow());
        assertEquals(40, trials.next(Set.of()).orElseThrow());
        assertEquals(41, trials.next(Set.of()).orElseThrow());
    }

    @Test
    public void acceptedOutletsDoNotConsumeTheRemainingTrialBudget() {
        Fixture fixture = fixture(List.of(new Source(0, 10000D, 0), new Source(0, 9000D, 128),
                new Source(1, 6000D, 4096), new Source(1, 3000D, 8192)));
        HydrologyRegionalSourceTrials trials = fixture.trials();
        assertEquals(0, trials.next(Set.of()).orElseThrow());

        assertEquals(2, trials.next(Set.of(100L)).orElseThrow());
        assertEquals(3, trials.next(Set.of(100L)).orElseThrow());
        assertTrue(trials.next(Set.of(100L)).isEmpty());
    }

    @Test
    public void lengthBandsReachDownstreamAlternativesBeforeNearbyLongestSources() {
        Fixture fixture = fixture(List.of(new Source(0, 10000D, 0), new Source(0, 9990D, 1),
                new Source(0, 8000D, 128), new Source(0, 6000D, 256), new Source(0, 4000D, 384)));
        HydrologyRegionalSourceTrials trials = fixture.trials();

        assertEquals(0, trials.next(Set.of()).orElseThrow());
        assertEquals(2, trials.next(Set.of()).orElseThrow());
        assertEquals(3, trials.next(Set.of()).orElseThrow());
        assertEquals(4, trials.next(Set.of()).orElseThrow());
        assertEquals(1, trials.next(Set.of()).orElseThrow());
    }

    @Test
    public void laterBandTrialsPreferDifferentBranchesAndIgnoreInputOrder() {
        Fixture fixture = fixture(List.of(new Source(0, 10000D, 0), new Source(0, 9999D, 1),
                new Source(0, 9998D, 4096), new Source(0, 2000D, -4096)));
        ArrayList<Integer> reversed = new ArrayList<>(fixture.sources());
        Collections.reverse(reversed);
        HydrologyRegionalSourceTrials first = fixture.trials();
        HydrologyRegionalSourceTrials second = new HydrologyRegionalSourceTrials(fixture.grid(), fixture.tree(), reversed);
        List<Integer> expected = List.of(0, 3, 2, 1);

        for (int source : expected) {
            assertEquals(source, first.next(Set.of()).orElseThrow());
            assertEquals(source, second.next(Set.of()).orElseThrow());
        }
        assertFalse(first.next(Set.of()).isPresent());
        assertFalse(second.next(Set.of()).isPresent());
    }

    private static Fixture fixture(List<Source> sources) {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(sources.size());
        ArrayList<Integer> indices = new ArrayList<>(sources.size());
        HydrologyRegionalGraph.Label[] selected = new HydrologyRegionalGraph.Label[sources.size()];
        int maximumRoot = 0;
        for (int index = 0; index < sources.size(); index++) {
            Source source = sources.get(index);
            selected[index] = new HydrologyRegionalGraph.Label(new HydrologyRegionalGraph.LabelState(
                    index, index, 63, 0D, source.length(), source.root()), null);
            maximumRoot = Math.max(maximumRoot, source.root());
            nodes.add(new HydrologyGridNode(index, index, 0, source.x(), 0, index,
                    HydrologyTerrainSample.openLand(70, 0D, "land")));
            indices.add(index);
        }
        ArrayList<OutletCandidate> outlets = new ArrayList<>();
        for (int root = 0; root <= maximumRoot; root++) {
            HydrologyPoint point = new HydrologyPoint(root * 256, 63, 0);
            outlets.add(new OutletCandidate(0, 0,
                    new RiverOutlet(100L + root, HydrologyFeatureType.MOUTH, root, point, point, 63, true)));
        }
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 16384, sources.size(), 1, nodes);
        HydrologyRegionalGraph.Tree tree = new HydrologyRegionalGraph.Tree(selected, new int[sources.size()], outlets);
        return new Fixture(grid, tree, indices);
    }

    private record Source(int root, double length, int x) {
    }

    private record Fixture(HydrologySampledGrid grid, HydrologyRegionalGraph.Tree tree, List<Integer> sources) {
        private HydrologyRegionalSourceTrials trials() {
            return new HydrologyRegionalSourceTrials(grid, tree, sources);
        }
    }
}
