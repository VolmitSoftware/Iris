package art.arcane.iris.generation.decoration;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.hunk.Hunk;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.ConcurrentHashMap;

public final class IrisSpeleothems {
    private static final String[] SPIKE_THICKNESSES = {"tip", "frustum", "base", "middle", "tip_merge"};
    private static final ConcurrentHashMap<PlatformBlockState, PlatformBlockState[][]> SPIKE_STATES = new ConcurrentHashMap<>();

    private IrisSpeleothems() {
    }

    public static boolean isSpike(PlatformBlockState state) {
        if (state == null || state.key() == null) {
            return false;
        }
        String material = IrisProceduralBlocks.materialKey(state);
        return material.equals("minecraft:pointed_dripstone") || material.equals("minecraft:sulfur_spike");
    }

    public static boolean isSupported(PlatformBlockState state, Hunk<PlatformBlockState> data, int x, int z, int y) {
        return isSupported(state, data, x, z, y,
                "up".equals(IrisProceduralBlocks.propertyValue(state, "vertical_direction")));
    }

    public static void finishAtTip(Hunk<PlatformBlockState> data, int x, int z, int y) {
        PlatformBlockState state = data.get(x, y, z);
        if (!isSpike(state)) {
            return;
        }
        boolean upward = "up".equals(IrisProceduralBlocks.propertyValue(state, "vertical_direction"));
        int frontY = y + (upward ? 1 : -1);
        PlatformBlockState front = frontY >= 0 && frontY < data.getHeight() ? data.get(x, frontY, z) : null;
        if (!sameSpike(state, front, upward)) {
            finishColumn(data, x, z, y, 1, upward);
        }
    }

    public static void finishColumn(Hunk<PlatformBlockState> data, int x, int z, int start, int placed, boolean upward) {
        int step = upward ? 1 : -1;
        for (int i = placed - 1; i >= 0; i--) {
            int y = start + step * i;
            PlatformBlockState state = data.get(x, y, z);
            if (isSpike(state)) {
                finishSpike(data, x, z, y, state, upward);
            }
        }
        if (placed == 0) {
            return;
        }
        PlatformBlockState state = data.get(x, start, z);
        if (!isSpike(state)) {
            return;
        }
        for (int y = start - step; y >= 0 && y < data.getHeight(); y -= step) {
            PlatformBlockState behind = data.get(x, y, z);
            if (!sameSpike(state, behind, upward)) {
                break;
            }
            finishSpike(data, x, z, y, behind, upward);
        }
    }

    static boolean canPlace(PlatformBlockState spike, Hunk<PlatformBlockState> data,
                            int x, int z, int y, boolean upward, boolean allowWater) {
        int supportY = y + (upward ? -1 : 1);
        if (y < 0 || y >= data.getHeight() || supportY < 0 || supportY >= data.getHeight()) {
            return false;
        }
        PlatformBlockState existing = data.get(x, y, z);
        if (!B.isAir(existing) && !(allowWater && existing != null && existing.isWater())) {
            return false;
        }
        return isSupported(spike, data, x, z, y, upward);
    }

    static PlatformBlockState orient(PlatformBlockState state, PlatformBlockState existing, boolean upward) {
        if (existing != null && existing.isWater() && !state.isWaterLogged()) {
            state = state.withProperty("waterlogged", "true");
        }
        return spikeBlock(state, upward, 0);
    }

    static boolean isSturdy(PlatformBlockState surface, boolean upward) {
        if (surface == null || B.isAir(surface) || B.isFluid(surface)) {
            return false;
        }
        DecoratorPlatformHooks.SurfaceSturdiness sturdiness = DecoratorPlatformHooks.surfaceSturdiness();
        if (sturdiness != null) {
            return sturdiness.canGoOn(surface, upward);
        }
        return ((BlockData) surface.nativeHandle()).isFaceSturdy(upward ? BlockFace.UP : BlockFace.DOWN, BlockSupport.FULL);
    }

    private static boolean isSupported(PlatformBlockState state, Hunk<PlatformBlockState> data,
                                        int x, int z, int y, boolean upward) {
        int supportY = y + (upward ? -1 : 1);
        if (supportY < 0 || supportY >= data.getHeight()) {
            return false;
        }
        PlatformBlockState support = data.get(x, supportY, z);
        return sameSpike(state, support, upward) || isSturdy(support, upward);
    }

    private static PlatformBlockState[][] buildSpikeStates(PlatformBlockState state) {
        PlatformBlockState[][] states = new PlatformBlockState[2][SPIKE_THICKNESSES.length];
        for (int direction = 0; direction < states.length; direction++) {
            PlatformBlockState directed = state.withProperty("vertical_direction", direction == 0 ? "up" : "down");
            for (int thickness = 0; thickness < SPIKE_THICKNESSES.length; thickness++) {
                states[direction][thickness] = directed.withProperty("thickness", SPIKE_THICKNESSES[thickness]);
            }
        }
        return states;
    }

    private static boolean sameSpike(PlatformBlockState state, PlatformBlockState other, boolean upward) {
        return isSpike(other)
                && IrisProceduralBlocks.materialKey(state).equals(IrisProceduralBlocks.materialKey(other))
                && (upward ? "up" : "down").equals(IrisProceduralBlocks.propertyValue(other, "vertical_direction"));
    }

    private static PlatformBlockState spikeBlock(PlatformBlockState state, boolean upward, int thickness) {
        return SPIKE_STATES.computeIfAbsent(state, IrisSpeleothems::buildSpikeStates)[upward ? 0 : 1][thickness];
    }

    private static void finishSpike(Hunk<PlatformBlockState> data, int x, int z, int y, PlatformBlockState state, boolean upward) {
        int step = upward ? 1 : -1;
        int frontY = y + step;
        int backY = y - step;
        PlatformBlockState front = frontY >= 0 && frontY < data.getHeight() ? data.get(x, frontY, z) : null;
        PlatformBlockState back = backY >= 0 && backY < data.getHeight() ? data.get(x, backY, z) : null;
        int thickness = 0;
        if (sameSpike(state, front, !upward)) {
            String frontThickness = IrisProceduralBlocks.propertyValue(front, "thickness");
            if ("tip".equals(frontThickness) || "tip_merge".equals(frontThickness)) {
                thickness = 4;
                data.set(x, frontY, z, spikeBlock(front, !upward, 4));
            }
        } else if (sameSpike(state, front, upward)) {
            String frontThickness = IrisProceduralBlocks.propertyValue(front, "thickness");
            thickness = "tip".equals(frontThickness) || "tip_merge".equals(frontThickness)
                    ? 1 : sameSpike(state, back, upward) ? 3 : 2;
        }
        data.set(x, y, z, spikeBlock(state, upward, thickness));
    }
}
