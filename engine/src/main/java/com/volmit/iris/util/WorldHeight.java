package com.volmit.iris.util;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorldHeight {
    private final int minHeight;
    private final int maxHeight;

    public WorldHeight(int maxHeight) {
        this(0, maxHeight);
    }

    public int getTotalHeight() {
        return maxHeight - minHeight;
    }
}
