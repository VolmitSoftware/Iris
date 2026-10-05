package com.volmit.support;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.world.BlockView;
import java.util.function.IntBinaryOperator;

/** Prepared pre-placement blocks: stone below a per-column ground height, air above. */
public final class TestTerrain implements BlockView {
    public static final BlockState STONE = new BlockState("minecraft:stone");
    public static final BlockState AIR = new BlockState("minecraft:air");
    public static final BlockBox BOUNDS = new BlockBox(-16, 0, -16, 16, 32, 16);

    private final IntBinaryOperator groundHeight;

    public TestTerrain(IntBinaryOperator groundHeight) {
        this.groundHeight = groundHeight;
    }

    @Override
    public BlockBox bounds() {
        return BOUNDS;
    }

    @Override
    public BlockState get(int x, int y, int z) {
        if (!BOUNDS.contains(x, y, z)) throw new IndexOutOfBoundsException("Outside test terrain: " + x + "," + y + "," + z);
        return y < groundHeight.applyAsInt(x, z) ? STONE : AIR;
    }
}
