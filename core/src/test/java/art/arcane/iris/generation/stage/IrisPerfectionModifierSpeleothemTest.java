package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.decoration.IrisSpeleothems;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisPerfectionModifierSpeleothemTest {
    @Test
    public void poolReplacementRemovesPreviouslySupportedSpikesInBothDirections() {
        for (String material : new String[]{"minecraft:sulfur_spike", "minecraft:pointed_dripstone"}) {
            for (boolean upward : new boolean[]{true, false}) {
                for (String fluid : new String[]{"minecraft:water", "minecraft:lava"}) {
                    Fixture fixture = new Fixture();
                    fixture.column(material, upward, false);
                    fixture.normalize();
                    for (int y = 2; y <= 4; y++) {
                        assertTrue(IrisSpeleothems.isSpike(fixture.output.get(0, y, 0)));
                    }

                    int supportY = upward ? 1 : 5;
                    PlatformBlockState pool = fluid.equals("minecraft:water") ? fixture.water : fluid(fluid);
                    fixture.output.set(0, supportY, 0, pool);
                    fixture.normalize();

                    assertSame(pool, fixture.output.get(0, supportY, 0));
                    for (int y = 2; y <= 4; y++) {
                        assertSame(fixture.air, fixture.output.get(0, y, 0));
                    }
                }
            }
        }
    }

    @Test
    public void removedWaterloggedSpikesRestoreWater() {
        for (boolean upward : new boolean[]{true, false}) {
            Fixture fixture = new Fixture();
            fixture.column("minecraft:sulfur_spike", upward, true);
            fixture.output.set(0, upward ? 1 : 5, 0, fixture.water);
            fixture.normalize();

            for (int y = 2; y <= 4; y++) {
                assertSame(fixture.water, fixture.output.get(0, y, 0));
            }
        }
    }

    @Test
    public void removingOneMergedColumnRepairsTheSupportedOppositeTip() {
        for (boolean removeUpward : new boolean[]{true, false}) {
            Fixture fixture = new Fixture();
            fixture.output.set(0, 0, 0, sturdyState());
            fixture.output.set(0, 7, 0, sturdyState());
            fixture.output.set(0, 1, 0, spike("minecraft:sulfur_spike", true, "base", false));
            fixture.output.set(0, 2, 0, spike("minecraft:sulfur_spike", true, "frustum", false));
            fixture.output.set(0, 3, 0, spike("minecraft:sulfur_spike", true, "tip_merge", false));
            fixture.output.set(0, 4, 0, spike("minecraft:sulfur_spike", false, "tip_merge", false));
            fixture.output.set(0, 5, 0, spike("minecraft:sulfur_spike", false, "frustum", false));
            fixture.output.set(0, 6, 0, spike("minecraft:sulfur_spike", false, "base", false));
            fixture.output.set(0, removeUpward ? 0 : 7, 0, fixture.water);
            fixture.normalize();

            for (int y = removeUpward ? 1 : 4; y <= (removeUpward ? 3 : 6); y++) {
                assertSame(fixture.air, fixture.output.get(0, y, 0));
            }
            assertEquals("tip", fixture.property(removeUpward ? 4 : 3, "thickness"));
            assertEquals("frustum", fixture.property(removeUpward ? 5 : 2, "thickness"));
            assertEquals("base", fixture.property(removeUpward ? 6 : 1, "thickness"));
            assertEquals(removeUpward ? "down" : "up",
                    fixture.property(removeUpward ? 4 : 3, "vertical_direction"));
        }
    }

    @Test
    public void objectRemovingTheFreeEndRetapersTheSupportedRemainder() {
        for (boolean upward : new boolean[]{true, false}) {
            Fixture fixture = new Fixture();
            fixture.column("minecraft:sulfur_spike", upward, false);
            fixture.output.set(0, upward ? 4 : 2, 0, fixture.air);
            fixture.normalize();

            assertEquals("tip", fixture.property(3, "thickness"));
            assertEquals("frustum", fixture.property(upward ? 2 : 4, "thickness"));
            assertSame(fixture.air, fixture.output.get(0, upward ? 4 : 2, 0));
        }
    }

    private static PlatformBlockState spike(String material, boolean upward, String thickness, boolean waterlogged) {
        return spike(material, Map.of(
                "vertical_direction", upward ? "up" : "down",
                "thickness", thickness,
                "waterlogged", Boolean.toString(waterlogged)));
    }

    private static PlatformBlockState spike(String material, Map<String, String> properties) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(material + "[thickness=" + properties.get("thickness")
                + ",vertical_direction=" + properties.get("vertical_direction")
                + ",waterlogged=" + properties.get("waterlogged") + "]");
        when(state.isWaterLogged()).thenReturn(Boolean.parseBoolean(properties.get("waterlogged")));
        when(state.withProperty(anyString(), anyString())).thenAnswer(invocation -> {
            Map<String, String> updated = new LinkedHashMap<>(properties);
            updated.put(invocation.getArgument(0), invocation.getArgument(1));
            return spike(material, updated);
        });
        return state;
    }

    private static PlatformBlockState sturdyState() {
        PlatformBlockState state = mock(PlatformBlockState.class);
        BlockData nativeState = mock(BlockData.class);
        when(state.key()).thenReturn("minecraft:sulfur");
        when(state.nativeHandle()).thenReturn(nativeState);
        when(nativeState.isFaceSturdy(any(), eq(BlockSupport.FULL))).thenReturn(true);
        return state;
    }

    private static PlatformBlockState fluid(String material) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(material);
        when(state.isFluid()).thenReturn(true);
        when(state.isWater()).thenReturn(material.equals("minecraft:water"));
        return state;
    }

    private static class Fixture {
        private final PlatformBlockState air = mock(PlatformBlockState.class);
        private final PlatformBlockState water = fluid("minecraft:water");
        private final Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 9, 1);

        private Fixture() {
            when(air.key()).thenReturn("minecraft:air");
            when(air.isAir()).thenReturn(true);
            for (int y = 0; y < output.getHeight(); y++) {
                output.set(0, y, 0, air);
            }
        }

        private void column(String material, boolean upward, boolean waterlogged) {
            output.set(0, upward ? 1 : 5, 0, sturdyState());
            output.set(0, upward ? 2 : 4, 0, spike(material, upward, "base", waterlogged));
            output.set(0, 3, 0, spike(material, upward, "frustum", waterlogged));
            output.set(0, upward ? 4 : 2, 0, spike(material, upward, "tip", waterlogged));
        }

        private void normalize() {
            for (int y = output.getHeight() - 1; y >= 0; y--) {
                PlatformBlockState state = output.get(0, y, 0);
                if (IrisSpeleothems.isSpike(state)) {
                    IrisPerfectionModifier.normalizeSpike(state, output, 0, 0, y, air, water);
                }
            }
        }

        private String property(int y, String property) {
            return IrisProceduralBlocks.propertyValue(output.get(0, y, 0), property);
        }
    }
}
