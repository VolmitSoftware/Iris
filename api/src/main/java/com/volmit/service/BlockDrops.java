package com.volmit.service;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.item.ItemDrop;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;

/**
 * Items a module drops when a block breaks, in addition to or instead of the platform's. The host asks every provider
 * when a player breaks a block in a world generated with the pack, drops the items of all answers where the block was
 * and keeps the platform's own drops unless an answer replaces them.
 */
@Service(Providers.ALL)
public interface BlockDrops {
    /** What this module drops for the block {@code broken}; {@link Result#NONE} when it does nothing for that block. */
    Result drops(PackWorld world, Broken broken);

    /**
     * The block that was broken.
     *
     * @param position world X and Z, Y the height above the dimension's bottom
     * @param block    the block as it was
     */
    record Broken(BlockPosition position, BlockState block) {
        public Broken {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }

    /**
     * @param drops                the items to drop
     * @param replacesVanillaDrops whether the platform's own drops of the block are not dropped
     */
    record Result(List<ItemDrop> drops, boolean replacesVanillaDrops) {
        public static final Result NONE = new Result(List.of(), false);

        public Result {
            drops = List.copyOf(drops);
        }
    }
}
