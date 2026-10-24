package com.volmit.example.placement;

import com.volmit.object.ObjectBlock;
import java.util.ArrayList;
import java.util.List;

/** Per-placement accumulator: the transformed blocks of the object's lowest layer. */
public final class StiltBounds {
    private int lowestY = Integer.MAX_VALUE;
    private final List<ObjectBlock> lowestLayer = new ArrayList<>();

    public void include(ObjectBlock block) {
        int y = block.position().y();
        if (y < lowestY) {
            lowestY = y;
            lowestLayer.clear();
        }
        if (y == lowestY) {
            lowestLayer.add(block);
        }
    }

    public List<ObjectBlock> lowestLayer() {
        return List.copyOf(lowestLayer);
    }
}
