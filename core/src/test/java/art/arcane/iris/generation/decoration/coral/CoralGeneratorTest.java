package art.arcane.iris.generation.decoration.coral;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoralGeneratorTest {
    @Test
    public void fanTipsFollowTheTopOfEachOccupiedColumn() {
        PlatformBlockState structure = mock(PlatformBlockState.class);
        PlatformBlockState tip = mock(PlatformBlockState.class);
        for (int height : new int[]{2, 7, 14}) {
            for (int width : new int[]{1, 3, 11}) {
                IrisCoral coral = new IrisCoral().setForm(IrisCoralForm.FAN)
                        .setHeightMin(height).setHeightMax(height).setFanWidth(width)
                        .setWaterlogged(false).setBlockPalette(palette(structure));
                IrisObject bare = CoralGenerator.generate(coral, 0, new RNG(0L), null);
                Map<Integer, Integer> tops = new HashMap<>();
                Set<IrisBlockVector> barePositions = new HashSet<>();
                for (IrisBlockVector position : bare.getBlocks().keys()) {
                    barePositions.add(position);
                    tops.merge(position.getBlockX(), position.getBlockY(), Math::max);
                }
                coral.setTipPalette(palette(tip));
                for (double chance : new double[]{0.0, 0.35, 1.0}) {
                    coral.setTipChance(chance);
                    for (long seed = 0; seed < 16; seed++) {
                        IrisObject decorated = CoralGenerator.generate(coral, 0, new RNG(seed), null);
                        Set<IrisBlockVector> positions = new HashSet<>();
                        int tipCount = 0;
                        for (IrisBlockVector position : decorated.getBlocks().keys()) {
                            positions.add(position);
                            if (decorated.getBlocks().get(position) == tip) {
                                assertTrue("Fan tip must occupy an existing column", tops.containsKey(position.getBlockX()));
                                assertEquals(tops.get(position.getBlockX()).intValue(), position.getBlockY());
                                tipCount++;
                            }
                        }
                        assertEquals(barePositions, positions);
                        if (chance == 1.0) {
                            assertEquals(tops.size(), tipCount);
                        }
                    }
                }
            }
        }
    }

    private static IrisMaterialPalette palette(PlatformBlockState state) {
        IrisBlockData block = mock(IrisBlockData.class);
        when(block.getWeight()).thenReturn(1);
        when(block.getBlockData(null)).thenReturn(state);
        return new IrisMaterialPalette().setPalette(new KList<>(block));
    }
}
