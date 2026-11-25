package art.arcane.iris.generation.block;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlantPlacementTest {
    private final PlantPlacement.ServerRule previous = PlantPlacement.bindServerRule(null);

    @After
    public void restoreRule() {
        PlantPlacement.restoreServerRule(previous);
    }

    @Test
    public void withoutAServerRuleTheBlockTableAnswers() {
        NativeBlockState plant = block("minecraft:short_grass");
        NativeBlockState ground = block("iris:grass_block");
        when(plant.canPlaceOnto(ground)).thenReturn(true);

        assertTrue(B.canPlaceOnto(plant, ground));
    }

    @Test
    public void aBoundServerRuleOverridesTheBlockTable() {
        NativeBlockState plant = block("minecraft:short_grass");
        NativeBlockState ground = block("iris:grass_block");
        PlantPlacement.bindServerRule((p, g) -> p == plant && g == ground);

        assertTrue(B.canPlaceOnto(plant, ground));
        assertFalse(B.canPlaceOnto(plant, block("minecraft:stone")));
    }

    @Test
    public void nothingStandsOnAirAndDeadBushesStayOffGrass() {
        PlantPlacement.bindServerRule((p, g) -> true);
        NativeBlockState air = block("minecraft:air");
        when(air.isAir()).thenReturn(true);

        assertFalse(B.canPlaceOnto(block("minecraft:short_grass"), air));
        assertFalse(B.canPlaceOnto(block("minecraft:dead_bush"), block("minecraft:grass_block")));
        assertTrue(B.canPlaceOnto(block("minecraft:dead_bush"), block("minecraft:sand")));
    }

    private static NativeBlockState block(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.materialKey()).thenReturn(key);
        return state;
    }
}
