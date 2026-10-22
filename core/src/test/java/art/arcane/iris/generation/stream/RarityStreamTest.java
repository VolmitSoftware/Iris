package art.arcane.iris.generation.stream;

import art.arcane.volmlib.util.math.Rarity;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RarityStreamTest {
    @Test
    public void picksMatchRarityStreamSelectionForEveryNoiseValue() {
        SplittableRandom random = new SplittableRandom(4242L);
        for (int trial = 0; trial < 200; trial++) {
            List<Option> options = new ArrayList<>();
            int count = 1 + random.nextInt(12);
            for (int index = 0; index < count; index++) {
                options.add(new Option(random.nextInt(-2, 40)));
            }
            double[] value = new double[1];
            ProceduralStream<Double> noise = ProceduralStream.ofDouble((x, z) -> value[0]);
            ProceduralStream<Option> expected = noise.selectRarity(options);
            RarityStream<Option> candidate = new RarityStream<>(noise, options);
            for (int sample = 0; sample < 2_000; sample++) {
                value[0] = switch (random.nextInt(5)) {
                    case 0 -> random.nextDouble();
                    case 1 -> random.nextDouble(-0.5D, 1.5D);
                    case 2 -> random.nextInt(0, 64) / 63D;
                    case 3 -> sample % 2 == 0 ? Double.NaN : -0D;
                    default -> Math.nextUp(random.nextInt(0, 16) / 15D);
                };
                assertSame(expected.get(sample, -sample), candidate.get(sample, -sample));
            }
        }
    }

    @Test
    public void emptyOptionsSelectNothing() {
        assertNull(new RarityStream<Option>(ProceduralStream.ofDouble((x, z) -> 0.5D), List.of()).get(0D, 0D));
    }

    private record Option(int rarity) implements Rarity {
        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
