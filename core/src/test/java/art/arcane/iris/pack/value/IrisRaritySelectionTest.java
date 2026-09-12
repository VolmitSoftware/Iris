package art.arcane.iris.pack.value;

import art.arcane.iris.generation.biome.IrisBiome;

import art.arcane.volmlib.util.math.Rarity;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisRaritySelectionTest {
    @Test
    public void everyIndexMatchesExpandedInsertionOrderForRandomizedChoices() {
        Random random = new Random(718938L);
        for (int scenario = 0; scenario < 500; scenario++) {
            ArrayList<Choice> choices = new ArrayList<>();
            int count = random.nextInt(12);
            for (int index = 0; index < count; index++) {
                choices.add(random.nextInt(7) == 0 ? null : new Choice(index, random.nextInt(35) - 3));
            }
            List<Choice> expanded = expand(choices);
            IrisRaritySelection<Choice> selection = IrisRaritySelection.create(choices);
            assertEquals(expanded.size(), selection.size());
            if (expanded.isEmpty()) {
                assertNull(selection.get(0));
                continue;
            }
            for (int index = 0; index < expanded.size(); index++) {
                assertSame(expanded.get(index), selection.get(index));
            }
            assertSame(expanded.getFirst(), selection.get(Long.MIN_VALUE));
            assertSame(expanded.getLast(), selection.get(Long.MAX_VALUE));
            for (double sample : new double[]{-3D, -0D, 0.125D, 0.5D, 0.875D, 1D, 3D,
                    Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                int index = (int) Math.round(sample * (expanded.size() - 1));
                assertSame(expanded.get(Math.max(0, Math.min(expanded.size() - 1, index))), selection.select(sample));
            }
        }
    }

    @Test(timeout = 1000)
    public void maximumRarityCountsStayPositiveWithoutExpandingBillionsOfEntries() {
        Choice rare = new Choice(0, Integer.MAX_VALUE);
        Choice common = new Choice(1, 1);
        Choice next = new Choice(2, 2);
        IrisRaritySelection<Choice> selection = IrisRaritySelection.create(List.of(rare, common, next));

        assertEquals(4_294_967_294L, selection.size());
        assertSame(next, selection.get(0L));
        assertSame(common, selection.get(2_147_483_646L));
        assertSame(rare, selection.get(2_147_483_647L));
        assertSame(common, selection.get(2_147_483_648L));
        assertSame(next, selection.get(selection.size() - 1L));
        assertSame(rare, selection.select(0.5D));
        assertSame(next, selection.select(0D));
        assertSame(next, selection.select(1D));
    }

    @Test
    public void equalMaximumRaritiesRetainEveryChoice() {
        Choice first = new Choice(0, Integer.MAX_VALUE);
        Choice second = new Choice(1, Integer.MAX_VALUE);
        Choice third = new Choice(2, Integer.MAX_VALUE);
        IrisRaritySelection<Choice> selection = IrisRaritySelection.create(List.of(first, second, third));

        assertEquals(3L, selection.size());
        assertSame(second, selection.get(0L));
        assertSame(first, selection.get(1L));
        assertSame(third, selection.get(2L));
    }

    @Test
    public void duplicateChoicesKeepTheirSeparateInsertionPositions() {
        Choice repeated = new Choice(0, 1);
        Choice other = new Choice(1, 3);
        List<Choice> choices = List.of(repeated, other, repeated);
        IrisRaritySelection<Choice> selection = IrisRaritySelection.create(choices);
        List<Choice> expanded = expand(choices);
        for (int index = 0; index < expanded.size(); index++) {
            assertSame(expanded.get(index), selection.get(index));
        }
    }

    @Test
    public void deserializedRuntimeRarityValuesUseTheExistingMinimumClamp() {
        Gson gson = new Gson();
        IrisBiome zero = gson.fromJson("{\"rarity\":0}", IrisBiome.class);
        IrisBiome negative = gson.fromJson("{\"rarity\":-2147483648}", IrisBiome.class);
        IrisBiome maximum = gson.fromJson("{\"rarity\":2147483647}", IrisBiome.class);
        IrisRaritySelection<IrisBiome> selection = IrisRaritySelection.create(List.of(zero, negative, maximum));

        assertEquals(4_294_967_295L, selection.size());
        assertSame(zero, selection.get(2_147_483_647L));
        assertSame(maximum, selection.get(selection.size() - 1L));
        assertEquals(0, zero.getRarity());
        assertEquals(Integer.MIN_VALUE, negative.getRarity());
    }

    @Test
    public void nullAndEmptyOptionsDoNotProduceCandidates() {
        assertNull(IrisRaritySelection.<Choice>create(List.of()).select(0.5D));
        assertNull(IrisRaritySelection.<Choice>create(Arrays.asList(null, null)).get(0L));
    }

    private static List<Choice> expand(List<Choice> choices) {
        int maximum = 1;
        for (Choice choice : choices) {
            if (choice != null) {
                maximum = Math.max(maximum, choice.rarity());
            }
        }
        ArrayList<Choice> expanded = new ArrayList<>();
        boolean append = false;
        for (Choice choice : choices) {
            if (choice == null) {
                continue;
            }
            int count = maximum + 1 - Math.max(1, choice.rarity());
            for (int index = 0; index < count; index++) {
                append = !append;
                if (append) {
                    expanded.add(choice);
                } else {
                    expanded.add(0, choice);
                }
            }
        }
        return expanded;
    }

    private record Choice(int id, int rarity) implements Rarity {
        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
