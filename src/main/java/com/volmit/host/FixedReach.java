package com.volmit.host;

import com.volmit.config.PackSettings;
import com.volmit.stage.PackReach;

/** A reach declared as a number at install, the same for every pack. */
record FixedReach(int blocks) implements PackReach {
    static final FixedReach NONE = new FixedReach(0);

    FixedReach {
        if (blocks < 0) throw new IllegalArgumentException("Negative reach: " + blocks);
    }

    @Override
    public int blocks(PackSettings settings) {
        return blocks;
    }
}
